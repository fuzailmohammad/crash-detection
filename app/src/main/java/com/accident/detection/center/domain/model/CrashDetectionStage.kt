package com.accident.detection.center.domain.model

/**
 * 5-Stage Multi-Sensor Fusion Pipeline State.
 * Ensures zero false alarms by enforcing sequential gating and post-impact verification.
 */
enum class CrashDetectionStage(
    val stageNumber: Int,
    val title: String,
    val description: String
) {
    IDLE(
        stageNumber = 0,
        title = "Monitoring Standby",
        description = "Awaiting movement gate trigger. Low-power GPS/Activity listening."
    ),
    MOVING_GATE_ACTIVE(
        stageNumber = 1,
        title = "Velocity Gate Active",
        description = "GPS speed or vehicle activity confirms vehicular travel. High-frequency IMU engaged."
    ),
    IMPACT_DETECTED(
        stageNumber = 2,
        title = "G-Force Impact Triggered",
        description = "Tri-axial G-force exceeded vehicle impact threshold. Analyzing rotational and barometric spikes."
    ),
    VERIFYING_POST_IMPACT(
        stageNumber = 3,
        title = "Post-Impact Verification Window",
        description = "Evaluating 5-second post-impact deceleration, vehicle halt, and absence of human walking steps."
    ),
    CRASH_CONFIRMED(
        stageNumber = 4,
        title = "Collision Confirmed",
        description = "Multi-sensor fusion verified high-confidence crash. Grace period countdown initiated."
    ),
    FALSE_ALARM_ABORTED(
        stageNumber = 5,
        title = "Anomaly Filtered (Safe)",
        description = "Event rejected (e.g., speedbump, dropped phone, or normal walking detected post-shock)."
    )
}

