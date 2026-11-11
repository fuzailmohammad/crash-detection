package com.accident.detection.center.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.accident.detection.center.data.preferences.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Automatically starts the Accident Monitoring Service upon device reboot,
 * quickboot power on, locked boot, or app package updates.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val profile = preferencesRepository.userProfileFlow.first()
                    if (profile.isProfileCompleted && profile.isProtectionShieldActive) {
                        val serviceIntent = Intent(context, AccidentMonitoringService::class.java)
                        ContextCompat.startForegroundService(context, serviceIntent)

                        // Also register the 15-minute watchdog worker
                        ServiceWatchdogWorker.scheduleWatchdog(context)
                    }
                } catch (e: Exception) {
                    // Logged
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
