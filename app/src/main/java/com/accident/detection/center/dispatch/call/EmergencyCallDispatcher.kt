package com.accident.detection.center.dispatch.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import com.accident.detection.center.dispatch.model.CallDispatchResult
import com.accident.detection.center.domain.model.ChannelDispatchStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyCallDispatcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun hasCallPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Executes an emergency phone call with forced speakerphone routing.
     */
    @SuppressLint("MissingPermission")
    suspend fun placeEmergencySpeakerphoneCall(
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
            // Enable Hands-Free Speakerphone Audio
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true

            val callUri = Uri.parse("tel:${primaryContact.phoneNumber}")

            if (hasCallPhonePermission()) {
                val callIntent = Intent(Intent.ACTION_CALL, callUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)

                CallDispatchResult(
                    contactName = primaryContact.name,
                    phoneNumber = primaryContact.phoneNumber,
                    isSpeakerphoneEnabled = true,
                    status = ChannelDispatchStatus.SUCCESS
                )
            } else {
                // Fallback to dialer if CALL_PHONE was revoked
                val dialIntent = Intent(Intent.ACTION_DIAL, callUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)

                CallDispatchResult(
                    contactName = primaryContact.name,
                    phoneNumber = primaryContact.phoneNumber,
                    isSpeakerphoneEnabled = false,
                    status = ChannelDispatchStatus.SUCCESS,
                    errorMessage = "CALL_PHONE permission missing; opened in phone dialer"
                )
            }
        } catch (e: Exception) {
            CallDispatchResult(
                contactName = primaryContact.name,
                phoneNumber = primaryContact.phoneNumber,
                isSpeakerphoneEnabled = false,
                status = ChannelDispatchStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Failed to initiate emergency call"
            )
        }
    }
}

