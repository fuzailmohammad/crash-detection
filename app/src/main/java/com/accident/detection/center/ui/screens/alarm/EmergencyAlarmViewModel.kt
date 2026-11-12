package com.accident.detection.center.ui.screens.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import com.accident.detection.center.data.preferences.UserPreferencesRepository
import com.accident.detection.center.domain.repository.CrashLogRepository
import com.accident.detection.center.domain.repository.EmergencyContactRepository
import com.accident.detection.center.dispatch.EmergencyDispatchEngine
import com.accident.detection.center.domain.model.CascadeStage
import com.accident.detection.center.domain.model.ChannelDispatchStatus
import com.accident.detection.center.domain.model.ContactDispatchProgress
import com.accident.detection.center.domain.model.DispatchStatusState
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.hardware.EmergencyHardwareController
import com.accident.detection.center.sensor.CrashEventPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

sealed class AlarmCommand {
    data object StopSirenAndTorch : AlarmCommand()
    data class SpeakTts(val text: String) : AlarmCommand()
    data class InitiatePhoneCall(val phoneNumber: String) : AlarmCommand()
}

@HiltViewModel
class EmergencyAlarmViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val contactRepository: EmergencyContactRepository,
    private val crashLogRepository: CrashLogRepository,
    private val emergencyDispatchEngine: EmergencyDispatchEngine,
    val hardwareController: EmergencyHardwareController
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = preferencesRepository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val contacts: StateFlow<List<EmergencyContactEntity>> = contactRepository.getAllContactsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(EmergencyCountdownUiState())
    val uiState: StateFlow<EmergencyCountdownUiState> = _uiState.asStateFlow()

    val dispatchState: StateFlow<DispatchStatusState> = emergencyDispatchEngine.dispatchState

    private val _commandEvents = MutableSharedFlow<AlarmCommand>()
    val commandEvents: SharedFlow<AlarmCommand> = _commandEvents.asSharedFlow()

    private var countdownJob: Job? = null
    private var lastAnnouncedSecond: Int = -1

    fun initializeAlarm(
        latitude: Double,
        longitude: Double,
        logId: Long = 0L,
        speedKmh: Float = 0.0f,
        peakG: Float = 0.0f,
        confidence: Float = 100.0f,
        vehicleMode: VehicleMode = VehicleMode.CAR,
        summary: String = "",
        isSimulation: Boolean = false
    ) {
        val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"
        val payload = CrashEventPayload(
            latitude = latitude,
            longitude = longitude,
            speedAtImpactKmh = if (speedKmh > 0f) speedKmh else 54.0f,
            peakGForce = if (peakG > 0f) peakG else 6.8f,
            gyroAngularVelocityDegS = 280.0f,
            ambientNoiseDb = 118.0f,
            pressureJumpHpa = 1.6f,
            crashConfidenceScore = confidence,
            vehicleMode = vehicleMode,
            isSimulation = isSimulation,
            forensicSummary = summary.ifBlank { "Multi-sensor collision vector confirmed." }
        )

        val initialContacts = contacts.value.map { contact ->
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

        _uiState.value = EmergencyCountdownUiState(
            phase = EmergencyCountdownPhase.COUNTING_DOWN,
            remainingMillis = 30_000L,
            remainingSeconds = 30,
            progressFraction = 1.0f,
            payload = payload,
            logId = logId,
            mapsUrl = mapsUrl,
            contactProgressList = initialContacts
        )

        // Activate Siren, 2 Hz Torch Strobe, and USAGE_ALARM Vibration
        hardwareController.startEmergencyHardware()

        // Start high-precision 100ms ticker
        startCountdownTicker()
    }

    /**
     * High-precision 100ms ticker counting down from 30,000ms to 0ms.
     * Triggers spoken voice alerts at 28s, 20s, 10s, and 5s with siren ducking.
     */
    private fun startCountdownTicker() {
        countdownJob?.cancel()
        lastAnnouncedSecond = -1

        countdownJob = viewModelScope.launch {
            var millis = 30_000L
            while (millis > 0L) {
                delay(100L)
                millis = (millis - 100L).coerceAtLeast(0L)

                val secondsRemaining = ceil(millis / 1000.0).toInt().coerceAtLeast(0)
                val fraction = (millis.toFloat() / 30_000f).coerceIn(0f, 1f)

                _uiState.value = _uiState.value.copy(
                    remainingMillis = millis,
                    remainingSeconds = secondsRemaining,
                    progressFraction = fraction
                )

                // Voice announcement checkpoints: 28s, 20s, 10s, 5s
                if ((secondsRemaining == 28 || secondsRemaining == 20 || secondsRemaining == 10 || secondsRemaining == 5) &&
                    secondsRemaining != lastAnnouncedSecond
                ) {
                    lastAnnouncedSecond = secondsRemaining
                    hardwareController.announceWarning(secondsRemaining)
                }
            }

            // Timer expired without user cancellation -> Automated Dispatch Cascade
            hardwareController.stopAllAlarms()
            executeAutomatedDispatchCascade()
        }
    }

    /**
     * User executed deliberate Slide-to-Cancel gesture past 85% track threshold.
     */
    fun onSlideToCancelSuccess() {
        countdownJob?.cancel()
        hardwareController.stopAllAlarms()

        _uiState.value = _uiState.value.copy(
            phase = EmergencyCountdownPhase.CANCELLED_SAFE,
            isShowingCancellationSurvey = true
        )

        viewModelScope.launch {
            _commandEvents.emit(AlarmCommand.StopSirenAndTorch)
        }
    }

    /**
     * User completed or skipped the cancellation survey.
     */
    fun onCancelConfirmed(falseAlarmReason: String?) {
        val logId = _uiState.value.logId
        viewModelScope.launch {
            if (logId != 0L) {
                val log = crashLogRepository.getLogById(logId)
                if (log != null) {
                    crashLogRepository.updateLog(
                        log.copy(
                            wasCancelledByUser = true,
                            falseAlarmReason = falseAlarmReason ?: "User cancelled via Slide-to-Cancel"
                        )
                    )
                }
            }
            _uiState.value = _uiState.value.copy(
                isShowingCancellationSurvey = false,
                surveySelection = falseAlarmReason,
                shouldFinishActivity = true
            )
        }
    }

    /**
     * Immediate SOS trigger: skips remaining timer and fires dispatch cascade right away.
     */
    fun onTriggerSosImmediately() {
        countdownJob?.cancel()
        hardwareController.stopAllAlarms()
        executeAutomatedDispatchCascade()
    }

    /**
     * Automated emergency dispatch across SMS, Email, and Hands-Free Call channels
     * orchestrated by EmergencyDispatchEngine under supervisorScope.
     */
    private fun executeAutomatedDispatchCascade() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = EmergencyCountdownPhase.DISPATCHING_ALERTS,
                notificationStatus = ChannelDispatchStatus.IN_PROGRESS
            )

            val payload = _uiState.value.payload
            val logId = _uiState.value.logId

            // Sync state with engine during dispatch
            val observerJob = launch {
                emergencyDispatchEngine.dispatchState.collectLatest { engineState ->
                    _uiState.value = _uiState.value.copy(
                        contactProgressList = engineState.contactProgressList,
                        notificationStatus = engineState.notificationStatus,
                        mapsUrl = engineState.mapsUrl
                    )
                }
            }

            emergencyDispatchEngine.executeEmergencyCascade(payload, logId)
            observerJob.cancel()

            val finalProgress = emergencyDispatchEngine.dispatchState.value.contactProgressList
            val notifiedCount = finalProgress.count {
                it.smsStatus == ChannelDispatchStatus.SUCCESS ||
                        it.emailStatus == ChannelDispatchStatus.SUCCESS ||
                        it.callStatus == ChannelDispatchStatus.SUCCESS
            }

            _uiState.value = _uiState.value.copy(
                phase = EmergencyCountdownPhase.RESPONDER_BEACON_ACTIVE,
                dispatchedContactsCount = notifiedCount,
                contactProgressList = finalProgress
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        hardwareController.stopAllAlarms()
    }
}
