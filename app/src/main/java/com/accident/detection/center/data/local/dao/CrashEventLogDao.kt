package com.accident.detection.center.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.accident.detection.center.data.local.entity.CrashEventLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CrashEventLogDao {

    @Query("SELECT * FROM crash_event_logs ORDER BY timestampEpochMs DESC")
    fun getAllLogsFlow(): Flow<List<CrashEventLogEntity>>

    @Query("SELECT * FROM crash_event_logs ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int): Flow<List<CrashEventLogEntity>>

    @Query("SELECT * FROM crash_event_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): CrashEventLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CrashEventLogEntity): Long

    @Update
    suspend fun updateLog(log: CrashEventLogEntity)

    @Query("DELETE FROM crash_event_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM crash_event_logs")
    suspend fun clearAllLogs()
}

