package com.accident.detection.center.ui.screens.alarm

import com.accident.detection.center.domain.model.ChannelDispatchStatus
import com.accident.detection.center.domain.model.ContactDispatchProgress
import com.accident.detection.center.sensor.CrashEventPayload

enum class EmergencyCountdownPhase {
    COUNTING_DOWN,
    CANCELLED_SAFE,
    DISPATCHING_ALERTS,
    RESPONDER_BEACON_ACTIVE
}

/**
 * State of the 30-Second Lockscreen Emergency Countdown and automated cascade.
 */
data class EmergencyCountdownUiState(
    val phase: EmergencyCountdownPhase = EmergencyCountdownPhase.COUNTING_DOWN,
    val remainingMillis: Long = 30_000L,
    val remainingSeconds: Int = 30,
    val progressFraction: Float = 1.0f,
    val payload: CrashEventPayload = CrashEventPayload(),
    val logId: Long = 0L,
    val isShowingCancellationSurvey: Boolean = false,
    val surveySelection: String? = null,
    val contactProgressList: List<ContactDispatchProgress> = emptyList(),
    val notificationStatus: ChannelDispatchStatus = ChannelDispatchStatus.IDLE,
    val mapsUrl: String = "",
    val isSpeakerphoneActive: Boolean = false,
    val dispatchedContactsCount: Int = 0,
    val shouldFinishActivity: Boolean = false
) {
    val telemetrySummaryText: String
        get() = "Detected: ${String.format("%.1f", payload.peakGForce)}G Impact • ${payload.speedAtImpactKmh.toInt()} km/h • GPS Locked"
}

