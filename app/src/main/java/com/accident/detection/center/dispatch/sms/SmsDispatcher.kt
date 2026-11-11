package com.accident.detection.center.dispatch.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import com.accident.detection.center.dispatch.model.SmsDispatchResult
import com.accident.detection.center.domain.model.ChannelDispatchStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsDispatcher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun getSmsManager(): SmsManager? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    /**
     * Checks if the device has permission to send hardware SMS.
     */
    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Dispatches silent SMS directly via device cellular telephony.
     */
    suspend fun dispatchSmsToContact(
        contact: EmergencyContactEntity,
        message: String
    ): SmsDispatchResult = withContext(Dispatchers.IO) {
        if (!contact.isSmsEnabled) {
            return@withContext SmsDispatchResult(
                contactId = contact.id,
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                status = ChannelDispatchStatus.SKIPPED,
                errorMessage = "SMS disabled for this contact"
            )
        }

        if (!hasSmsPermission()) {
            return@withContext SmsDispatchResult(
                contactId = contact.id,
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                status = ChannelDispatchStatus.FAILED,
                errorMessage = "SEND_SMS permission not granted"
            )
        }

        val smsManager = getSmsManager()
        if (smsManager == null) {
            return@withContext SmsDispatchResult(
                contactId = contact.id,
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                status = ChannelDispatchStatus.FAILED,
                errorMessage = "SmsManager unavailable on device"
            )
        }

        try {
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    contact.phoneNumber,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    contact.phoneNumber,
                    null,
                    message,
                    null,
                    null
                )
            }

            SmsDispatchResult(
                contactId = contact.id,
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                status = ChannelDispatchStatus.SUCCESS,
                partsCount = parts.size
            )
        } catch (e: Exception) {
            SmsDispatchResult(
                contactId = contact.id,
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                status = ChannelDispatchStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Failed to transmit SMS"
            )
        }
    }

    /**
     * Dispatches emergency SMS to all authorized contacts in parallel/batch.
     */
    suspend fun dispatchSmsBatch(
        contacts: List<EmergencyContactEntity>,
        message: String
    ): List<SmsDispatchResult> = withContext(Dispatchers.IO) {
        contacts.filter { it.isSmsEnabled }.map { contact ->
            dispatchSmsToContact(contact, message)
        }
    }
}

