package com.accident.detection.center.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PowerPolicy(
    val title: String,
    val description: String,
    val imuSamplingDelayUs: Int,
    val gpsIntervalMs: Long
) {
    ULTRA_PERFORMANCE(
        title = "Ultra Performance (Plugged In)",
        description = "Car charger connected. Uncapped 100 Hz IMU + 1,000ms high-accuracy GPS.",
        imuSamplingDelayUs = 10_000, // 100 Hz (SENSOR_DELAY_GAME)
        gpsIntervalMs = 1_000L
    ),
    BALANCED_MOBILE(
        title = "Balanced Adaptive (Standard)",
        description = "Adaptive standby geofence / 100 Hz driving mode based on GPS velocity.",
        imuSamplingDelayUs = 20_000, // 50 Hz
        gpsIntervalMs = 2_000L
    ),
    BATTERY_SAVER_SURVIVAL(
        title = "Power Saver Survival Mode",
        description = "Battery < 20%. Duty-cycled sampling & 10s GPS while keeping crash detection armed.",
        imuSamplingDelayUs = 60_000, // ~16 Hz
        gpsIntervalMs = 10_000L
    ),
    THERMAL_THROTTLED(
        title = "Thermal Protection Throttling",
        description = "Device temperature > 45°C. Lowered sensor frequency to prevent thermal shutdown.",
        imuSamplingDelayUs = 50_000, // 20 Hz
        gpsIntervalMs = 5_000L
    )
}

@Singleton
class PowerGovernor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _currentPolicy = MutableStateFlow(PowerPolicy.BALANCED_MOBILE)
    val currentPolicy: StateFlow<PowerPolicy> = _currentPolicy.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _batteryTempCelsius = MutableStateFlow(25.0f)
    val batteryTempCelsius: StateFlow<Float> = _batteryTempCelsius.asStateFlow()

    private val _isPluggedIn = MutableStateFlow(false)
    val isPluggedIn: StateFlow<Boolean> = _isPluggedIn.asStateFlow()

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val pct = if (level != -1 && scale != -1) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 100
                    _batteryLevel.value = pct

                    val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250)
                    val temp = tempTenths / 10.0f
                    _batteryTempCelsius.value = temp

                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    val isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
                    _isPluggedIn.value = isCharging

                    recalculatePowerPolicy(pct, temp, isCharging)
                }
            }
        }

        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    internal fun recalculatePowerPolicy(batteryPct: Int, tempCelsius: Float, isPlugged: Boolean): PowerPolicy {
        val policy = when {
            tempCelsius >= 45.0f -> PowerPolicy.THERMAL_THROTTLED
            isPlugged -> PowerPolicy.ULTRA_PERFORMANCE
            batteryPct <= 20 -> PowerPolicy.BATTERY_SAVER_SURVIVAL
            else -> PowerPolicy.BALANCED_MOBILE
        }
        _currentPolicy.value = policy
        return policy
    }
}

