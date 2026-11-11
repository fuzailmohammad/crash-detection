package com.accident.detection.center.domain.repository

import com.accident.detection.center.data.local.entity.CrashEventLogEntity
import kotlinx.coroutines.flow.Flow

interface CrashLogRepository {
    fun getAllLogsFlow(): Flow<List<CrashEventLogEntity>>
    fun getRecentLogsFlow(limit: Int): Flow<List<CrashEventLogEntity>>
    suspend fun getLogById(id: Long): CrashEventLogEntity?
    suspend fun insertLog(log: CrashEventLogEntity): Long
    suspend fun updateLog(log: CrashEventLogEntity)
    suspend fun deleteLogById(id: Long)
    suspend fun clearAllLogs()
}

