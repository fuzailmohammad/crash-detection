package com.accident.detection.center.domain.model

import kotlin.math.sqrt

/**
 * Real-time sensor telemetry state streamed to the Command Center HUD
 * and evaluated by the multi-sensor fusion engine.
 */
data class SensorTelemetryState(
    // Accelerometer (m/s² converted to G-Force where 1G ≈ 9.80665 m/s²)
    val accelX: Float = 0.0f,
    val accelY: Float = 0.0f,
    val accelZ: Float = 1.0f,
    val svmGForce: Float = 1.0f, // sqrt(x^2 + y^2 + z^2)

    // Gyroscope (rad/s)
    val gyroX: Float = 0.0f,
    val gyroY: Float = 0.0f,
    val gyroZ: Float = 0.0f,
    val rotationalRateRadS: Float = 0.0f, // sqrt(gx^2 + gy^2 + gz^2)

    // Environment & Acoustic
    val barometricPressureHpa: Float = 1013.25f,
    val soundLevelDb: Float = 45.0f,

    // GPS & Motion
    val speedKmh: Float = 0.0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val gpsAccuracyMeters: Float = 0.0f,

    // Activity Recognition & Pedometer
    val activityType: String = "STATIONARY",
    val activityConfidence: Int = 100,
    val postImpactStepCount: Int = 0,

    // Algorithm Stage & Confidence
    val currentStage: CrashDetectionStage = CrashDetectionStage.IDLE,
    val crashConfidenceScore: Float = 0.0f, // 0.0 to 100.0%
    val lastUpdatedTimestampEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        fun calculateSvm(x: Float, y: Float, z: Float): Float {
            return sqrt(x * x + y * y + z * z)
        }
    }
}

