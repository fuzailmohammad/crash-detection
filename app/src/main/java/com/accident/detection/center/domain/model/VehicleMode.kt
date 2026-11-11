package com.accident.detection.center.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Defines vehicle modes with tailored multi-sensor thresholds to eliminate false alarms
 * while ensuring sensitivity across distinct transport kinematics.
 */
enum class VehicleMode(
    val displayName: String,
    val description: String,
    val minSpeedGateKmh: Float,
    val impactGThreshold: Float,
    val gyroRotationalSpikeRadPerSec: Float,
    val barometerPressureSpikeHpa: Float,
    val postImpactStillnessThresholdG: Float,
    val postImpactVerificationWindowSec: Int
) {
    CAR(
        displayName = "Automobile / Car",
        description = "Tailored for enclosed 4-wheel vehicles with airbag cabin pressure & severe deceleration.",
        minSpeedGateKmh = 25.0f,
        impactGThreshold = 4.2f, // Cabin structure absorbs minor shocks; crashes exceed 4.0G
        gyroRotationalSpikeRadPerSec = 4.5f,
        barometerPressureSpikeHpa = 1.2f, // Airbag explosion creates distinct ~1-2 hPa instantaneous cabin spike
        postImpactStillnessThresholdG = 0.35f,
        postImpactVerificationWindowSec = 5
    ),
    TWO_WHEELER(
        displayName = "Motorcycle / Scooter",
        description = "High dynamic lean angles with lower lateral impact thresholds and skid detection.",
        minSpeedGateKmh = 18.0f,
        impactGThreshold = 3.2f, // Bike slides and high-sides trigger at lower G-forces
        gyroRotationalSpikeRadPerSec = 6.8f, // Rapid angular tilt / roll is common in motorcycle crashes
        barometerPressureSpikeHpa = 0.3f, // No enclosed cabin; acoustic/barometric pressure is minimal
        postImpactStillnessThresholdG = 0.40f,
        postImpactVerificationWindowSec = 6
    ),
    BICYCLE(
        displayName = "Bicycle / E-Bike",
        description = "Sensitive to low-speed curb impacts, sudden falls, and rapid vertical deceleration.",
        minSpeedGateKmh = 12.0f,
        impactGThreshold = 2.4f, // Human-powered falls happen at ~2.0G - 2.5G
        gyroRotationalSpikeRadPerSec = 5.0f,
        barometerPressureSpikeHpa = 0.2f,
        postImpactStillnessThresholdG = 0.30f,
        postImpactVerificationWindowSec = 5
    );

    val icon: ImageVector
        get() = when (this) {
            CAR -> Icons.Default.DirectionsCar
            TWO_WHEELER -> Icons.Default.TwoWheeler
            BICYCLE -> Icons.Default.DirectionsBike
        }
}

