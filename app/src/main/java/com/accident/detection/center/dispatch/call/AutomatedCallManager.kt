package com.accident.detection.center.dispatch.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import com.accident.detection.center.dispatch.model.CallDispatchResult
import com.accident.detection.center.domain.model.ChannelDispatchStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automates emergency hands-free calling:
 * 1. Places direct phone call via ACTION_CALL targeting Priority #1 emergency contact.
 * 2. Fallbacks to dialer prompt if CALL_PHONE permission is missing.
 * 3. Enforces Speakerphone 1.5 seconds after call initiation to allow the telephony stack
 *    to establish the audio session before overriding the communication device.
 */
@Singleton
class AutomatedCallManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "AutomatedCallManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun hasCallPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Places direct emergency speakerphone call to the specified primary contact.
     */
    @SuppressLint("MissingPermission")
    suspend fun initiateEmergencySpeakerphoneCall(
        primaryContact: EmergencyContactEntity
    ): CallDispatchResult = withContext(Dispatchers.Main) {
        if (!primaryContact.isCallEnabled || primaryContact.phoneNumber.isBlank()) {
            return@withContext CallDispatchResult(
                contactName = primaryContact.name,
                phoneNumber = primaryContact.phoneNumber,
                isSpeakerphoneEnabled = false,
                status = ChannelDispatchStatus.SKIPPED,
                errorMessage = "Emergency phone calling disabled for this contact"
            )
        }

        try {
            val callUri = Uri.parse("tel:${primaryContact.phoneNumber}")

            // 1. Launch Intent.ACTION_CALL (direct call without dialer prompt)
            if (hasCallPhonePermission()) {
                val callIntent = Intent(Intent.ACTION_CALL, callUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)

                // 2. Schedule Speakerphone activation 1.5s after call launch
                scheduleSpeakerphoneActivation()

                CallDispatchResult(
                    contactName = primaryContact.name,
                    phoneNumber = primaryContact.phoneNumber,
                    isSpeakerphoneEnabled = true,
                    status = ChannelDispatchStatus.SUCCESS
                )
            } else {
                // Fallback to ACTION_DIAL if CALL_PHONE permission was revoked
                val dialIntent = Intent(Intent.ACTION_DIAL, callUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)

                scheduleSpeakerphoneActivation()

                CallDispatchResult(
                    contactName = primaryContact.name,
                    phoneNumber = primaryContact.phoneNumber,
                    isSpeakerphoneEnabled = false,
                    status = ChannelDispatchStatus.SUCCESS,
                    errorMessage = "CALL_PHONE permission missing; launched dialer"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to place emergency call: ${e.message}", e)
            CallDispatchResult(
                contactName = primaryContact.name,
                phoneNumber = primaryContact.phoneNumber,
                isSpeakerphoneEnabled = false,
                status = ChannelDispatchStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Failed to initiate emergency call"
            )
        }
    }

    /**
     * Enforces speakerphone routing 1,500ms after call initiation.
     */
    private fun scheduleSpeakerphoneActivation() {
        scope.launch {
            delay(1500L)
            withContext(Dispatchers.Main) {
                enableSpeakerphone()
            }
        }
    }

    /**
     * Activates speakerphone routing handling Android 12+ (API 31+) AudioDeviceInfo
     * and legacy API 26-30 isSpeakerphoneOn.
     */
    fun enableSpeakerphone() {
        val am = audioManager ?: return
        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ API 31+
                val speakerDevice = am.availableCommunicationDevices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speakerDevice != null) {
                    val success = am.setCommunicationDevice(speakerDevice)
                    Log.d(tag, "Set communication device to speaker: $success")
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                }
            } else {
                // Android 8.0 - 11 (API 26-30)
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            }
            Log.d(tag, "Speakerphone successfully activated for hands-free emergency communication")
        } catch (e: Exception) {
            Log.w(tag, "Unable to force speakerphone: ${e.message}")
        }
    }

    /**
     * Restores audio communication device routing.
     */
    fun disableSpeakerphone() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(tag, "Error resetting audio communication mode: ${e.message}")
        }
    }
}

