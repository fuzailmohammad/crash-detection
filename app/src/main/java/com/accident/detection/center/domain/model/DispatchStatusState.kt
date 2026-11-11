package com.accident.detection.center.domain.model

enum class ChannelDispatchStatus {
    IDLE,
    QUEUED,
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    SKIPPED
}

enum class CascadeStage {
    IDLE,
    GRACE_PERIOD_COUNTDOWN,
    DISPATCHING_ALERTS,
    DISPATCH_COMPLETED,
    CANCELLED_BY_USER
}

data class ContactDispatchProgress(
    val contactId: Long,
    val contactName: String,
    val phoneNumber: String,
    val email: String,
    val smsStatus: ChannelDispatchStatus = ChannelDispatchStatus.IDLE,
    val emailStatus: ChannelDispatchStatus = ChannelDispatchStatus.IDLE,
    val callStatus: ChannelDispatchStatus = ChannelDispatchStatus.IDLE,
    val failureReason: String? = null
)

/**
 * Tracks the real-time execution of the Automated Emergency Cascade
 * and the first-responder medical beacon state.
 */
data class DispatchStatusState(
    val stage: CascadeStage = CascadeStage.IDLE,
    val countdownSecondsRemaining: Int = 30,
    val initialCountdownTotalSeconds: Int = 30,
    val notificationStatus: ChannelDispatchStatus = ChannelDispatchStatus.IDLE,
    val contactProgressList: List<ContactDispatchProgress> = emptyList(),
    val isMedicalBeaconVisible: Boolean = false,
    val crashTimestampEpochMs: Long = 0L,
    val incidentLatitude: Double = 0.0,
    val incidentLongitude: Double = 0.0,
    val mapsUrl: String = "",
    val errorMessage: String? = null
) {
    val isCountdownActive: Boolean
        get() = stage == CascadeStage.GRACE_PERIOD_COUNTDOWN && countdownSecondsRemaining > 0

    val countdownProgress: Float
        get() = if (initialCountdownTotalSeconds > 0) {
            countdownSecondsRemaining.toFloat() / initialCountdownTotalSeconds.toFloat()
        } else 0f

    val isAllDispatchesComplete: Boolean
        get() = stage == CascadeStage.DISPATCH_COMPLETED ||
                (contactProgressList.isNotEmpty() && contactProgressList.all {
                    (it.smsStatus == ChannelDispatchStatus.SUCCESS || it.smsStatus == ChannelDispatchStatus.SKIPPED || it.smsStatus == ChannelDispatchStatus.FAILED) &&
                            (it.emailStatus == ChannelDispatchStatus.SUCCESS || it.emailStatus == ChannelDispatchStatus.SKIPPED || it.emailStatus == ChannelDispatchStatus.FAILED) &&
                            (it.callStatus == ChannelDispatchStatus.SUCCESS || it.callStatus == ChannelDispatchStatus.SKIPPED || it.callStatus == ChannelDispatchStatus.FAILED)
                })
}

