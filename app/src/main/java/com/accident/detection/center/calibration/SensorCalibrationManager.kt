package com.accident.detection.center.calibration

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.accident.detection.center.data.preferences.userDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

data class HardwareSensorSpec(
    val sensorName: String,
    val vendor: String,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val minDelayMicrosec: Int,
    val isPresent: Boolean
)

data class BatteryDiagnostics(
    val percentage: Int = 100,
    val temperatureCelsius: Float = 25.0f,
    val isCharging: Boolean = false,
    val healthStatus: String = "Good",
    val powerSource: String = "Battery"
)

data class CalibrationOffsets(
    val biasX: Float = 0.0f,
    val biasY: Float = 0.0f,
    val biasZ: Float = 0.0f,
    val noiseStdDev: Float = 0.05f,
    val isCalibrated: Boolean = false
)

@Singleton
class SensorCalibrationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val dataStore = context.userDataStore

    private object PreferencesKeys {
        val BIAS_X = floatPreferencesKey("calib_bias_x")
        val BIAS_Y = floatPreferencesKey("calib_bias_y")
        val BIAS_Z = floatPreferencesKey("calib_bias_z")
        val NOISE_STD_DEV = floatPreferencesKey("calib_noise_std_dev")
        val IS_CALIBRATED = booleanPreferencesKey("calib_is_calibrated")
    }

    private val _currentOffsets = MutableStateFlow(CalibrationOffsets())
    val currentOffsets: StateFlow<CalibrationOffsets> = _currentOffsets.asStateFlow()

    init {
        // Load persisted calibration offsets
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).run {
            kotlinx.coroutines.launch {
                val prefs = dataStore.data.first()
                _currentOffsets.value = CalibrationOffsets(
                    biasX = prefs[PreferencesKeys.BIAS_X] ?: 0.0f,
                    biasY = prefs[PreferencesKeys.BIAS_Y] ?: 0.0f,
                    biasZ = prefs[PreferencesKeys.BIAS_Z] ?: 0.0f,
                    noiseStdDev = prefs[PreferencesKeys.NOISE_STD_DEV] ?: 0.05f,
                    isCalibrated = prefs[PreferencesKeys.IS_CALIBRATED] ?: false
                )
            }
        }
    }

    fun getAccelerometerSpec(): HardwareSensorSpec {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        return if (sensor != null) {
            HardwareSensorSpec(
                sensorName = sensor.name,
                vendor = sensor.vendor,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution,
                powerMa = sensor.power,
                minDelayMicrosec = sensor.minDelay,
                isPresent = true
            )
        } else {
            HardwareSensorSpec("Unavailable", "None", 0f, 0f, 0f, 0, false)
        }
    }

    fun getGyroscopeSpec(): HardwareSensorSpec {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        return if (sensor != null) {
            HardwareSensorSpec(
                sensorName = sensor.name,
                vendor = sensor.vendor,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution,
                powerMa = sensor.power,
                minDelayMicrosec = sensor.minDelay,
                isPresent = true
            )
        } else {
            HardwareSensorSpec("Unavailable", "None", 0f, 0f, 0f, 0, false)
        }
    }

    fun getBarometerSpec(): HardwareSensorSpec {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        return if (sensor != null) {
            HardwareSensorSpec(
                sensorName = sensor.name,
                vendor = sensor.vendor,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution,
                powerMa = sensor.power,
                minDelayMicrosec = sensor.minDelay,
                isPresent = true
            )
        } else {
            HardwareSensorSpec("Not Installed", "None", 0f, 0f, 0f, 0, false)
        }
    }

    fun hasStepDetectorHardware(): Boolean {
        return sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    }

    /**
     * Calculates altitude based on standard barometric formula.
     * h = 44330 * (1 - (P / 1013.25)^(1/5.255))
     */
    fun calculateAltitudeMeters(pressureHpa: Float): Float {
        if (pressureHpa <= 0f) return 0f
        return (44330.0f * (1.0f - (pressureHpa / 1013.25f).pow(0.190284f)))
    }

    /**
     * Inspects current battery and thermal telemetry.
     */
    fun getBatteryDiagnostics(): BatteryDiagnostics {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return BatteryDiagnostics()

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level != -1 && scale != -1) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 100

        val tempTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250)
        val temperatureCelsius = tempTenths / 10.0f

        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

        val powerSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable / Car Charger"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Qi Charger"
            else -> "Internal Battery"
        }

        val health = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEATING"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            else -> "Normal"
        }

        return BatteryDiagnostics(
            percentage = percentage,
            temperatureCelsius = temperatureCelsius,
            isCharging = isCharging,
            healthStatus = health,
            powerSource = powerSource
        )
    }

    /**
     * Runs an interactive calibration routine over 100 samples
     * to eliminate factory sensor offset bias and measure noise variance.
     */
    fun runInteractiveCalibration(): Flow<Int> = callbackFlow {
        val sm = sensorManager ?: run {
            close()
            return@callbackFlow
        }
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            close()
            return@callbackFlow
        }

        val samplesX = mutableListOf<Float>()
        val samplesY = mutableListOf<Float>()
        val samplesZ = mutableListOf<Float>()
        val targetSamples = 100

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                samplesX.add(event.values[0])
                samplesY.add(event.values[1])
                samplesZ.add(event.values[2])

                val progress = ((samplesX.size.toFloat() / targetSamples.toFloat()) * 100).toInt()
                trySend(progress)

                if (samplesX.size >= targetSamples) {
                    sm.unregisterListener(this)

                    // Calculate average biases
                    val meanX = samplesX.average().toFloat()
                    val meanY = samplesY.average().toFloat()
                    val meanZ = samplesZ.average().toFloat()

                    // Compute standard deviation of noise
                    var varSum = 0.0f
                    for (x in samplesX) {
                        val diff = x - meanX
                        varSum += diff * diff
                    }
                    val stdDev = sqrt(varSum / samplesX.size)

                    // Assuming phone is resting flat on table (ideal ax=0, ay=0, az=9.80665)
                    val biasX = meanX
                    val biasY = meanY
                    val biasZ = meanZ - 9.80665f

                    val offsets = CalibrationOffsets(
                        biasX = biasX,
                        biasY = biasY,
                        biasZ = biasZ,
                        noiseStdDev = stdDev,
                        isCalibrated = true
                    )
                    _currentOffsets.value = offsets

                    // Persist to DataStore
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).run {
                        kotlinx.coroutines.launch {
                            dataStore.edit { p ->
                                p[PreferencesKeys.BIAS_X] = biasX
                                p[PreferencesKeys.BIAS_Y] = biasY
                                p[PreferencesKeys.BIAS_Z] = biasZ
                                p[PreferencesKeys.NOISE_STD_DEV] = stdDev
                                p[PreferencesKeys.IS_CALIBRATED] = true
                            }
                        }
                    }

                    channel.close()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_FASTEST)

        awaitClose {
            sm.unregisterListener(listener)
        }
    }
}

