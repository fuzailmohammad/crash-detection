package com.accident.detection.center

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CrashDetectionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Emergency Siren & Fullscreen Channel
            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY_ALERTS,
                getString(R.string.channel_emergency_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_emergency_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttributes)
                setBypassDnd(true)
            }

            // 2. Persistent Monitoring Foreground Service Channel
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_STATUS,
                getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_service_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            notificationManager.createNotificationChannel(emergencyChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_EMERGENCY_ALERTS = "channel_emergency_alerts_v1"
        const val CHANNEL_SERVICE_STATUS = "channel_service_status_v1"
    }
}

