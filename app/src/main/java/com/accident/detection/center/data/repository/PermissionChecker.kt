package com.accident.detection.center.data.repository

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.accident.detection.center.domain.model.PermissionHealthState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _permissionState = MutableStateFlow(computePermissions())
    val permissionState: StateFlow<PermissionHealthState> = _permissionState.asStateFlow()

    fun checkAllPermissions(): PermissionHealthState {
        val updatedState = computePermissions()
        _permissionState.value = updatedState
        return updatedState
    }

    private fun computePermissions(): PermissionHealthState {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Prior to Android 10, background location was bound to fine location
        }

        val activityRecognition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val recordAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val sendSms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val callPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val postNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val systemAlertWindow = Settings.canDrawOverlays(context)

        val fullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOptIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        val exactAlarmPermitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }

        val updatedState = PermissionHealthState(
            isFineLocationGranted = fineLocation,
            isBackgroundLocationGranted = backgroundLocation,
            isActivityRecognitionGranted = activityRecognition,
            isRecordAudioGranted = recordAudio,
            isSendSmsGranted = sendSms,
            isCallPhoneGranted = callPhone,
            isPostNotificationsGranted = postNotifications,
            isSystemAlertWindowGranted = systemAlertWindow,
            isFullScreenIntentGranted = fullScreenIntent,
            isBatteryOptimizationIgnored = batteryOptIgnored,
            isExactAlarmPermitted = exactAlarmPermitted
        )

        return updatedState
    }
}
