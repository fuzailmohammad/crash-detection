package com.accident.detection.center.dispatch.model

import com.accident.detection.center.domain.model.ChannelDispatchStatus

/**
 * Result of an SMS dispatch operation to a specific emergency contact.
 */
data class SmsDispatchResult(
    val contactId: Long,
    val contactName: String,
    val phoneNumber: String,
    val status: ChannelDispatchStatus,
    val errorMessage: String? = null,
    val partsCount: Int = 1
)

/**
 * Result of an Email crash report dispatch operation.
 */
data class EmailDispatchResult(
    val contactId: Long,
    val recipientEmail: String,
    val status: ChannelDispatchStatus,
    val errorMessage: String? = null
)

/**
 * Result of the direct emergency phone call attempt.
 */
data class CallDispatchResult(
    val contactName: String,
    val phoneNumber: String,
    val isSpeakerphoneEnabled: Boolean,
    val status: ChannelDispatchStatus,
    val errorMessage: String? = null
)

/**
 * Full summary of an automated emergency cascade execution.
 */
data class EmergencyCascadeSummary(
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val totalContactsNotified: Int = 0,
    val smsSuccessCount: Int = 0,
    val emailSuccessCount: Int = 0,
    val isCallConnected: Boolean = false,
    val dispatchNotes: String = ""
)

