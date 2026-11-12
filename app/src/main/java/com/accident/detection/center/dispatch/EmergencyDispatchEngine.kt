package com.accident.detection.center.dispatch

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.accident.detection.center.CrashDetectionApp
import com.accident.detection.center.R
import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import com.accident.detection.center.data.preferences.UserPreferencesRepository
import com.accident.detection.center.domain.repository.CrashLogRepository
import com.accident.detection.center.domain.repository.EmergencyContactRepository
import com.accident.detection.center.dispatch.call.AutomatedCallManager
import com.accident.detection.center.dispatch.email.EmailDispatchService
import com.accident.detection.center.dispatch.location.CrashLocationResolver
import com.accident.detection.center.dispatch.location.ResolvedCrashTelemetry
import com.accident.detection.center.dispatch.sms.SmsDispatcher
import com.accident.detection.center.domain.model.CascadeStage
import com.accident.detection.center.domain.model.ChannelDispatchStatus
import com.accident.detection.center.domain.model.ContactDispatchProgress
import com.accident.detection.center.domain.model.DispatchStatusState
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.sensor.CrashEventPayload
import com.accident.detection.center.service.AccidentMonitoringService
import com.accident.detection.center.ui.screens.alarm.EmergencyAlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-grade Multi-Channel Emergency Dispatch Engine.
 *
 * Executes the 4 automated dispatch channels concurrently under a supervisorScope:
 * Channel A: High-Priority Local Notification ("SOS DISPATCHED")
 * Channel B: Silent Background Multipart SMS via SmsManager with Sent PendingIntents
 * Channel C: Automated HTML Crash Report Email via EmailDispatchService + Expedited WorkManager
 * Channel D: Automated Direct Speakerphone Call via AutomatedCallManager (Priority #1 contact)
 */
@Singleton
class EmergencyDispatchEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationResolver: CrashLocationResolver,
    private val smsDispatcher: SmsDispatcher,
    private val emailDispatchService: EmailDispatchService,
    private val automatedCallManager: AutomatedCallManager,
    private val contactRepository: EmergencyContactRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val crashLogRepository: CrashLogRepository
) {
    private val tag = "EmergencyDispatchEngine"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _dispatchState = MutableStateFlow(DispatchStatusState())
    val dispatchState: StateFlow<DispatchStatusState> = _dispatchState.asStateFlow()

    /**
     * Executes all 4 dispatch channels concurrently.
     */
    suspend fun executeEmergencyCascade(
        payload: CrashEventPayload,
        incidentLogId: Long = 0L
    ): DispatchStatusState = withContext(Dispatchers.IO) {
        val userProfile = preferencesRepository.userProfileFlow.first()
        val contacts = contactRepository.getAllContactsFlow().first()

        // 1. Resolve fresh GPS coordinates, street address, battery %
        val telemetry = locationResolver.resolveCrashTelemetry(payload)
        val mapsUrl = telemetry.mapsUrl

        val initialProgress = contacts.map { contact ->
            ContactDispatchProgress(
                contactId = contact.id,
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                email = contact.email,
                smsStatus = if (contact.isSmsEnabled) ChannelDispatchStatus.QUEUED else ChannelDispatchStatus.SKIPPED,
                emailStatus = if (contact.isEmailEnabled && contact.email.isNotBlank()) ChannelDispatchStatus.QUEUED else ChannelDispatchStatus.SKIPPED,
                callStatus = if (contact.isCallEnabled && contact.priority == 1) ChannelDispatchStatus.QUEUED else ChannelDispatchStatus.SKIPPED
            )
        }

        _dispatchState.value = DispatchStatusState(
            stage = CascadeStage.DISPATCHING_ALERTS,
            countdownSecondsRemaining = 0,
            notificationStatus = ChannelDispatchStatus.IN_PROGRESS,
            incidentLatitude = telemetry.latitude,
            incidentLongitude = telemetry.longitude,
            mapsUrl = mapsUrl,
            crashTimestampEpochMs = payload.timestampEpochMs,
            contactProgressList = initialProgress
        )

        // 2. Concurrently execute all channels under supervisorScope
        supervisorScope {
            // Channel A: High-Priority Local Alert Notification
            launch {
                dispatchChannelANotification(telemetry, userProfile)
            }

            // Channel B: Silent Background SMS Dispatch
            launch {
                dispatchChannelBSms(contacts, payload, telemetry, userProfile)
            }

            // Channel C: Automated HTML Email Report
            launch {
                dispatchChannelCEmail(contacts, payload, telemetry, userProfile)
            }

            // Channel D: Automated Direct Speakerphone Call
            launch {
                dispatchChannelDCall(contacts)
            }
        }

        // 3. Mark completed and update Room log
        val finalProgress = _dispatchState.value.contactProgressList
        val notifiedCount = finalProgress.count {
            it.smsStatus == ChannelDispatchStatus.SUCCESS ||
                    it.emailStatus == ChannelDispatchStatus.SUCCESS ||
                    it.callStatus == ChannelDispatchStatus.SUCCESS
        }

        _dispatchState.value = _dispatchState.value.copy(
            stage = CascadeStage.DISPATCH_COMPLETED,
            isMedicalBeaconVisible = true
        )

        if (incidentLogId != 0L) {
            val log = crashLogRepository.getLogById(incidentLogId)
            if (log != null) {
                crashLogRepository.updateLog(
                    log.copy(
                        sosDispatchTriggered = true,
                        contactsNotifiedCount = notifiedCount,
                        dispatchSummary = "Cascade completed: $notifiedCount contact channels alerted."
                    )
                )
            }
        }

        _dispatchState.value
    }

    /**
     * Channel A: Post persistent high-priority SOS alert notification.
     */
    private fun dispatchChannelANotification(
        telemetry: ResolvedCrashTelemetry,
        userProfile: UserProfile
    ) {
        try {
            val intent = Intent(context, EmergencyAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

            val notification = NotificationCompat.Builder(context, CrashDetectionApp.CHANNEL_EMERGENCY_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 SOS DISPATCHED")
                .setContentText("Emergency contacts have been alerted with your live GPS location.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "SOS DISPATCHED — Emergency contacts have been alerted with your GPS location.\n" +
                                "Address: ${telemetry.streetAddress}\n" +
                                "Medical ID: ${userProfile.fullName} (${userProfile.bloodGroup.label})"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID_SOS_ALERT, notification)
            _dispatchState.value = _dispatchState.value.copy(
                notificationStatus = ChannelDispatchStatus.SUCCESS
            )
            Log.d(tag, "Channel A Notification posted successfully")
        } catch (e: Exception) {
            Log.e(tag, "Channel A Notification failed: ${e.message}")
            _dispatchState.value = _dispatchState.value.copy(
                notificationStatus = ChannelDispatchStatus.FAILED
            )
        }
    }

    /**
     * Channel B: Silent Background Multipart SMS Dispatch.
     */
    private suspend fun dispatchChannelBSms(
        contacts: List<EmergencyContactEntity>,
        payload: CrashEventPayload,
        telemetry: ResolvedCrashTelemetry,
        userProfile: UserProfile
    ) {
        val userName = userProfile.fullName.ifBlank { "User" }
        val peakG = String.format("%.1f", payload.peakGForce)
        val speed = payload.speedAtImpactKmh.toInt()
        val bloodGroup = userProfile.bloodGroup.label
        val allergies = userProfile.allergies.ifBlank { "None" }

        val smsBody = "🚨 CRITICAL SOS: $userName was involved in a severe vehicle crash ($peakG G at $speed km/h). " +
                "Location: ${telemetry.streetAddress} (${telemetry.mapsUrl}) " +
                "Medical: Blood $bloodGroup, Allergies: $allergies. Battery: ${telemetry.batteryPercentage}%. " +
                "Immediate emergency response requested."

        val currentList = _dispatchState.value.contactProgressList.toMutableList()

        for (i in currentList.indices) {
            val contactProgress = currentList[i]
            val contact = contacts.firstOrNull { it.id == contactProgress.contactId } ?: continue

            if (!contact.isSmsEnabled || contact.phoneNumber.isBlank()) continue

            currentList[i] = contactProgress.copy(smsStatus = ChannelDispatchStatus.IN_PROGRESS)
            _dispatchState.value = _dispatchState.value.copy(contactProgressList = currentList.toList())

            val result = smsDispatcher.dispatchSmsToContact(contact, smsBody)

            currentList[i] = contactProgress.copy(
                smsStatus = result.status,
                failureReason = result.errorMessage
            )
            _dispatchState.value = _dispatchState.value.copy(contactProgressList = currentList.toList())
        }
    }

    /**
     * Channel C: Automated HTML Crash Report Email Dispatch.
     */
    private suspend fun dispatchChannelCEmail(
        contacts: List<EmergencyContactEntity>,
        payload: CrashEventPayload,
        telemetry: ResolvedCrashTelemetry,
        userProfile: UserProfile
    ) {
        val currentList = _dispatchState.value.contactProgressList.toMutableList()

        for (i in currentList.indices) {
            val contactProgress = currentList[i]
            val contact = contacts.firstOrNull { it.id == contactProgress.contactId } ?: continue

            if (!contact.isEmailEnabled || contact.email.isBlank()) continue

            currentList[i] = contactProgress.copy(emailStatus = ChannelDispatchStatus.IN_PROGRESS)
            _dispatchState.value = _dispatchState.value.copy(contactProgressList = currentList.toList())

            val result = emailDispatchService.dispatchCrashReportEmail(
                contact = contact,
                payload = payload,
                userProfile = userProfile,
                streetAddress = telemetry.streetAddress,
                batteryPercentage = telemetry.batteryPercentage
            )

            currentList[i] = contactProgress.copy(
                emailStatus = result.status,
                failureReason = result.errorMessage
            )
            _dispatchState.value = _dispatchState.value.copy(contactProgressList = currentList.toList())
        }
    }

    /**
     * Channel D: Automated Direct Speakerphone Call to Priority #1 Contact.
     */
    private suspend fun dispatchChannelDCall(contacts: List<EmergencyContactEntity>) {
        val primaryContact = contacts.filter { it.isCallEnabled && it.phoneNumber.isNotBlank() }
            .minByOrNull { it.priority } ?: return

        val currentList = _dispatchState.value.contactProgressList.toMutableList()
        val index = currentList.indexOfFirst { it.contactId == primaryContact.id }

        if (index >= 0) {
            currentList[index] = currentList[index].copy(callStatus = ChannelDispatchStatus.IN_PROGRESS)
            _dispatchState.value = _dispatchState.value.copy(contactProgressList = currentList.toList())

            val result = automatedCallManager.initiateEmergencySpeakerphoneCall(primaryContact)

            currentList[index] = currentList[index].copy(
                callStatus = result.status,
                failureReason = result.errorMessage
            )
            _dispatchState.value = _dispatchState.value.copy(contactProgressList = currentList.toList())
        }
    }

    companion object {
        const val NOTIFICATION_ID_SOS_ALERT = 9999
    }
}

