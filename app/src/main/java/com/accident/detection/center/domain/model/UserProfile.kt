package com.accident.detection.center.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing the user's identity, vehicle configuration,
 * and first-responder medical data.
 */
@Serializable
data class UserProfile(
    val fullName: String = "",
    val bloodGroup: BloodGroup = BloodGroup.UNKNOWN,
    val allergies: String = "",
    val medicalConditions: String = "",
    val medications: String = "",
    val organDonor: Boolean = false,
    val selectedVehicleMode: VehicleMode = VehicleMode.CAR,
    val isProtectionShieldActive: Boolean = true,
    val isProfileCompleted: Boolean = false
) {
    val hasValidMedicalData: Boolean
        get() = fullName.isNotBlank() && bloodGroup != BloodGroup.UNKNOWN
}

