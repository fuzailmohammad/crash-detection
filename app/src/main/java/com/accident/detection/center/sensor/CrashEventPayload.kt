package com.accident.detection.center.sensor

import com.accident.detection.center.domain.model.VehicleMode

/**
 * High-fidelity telemetry payload generated upon confirmed collision verification.
 */
data class CrashEventPayload(
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedAtImpactKmh: Float = 0.0f,
    val peakGForce: Float = 0.0f,
    val gyroAngularVelocityDegS: Float = 0.0f,
    val ambientNoiseDb: Float = 0.0f,
    val pressureJumpHpa: Float = 0.0f,
    val crashConfidenceScore: Float = 0.0f,
    val vehicleMode: VehicleMode = VehicleMode.CAR,
    val preImpactTelemetry: List<TelemetrySample> = emptyList(),
    val isSimulation: Boolean = false,
    val forensicSummary: String = ""
)

