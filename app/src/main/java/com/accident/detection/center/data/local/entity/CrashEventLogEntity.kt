package com.accident.detection.center.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.accident.detection.center.domain.model.CrashDetectionStage
import com.accident.detection.center.domain.model.VehicleMode

/**
 * Room entity recording forensic telemetry of detected collisions,
 * simulation tests, and false alarms.
 */
@Entity(tableName = "crash_event_logs")
data class CrashEventLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val speedAtImpactKmh: Float,
    val peakGForce: Float,
    val gyroRotationalRateRadS: Float,
    val ambientNoiseDb: Float,
    val barometricPressureSpikeHpa: Float,
    val vehicleMode: VehicleMode,
    val finalDetectionStage: CrashDetectionStage,
    val crashConfidenceScore: Float,
    val wasCancelledByUser: Boolean = false,
    val falseAlarmReason: String? = null,
    val sosDispatchTriggered: Boolean = false,
    val contactsNotifiedCount: Int = 0,
    val dispatchSummary: String = "",
    val isSimulation: Boolean = false
)

