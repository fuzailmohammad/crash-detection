package com.accident.detection.center.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.accident.detection.center.CrashDetectionApp
import com.accident.detection.center.R
import com.accident.detection.center.sensor.CrashEventPayload
import com.accident.detection.center.ui.screens.alarm.EmergencyAlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyLaunchController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val EMERGENCY_NOTIFICATION_ID = 9999
        private const val WAKELOCK_TAG = "AccidentDetection:EmergencyLockscreenWake"
        private const val WAKELOCK_TIMEOUT_MS = 60_000L // 60 seconds
    }

    /**
     * Wakes device and launches EmergencyAlarmActivity over the lockscreen
     * even from terminated or background state.
     */
    fun launchEmergencyAlarm(payload: CrashEventPayload) {
        // 1. Acquire Partial/Screen WakeLock with ACQUIRE_CAUSES_WAKEUP for 60 seconds
        acquireEmergencyWakeLock()

        // 2. Build explicit Intent for EmergencyAlarmActivity
        val emergencyIntent = Intent(context, EmergencyAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EmergencyAlarmActivity.EXTRA_LATITUDE, payload.latitude)
            putExtra(EmergencyAlarmActivity.EXTRA_LONGITUDE, payload.longitude)
            putExtra(EmergencyAlarmActivity.EXTRA_SPEED_KMH, payload.speedAtImpactKmh)
            putExtra(EmergencyAlarmActivity.EXTRA_PEAK_G, payload.peakGForce)
            putExtra(EmergencyAlarmActivity.EXTRA_CONFIDENCE, payload.crashConfidenceScore)
            putExtra(EmergencyAlarmActivity.EXTRA_VEHICLE_MODE, payload.vehicleMode.name)
            putExtra(EmergencyAlarmActivity.EXTRA_FORENSIC_SUMMARY, payload.forensicSummary)
            putExtra(EmergencyAlarmActivity.EXTRA_IS_SIMULATION, payload.isSimulation)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            EMERGENCY_NOTIFICATION_ID,
            emergencyIntent,
            pendingIntentFlags
        )

        // 3. Build High-Priority FullScreen Notification
        val notification = NotificationCompat.Builder(context, CrashDetectionApp.CHANNEL_EMERGENCY_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 VEHICULAR COLLISION DETECTED!")
            .setContentText("Impact: ${String.format("%.1f", payload.peakGForce)}G • 30s countdown active. Slide to cancel if safe.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "High-confidence collision verified (${payload.crashConfidenceScore.toInt()}%). " +
                            "Speed: ${payload.speedAtImpactKmh.toInt()} km/h. " +
                            "Emergency dispatch cascade will trigger in 30 seconds unless cancelled."
                )
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()

        notificationManager.notify(EMERGENCY_NOTIFICATION_ID, notification)

        // 4. In tandem, launch the Activity directly if overlay or background start is permitted
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)) {
                context.startActivity(emergencyIntent)
            }
        } catch (e: Exception) {
            // Full screen intent fallback handles the lockscreen intercept
        }
    }

    private fun acquireEmergencyWakeLock() {
        try {
            releaseWakeLock()
            @Suppress("DEPRECATION")
            val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE

            wakeLock = powerManager?.newWakeLock(flags, WAKELOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            // Fallback to partial wake lock if screen bright lock fails
            try {
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)?.apply {
                    setReferenceCounted(false)
                    acquire(WAKELOCK_TIMEOUT_MS)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {
        } finally {
            wakeLock = null
        }
    }
}

