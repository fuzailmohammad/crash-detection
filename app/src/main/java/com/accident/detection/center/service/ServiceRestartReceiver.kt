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
 * Self-healing watchdog receiver invoked when AccidentMonitoringService is killed,
 * or when the user swipes the app away from the Android Recents tray.
 */
@AndroidEntryPoint
class ServiceRestartReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profile = preferencesRepository.userProfileFlow.first()
                if (profile.isProfileCompleted && profile.isProtectionShieldActive) {
                    val serviceIntent = Intent(context, AccidentMonitoringService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            } catch (e: Exception) {
                // Logged
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RESTART_SERVICE = "com.accident.detection.center.ACTION_RESTART_SERVICE"
    }
}

