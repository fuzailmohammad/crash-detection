package com.accident.detection.center.data.repository

import com.accident.detection.center.data.local.dao.CrashEventLogDao
import com.accident.detection.center.data.local.entity.CrashEventLogEntity
import com.accident.detection.center.domain.repository.CrashLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashLogRepositoryImpl @Inject constructor(
    private val crashEventLogDao: CrashEventLogDao
) : CrashLogRepository {

    override fun getAllLogsFlow(): Flow<List<CrashEventLogEntity>> =
        crashEventLogDao.getAllLogsFlow()

    override fun getRecentLogsFlow(limit: Int): Flow<List<CrashEventLogEntity>> =
        crashEventLogDao.getRecentLogsFlow(limit)

    override suspend fun getLogById(id: Long): CrashEventLogEntity? =
        crashEventLogDao.getLogById(id)

    override suspend fun insertLog(log: CrashEventLogEntity): Long =
        crashEventLogDao.insertLog(log)

    override suspend fun updateLog(log: CrashEventLogEntity) =
        crashEventLogDao.updateLog(log)

    override suspend fun deleteLogById(id: Long) =
        crashEventLogDao.deleteLogById(id)

    override suspend fun clearAllLogs() =
        crashEventLogDao.clearAllLogs()
}

