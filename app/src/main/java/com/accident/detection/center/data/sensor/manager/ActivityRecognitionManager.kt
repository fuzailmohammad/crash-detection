package com.accident.detection.center.data.sensor.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UserActivityData(
    val activityType: String = "STATIONARY",
    val confidence: Int = 100,
    val isVehicular: Boolean = false,
    val isWalkingOrRunning: Boolean = false,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

@Singleton
class ActivityRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _activityFlow = MutableStateFlow(UserActivityData())
    val activityFlow: StateFlow<UserActivityData> = _activityFlow.asStateFlow()

    private var isTracking = false

    fun startTracking() {
        if (isTracking || !hasPermission()) return
        isTracking = true
    }

    fun stopTracking() {
        isTracking = false
    }

    fun updateActivity(type: String, confidence: Int) {
        val isVehicular = type.equals("IN_VEHICLE", ignoreCase = true) || type.equals("ON_BICYCLE", ignoreCase = true)
        val isWalking = type.equals("WALKING", ignoreCase = true) || type.equals("RUNNING", ignoreCase = true) || type.equals("ON_FOOT", ignoreCase = true)

        _activityFlow.value = UserActivityData(
            activityType = type,
            confidence = confidence,
            isVehicular = isVehicular,
            isWalkingOrRunning = isWalking,
            timestampEpochMs = System.currentTimeMillis()
        )
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

