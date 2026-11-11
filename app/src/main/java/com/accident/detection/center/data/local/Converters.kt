package com.accident.detection.center.data.local

import androidx.room.TypeConverter
import com.accident.detection.center.domain.model.CrashDetectionStage
import com.accident.detection.center.domain.model.VehicleMode

class Converters {

    @TypeConverter
    fun fromVehicleMode(mode: VehicleMode): String = mode.name

    @TypeConverter
    fun toVehicleMode(value: String): VehicleMode =
        try {
            VehicleMode.valueOf(value)
        } catch (e: Exception) {
            VehicleMode.CAR
        }

    @TypeConverter
    fun fromCrashDetectionStage(stage: CrashDetectionStage): String = stage.name

    @TypeConverter
    fun toCrashDetectionStage(value: String): CrashDetectionStage =
        try {
            CrashDetectionStage.valueOf(value)
        } catch (e: Exception) {
            CrashDetectionStage.IDLE
        }
}

