package com.accident.detection.center.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.accident.detection.center.data.preferences.UserPreferencesRepository
import com.accident.detection.center.data.preferences.userDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 15-minute Periodic WorkManager watchdog to guarantee service survival
 * against aggressive OEM battery killers (Doze, app standbys, OEM background killers).
 */
class ServiceWatchdogWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val repository = UserPreferencesRepository(applicationContext)
            val profile = repository.userProfileFlow.first()
            if (profile.isProfileCompleted && profile.isProtectionShieldActive) {
                if (!isServiceRunning(AccidentMonitoringService::class.java)) {
                    val serviceIntent = Intent(applicationContext, AccidentMonitoringService::class.java)
                    ContextCompat.startForegroundService(applicationContext, serviceIntent)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        @Suppress("DEPRECATION")
        val services = manager.getRunningServices(Int.MAX_VALUE)
        return services.any { it.service.className == serviceClass.name }
    }

    companion object {
        private const val WORK_NAME = "service_watchdog_periodic"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // 5-minute flex interval
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun scheduleWatchdog(context: Context) {
            schedule(context)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
