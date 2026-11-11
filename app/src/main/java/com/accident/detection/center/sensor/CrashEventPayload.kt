package com.accident.detection.center.sensor

import com.accident.detection.center.domain.model.VehicleMode

/**
 * High-fidelity telemetry payload generated upon confirmed collision verification.
 */
data class CrashEventPayload(
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val speedAtImpactKmh: Float,
    val peakGForce: Float,
    val gyroAngularVelocityDegS: Float,
    val ambientNoiseDb: Float,
    val pressureJumpHpa: Float,
    val crashConfidenceScore: Float,
    val vehicleMode: VehicleMode,
    val preImpactTelemetry: List<TelemetrySample> = emptyList(),
    val isSimulation: Boolean = false,
    val forensicSummary: String = ""
)

