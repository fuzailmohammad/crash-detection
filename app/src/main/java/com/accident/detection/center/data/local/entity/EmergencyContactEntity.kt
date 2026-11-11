package com.accident.detection.center.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an emergency contact prioritized for automated dispatch.
 */
@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val email: String,
    val relationship: String = "Family",
    val priority: Int = 1, // 1 is highest priority (receives immediate phone call)
    val isCallEnabled: Boolean = true,
    val isSmsEnabled: Boolean = true,
    val isEmailEnabled: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

