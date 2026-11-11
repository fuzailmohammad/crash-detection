package com.accident.detection.center.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ActivityContextSnapshot(
    val mostProbableActivity: String = "STATIONARY",
    val confidence: Int = 100,
    val isVehicular: Boolean = false,
    val isBicycle: Boolean = false,
    val isFootOrWalking: Boolean = false,
    val isStill: Boolean = true,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

@Singleton
class ActivityContextCollector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityClient: ActivityRecognitionClient =
        ActivityRecognition.getClient(context)

    private val _currentActivity = MutableStateFlow(ActivityContextSnapshot())
    val currentActivity: StateFlow<ActivityContextSnapshot> = _currentActivity.asStateFlow()

    private var pendingIntent: PendingIntent? = null

    companion object {
        const val ACTION_ACTIVITY_UPDATE = "com.accident.detection.center.ACTION_ACTIVITY_UPDATE"
        private const val REQUEST_CODE_ACTIVITY = 4001
    }

    /**
     * Streams real-time activity updates.
     */
    @SuppressLint("MissingPermission")
    fun activityFlow(detectionIntervalMs: Long = 3000L): Flow<ActivityContextSnapshot> = callbackFlow {
        if (!hasActivityPermission()) {
            close()
            return@callbackFlow
        }

        val intent = Intent(ACTION_ACTIVITY_UPDATE).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE_ACTIVITY, intent, flags)
        pendingIntent = pi

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                if (ActivityRecognitionResult.hasResult(intent)) {
                    val result = ActivityRecognitionResult.extractResult(intent) ?: return
                    val probable = result.mostProbableActivity
                    val typeName = getTypeName(probable.type)
                    val snapshot = ActivityContextSnapshot(
                        mostProbableActivity = typeName,
                        confidence = probable.confidence,
                        isVehicular = probable.type == DetectedActivity.IN_VEHICLE,
                        isBicycle = probable.type == DetectedActivity.ON_BICYCLE,
                        isFootOrWalking = probable.type == DetectedActivity.ON_FOOT ||
                                probable.type == DetectedActivity.WALKING ||
                                probable.type == DetectedActivity.RUNNING,
                        isStill = probable.type == DetectedActivity.STILL,
                        timestampEpochMs = System.currentTimeMillis()
                    )
                    _currentActivity.value = snapshot
                    trySend(snapshot)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(ACTION_ACTIVITY_UPDATE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_ACTIVITY_UPDATE))
        }

        try {
            activityClient.requestActivityUpdates(detectionIntervalMs, pi)
        } catch (e: SecurityException) {
            // Permission missing
        }

        awaitClose {
            try {
                activityClient.removeActivityUpdates(pi)
            } catch (e: Exception) {
                // Ignored
            }
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    fun updateSimulatedActivity(activityType: String, confidence: Int) {
        val isVehicular = activityType.equals("IN_VEHICLE", ignoreCase = true)
        val isBicycle = activityType.equals("ON_BICYCLE", ignoreCase = true)
        val isFoot = activityType.equals("WALKING", ignoreCase = true) ||
                activityType.equals("RUNNING", ignoreCase = true) ||
                activityType.equals("ON_FOOT", ignoreCase = true)
        val isStill = activityType.equals("STILL", ignoreCase = true) ||
                activityType.equals("STATIONARY", ignoreCase = true)

        val snapshot = ActivityContextSnapshot(
            mostProbableActivity = activityType,
            confidence = confidence,
            isVehicular = isVehicular,
            isBicycle = isBicycle,
            isFootOrWalking = isFoot,
            isStill = isStill,
            timestampEpochMs = System.currentTimeMillis()
        )
        _currentActivity.value = snapshot
    }

    private fun getTypeName(type: Int): String {
        return when (type) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.TILTING -> "TILTING"
            else -> "UNKNOWN"
        }
    }

    fun hasActivityPermission(): Boolean {
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

