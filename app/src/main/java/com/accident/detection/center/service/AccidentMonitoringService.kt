package com.accident.detection.center.service

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.accident.detection.center.CrashDetectionApp
import com.accident.detection.center.R
import com.accident.detection.center.data.preferences.UserPreferencesRepository
import com.accident.detection.center.domain.model.CrashDetectionStage
import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.sensor.CrashDetectionEngine
import com.accident.detection.center.sensor.CrashEventPayload
import com.accident.detection.center.ui.MainActivity
import com.accident.detection.center.ui.screens.alarm.EmergencyAlarmActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Production-Grade Sticky Foreground Monitoring Service.
 * Runs continuously across Foreground, Background, and Terminated states
 * with adaptive power governing, exact alarm watchdog recovery, and Android 14/15 FGS compliance.
 */
@AndroidEntryPoint
class AccidentMonitoringService : LifecycleService() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var crashDetectionEngine: CrashDetectionEngine

    @Inject
    lateinit var emergencyLaunchController: EmergencyLaunchController

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val alarmManager by lazy {
        getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private var activeVehicleMode: VehicleMode = VehicleMode.CAR
    private var isDrivingModeActive = false
    private var lastReportedSpeed = 0

    companion object {
        const val SERVICE_NOTIFICATION_ID = 2001
        const val ACTION_SOS_NOW = "com.accident.detection.center.ACTION_SOS_NOW"
        const val ACTION_STOP_SERVICE = "com.accident.detection.center.ACTION_STOP_SERVICE"
        private const val WAKELOCK_TAG = "AccidentDetection:SelfHealingWakeLock"
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithCompliance()
        crashDetectionEngine.startEngine()
        observePreferences()
        observeTelemetryAndStage()
        observeConfirmedCrashes()
        ServiceWatchdogWorker.scheduleWatchdog(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_SOS_NOW -> {
                // Immediate manual SOS triggered from notification action button
                val currentTelemetry = crashDetectionEngine.telemetryState.value
                val manualPayload = CrashEventPayload(
                    latitude = currentTelemetry.latitude,
                    longitude = currentTelemetry.longitude,
                    speedAtImpactKmh = currentTelemetry.speedKmh,
                    peakGForce = currentTelemetry.svmGForce,
                    gyroAngularVelocityDegS = currentTelemetry.rotationalRateRadS * 57.2958f,
                    ambientNoiseDb = currentTelemetry.soundLevelDb,
                    pressureJumpHpa = 0.0f,
                    crashConfidenceScore = 100.0f,
                    vehicleMode = activeVehicleMode,
                    forensicSummary = "Manual SOS button triggered from persistent notification"
                )
                emergencyLaunchController.launchEmergencyAlarm(manualPayload)
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }

        // Return START_STICKY to guarantee Android OS self-restarts the service if killed for memory
        return Service.START_STICKY
    }

    /**
     * Android 14 (API 34) & Android 15 (API 35) compliant startForeground call.
     * Verifies runtime permissions before declaring foreground service types.
     */
    private fun startForegroundWithCompliance() {
        val initialNotification = buildMonitoringNotification(
            title = "Shield Active • Standby (Stationary)",
            content = "5-Sensor Fusion Engine Guarding • Speed gate armed (> ${activeVehicleMode.minSpeedGateKmh.toInt()} km/h)"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgsTypes = 0

            // 1. Location type
            val hasLocation = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasLocation) {
                fgsTypes = fgsTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }

            // 2. Microphone type (for acoustic shock sampling)
            val hasMic = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fgsTypes = fgsTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }

            // 3. Health type
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                fgsTypes = fgsTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            }

            // Fallback for API 29-33 if no specific type matched
            if (fgsTypes == 0) {
                fgsTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }

            try {
                ServiceCompat.startForeground(
                    this,
                    SERVICE_NOTIFICATION_ID,
                    initialNotification,
                    fgsTypes
                )
            } catch (e: Exception) {
                // Fallback to basic startForeground
                startForeground(SERVICE_NOTIFICATION_ID, initialNotification)
            }
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, initialNotification)
        }
    }

    private fun observePreferences() {
        lifecycleScope.launch {
            preferencesRepository.userProfileFlow.collectLatest { profile ->
                activeVehicleMode = profile.selectedVehicleMode
                if (!profile.isProtectionShieldActive) {
                    stopSelf()
                }
            }
        }
    }

    private fun observeTelemetryAndStage() {
        lifecycleScope.launch {
            crashDetectionEngine.telemetryState.collectLatest { telemetry ->
                val currentSpeed = telemetry.speedKmh.toInt()
                val isMoving = currentSpeed >= 10

                // Only update notification text when significant status changes occur to prevent notification flickering
                if (isMoving != isDrivingModeActive || kotlin.math.abs(currentSpeed - lastReportedSpeed) >= 10) {
                    isDrivingModeActive = isMoving
                    lastReportedSpeed = currentSpeed

                    val title: String
                    val content: String

                    when (telemetry.currentStage) {
                        CrashDetectionStage.MOVING_GATE_ACTIVE -> {
                            title = "Shield Active • High-Precision Driving Mode ($currentSpeed km/h)"
                            content = "100 Hz IMU & Acoustic Analysis Guarding • ${activeVehicleMode.displayName}"
                        }
                        CrashDetectionStage.IMPACT_DETECTED -> {
                            title = "⚠️ Impact Detected! Analyzing Rotation & Acoustics..."
                            content = "Evaluating post-impact dynamics • Peak ${String.format("%.1f", telemetry.svmGForce)}G"
                        }
                        CrashDetectionStage.VERIFYING_POST_IMPACT -> {
                            title = "⏳ Verifying Post-Impact Deceleration..."
                            content = "Observing 5s stillness & pedometer rejection window"
                        }
                        CrashDetectionStage.FALSE_ALARM_ABORTED -> {
                            title = "Shield Active • Anomaly Filtered Safely"
                            content = "False alarm rejected • Standby monitoring resumed"
                        }
                        CrashDetectionStage.CRASH_CONFIRMED -> {
                            title = "🚨 VEHICULAR COLLISION CONFIRMED!"
                            content = "Emergency Siren and automated dispatch cascade initiated"
                        }
                        CrashDetectionStage.IDLE -> {
                            title = "Shield Active • Standby (Stationary)"
                            content = "Speed gate armed (> ${activeVehicleMode.minSpeedGateKmh.toInt()} km/h) • ${activeVehicleMode.displayName}"
                        }
                    }

                    updatePersistentNotification(title, content)
                }
            }
        }
    }

    private fun observeConfirmedCrashes() {
        lifecycleScope.launch {
            crashDetectionEngine.confirmedCrashEvents.collectLatest { payload ->
                emergencyLaunchController.launchEmergencyAlarm(payload)
            }
        }
    }

    private fun updatePersistentNotification(title: String, content: String) {
        val notification = buildMonitoringNotification(title, content)
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
    }

    private fun buildMonitoringNotification(title: String, content: String): Notification {
        // 1. PendingIntent for opening Dashboard Command Center
        val dashboardIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val dashboardPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. PendingIntent for Action: SOS Now
        val sosIntent = Intent(this, AccidentMonitoringService::class.java).apply {
            action = ACTION_SOS_NOW
        }
        val sosPendingIntent = PendingIntent.getService(
            this,
            1002,
            sosIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CrashDetectionApp.CHANNEL_SERVICE_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(dashboardPendingIntent)
            .setOngoing(true)
            .setSilent(true) // Never buzz or chime on dynamic status updates
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "SOS NOW",
                sosPendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "OPEN COMMAND CENTER",
                dashboardPendingIntent
            )
            .build()
    }

    /**
     * CRITICAL TERMINATED-STATE SURVIVAL HOOK:
     * Invoked when the user swipes the application away from the Android Recents tray.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // 1. Immediately acquire a brief 10-second WakeLock to guarantee code execution
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        )
        wakeLock?.acquire(10_000L)

        // 2. Create explicit PendingIntent targeting ServiceRestartReceiver
        val restartIntent = Intent(applicationContext, ServiceRestartReceiver::class.java).apply {
            action = ServiceRestartReceiver.ACTION_RESTART_SERVICE
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val restartPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            2002,
            restartIntent,
            flags
        )

        // 3. Schedule exact wake-up in 1,000ms using AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + 1000L

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    restartPendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    restartPendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback if SCHEDULE_EXACT_ALARM is missing
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                restartPendingIntent
            )
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        crashDetectionEngine.stopEngine()
        emergencyLaunchController.releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}

