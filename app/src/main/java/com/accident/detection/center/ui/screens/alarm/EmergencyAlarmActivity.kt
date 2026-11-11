package com.accident.detection.center.ui.screens.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.hardware.EmergencyHardwareController
import com.accident.detection.center.ui.theme.AccidentDetectionCenterTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SingleInstance lockscreen-bypassing Activity hosting the 30-Second Alarm Countdown.
 *
 * Resilience & Lockscreen Capabilities:
 * 1. setShowWhenLocked(true) & setTurnScreenOn(true) on API 27+ with Keyguard dismissal.
 * 2. WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON + FLAG_ALLOW_LOCK_WHILE_SCREEN_ON.
 * 3. 100% Screen Brightness override (screenBrightness = 1.0f) for maximum visibility in daylight/cracked glass.
 * 4. OnBackPressedCallback(true) disables back-button dismissal to prevent accidental pocket cancellation.
 */
@AndroidEntryPoint
class EmergencyAlarmActivity : ComponentActivity() {

    private val viewModel: EmergencyAlarmViewModel by viewModels()

    @Inject
    lateinit var emergencyHardwareController: EmergencyHardwareController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Lockscreen Wake-up and Screen Brightness Flags
        configureLockscreenWakeupAndBrightness()

        // 2. Disable accidental back-button dismissal
        configureBackPressInterception()

        // 3. Extract Incident Telemetry from Intent Extras
        val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
        val logId = intent.getLongExtra(EXTRA_LOG_ID, 0L)
        val speedKmh = intent.getFloatExtra(EXTRA_SPEED_KMH, 0.0f)
        val peakG = intent.getFloatExtra(EXTRA_PEAK_G, 0.0f)
        val confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 100.0f)
        val vehicleModeStr = intent.getStringExtra(EXTRA_VEHICLE_MODE) ?: VehicleMode.CAR.name
        val vehicleMode = try {
            VehicleMode.valueOf(vehicleModeStr)
        } catch (e: Exception) {
            VehicleMode.CAR
        }
        val summary = intent.getStringExtra(EXTRA_FORENSIC_SUMMARY) ?: ""
        val isSimulation = intent.getBooleanExtra(EXTRA_IS_SIMULATION, false)

        // 4. Initialize Alarm and start high-precision countdown ticker
        viewModel.initializeAlarm(
            latitude = latitude,
            longitude = longitude,
            logId = logId,
            speedKmh = speedKmh,
            peakG = peakG,
            confidence = confidence,
            vehicleMode = vehicleMode,
            summary = summary,
            isSimulation = isSimulation
        )

        // 5. Observe Alarm Commands (Phone Call, Stop Hardware, Finish Activity)
        observeActivityLifecycleCommands()

        // 6. Set Compose UI
        setContent {
            AccidentDetectionCenterTheme(isEmergencyMode = true) {
                EmergencyCountdownScreen(
                    viewModel = viewModel,
                    onDismissActivity = { finish() }
                )
            }
        }
    }

    private fun configureLockscreenWakeupAndBrightness() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        // Force 100% Screen Brightness
        val layoutParams = window.attributes
        layoutParams.screenBrightness = 1.0f
        window.attributes = layoutParams
    }

    private fun configureBackPressInterception() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentional anti-pocket lock: Back button will NOT dismiss the alarm.
                // The user must deliberately perform the Slide-to-Cancel gesture.
            }
        })
    }

    private fun observeActivityLifecycleCommands() {
        // Finish Activity when ViewModel signals completion
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state.shouldFinishActivity) {
                    finish()
                }
            }
        }

        // Handle single-shot action commands
        lifecycleScope.launch {
            viewModel.commandEvents.collectLatest { command ->
                when (command) {
                    is AlarmCommand.StopSirenAndTorch -> {
                        emergencyHardwareController.stopAllAlarms()
                    }
                    is AlarmCommand.SpeakTts -> {
                        // Handled natively within EmergencyHardwareController
                    }
                    is AlarmCommand.InitiatePhoneCall -> {
                        emergencyHardwareController.stopAllAlarms()
                        placeSpeakerphoneCall(command.phoneNumber)
                    }
                }
            }
        }
    }

    private fun placeSpeakerphoneCall(phoneNumber: String) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = true
        } catch (e: Exception) {
            // Ignore
        }

        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(callIntent)
        } catch (e: SecurityException) {
            // Fallback to dialer if CALL_PHONE was revoked
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(dialIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Guarantee no hardware siren, strobe, or vibrator leaks
        emergencyHardwareController.stopAllAlarms()
    }

    companion object {
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_LOG_ID = "extra_log_id"
        const val EXTRA_SPEED_KMH = "extra_speed_kmh"
        const val EXTRA_PEAK_G = "extra_peak_g"
        const val EXTRA_CONFIDENCE = "extra_confidence"
        const val EXTRA_VEHICLE_MODE = "extra_vehicle_mode"
        const val EXTRA_FORENSIC_SUMMARY = "extra_forensic_summary"
        const val EXTRA_IS_SIMULATION = "extra_is_simulation"
    }
}
