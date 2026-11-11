package com.accident.detection.center.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.accident.detection.center.data.local.dao.CrashEventLogDao
import com.accident.detection.center.data.local.dao.EmergencyContactDao
import com.accident.detection.center.data.local.entity.CrashEventLogEntity
import com.accident.detection.center.data.local.entity.EmergencyContactEntity

@Database(
    entities = [
        EmergencyContactEntity::class,
        CrashEventLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun crashEventLogDao(): CrashEventLogDao

    companion object {
        const val DATABASE_NAME = "accident_detection_center.db"
    }
}

