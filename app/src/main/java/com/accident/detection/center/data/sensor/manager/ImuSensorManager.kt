package com.accident.detection.center.data.sensor.manager

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.accident.detection.center.data.sensor.filter.CircularSensorBuffer
import com.accident.detection.center.data.sensor.filter.HighPassFilter
import com.accident.detection.center.data.sensor.filter.ImuSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

data class ImuTelemetryData(
    val accelX: Float = 0.0f,
    val accelY: Float = 0.0f,
    val accelZ: Float = 9.80665f,
    val linearSvmG: Float = 0.0f,
    val totalSvmG: Float = 1.0f,
    val jerkGPerSec: Float = 0.0f,
    val gyroX: Float = 0.0f,
    val gyroY: Float = 0.0f,
    val gyroZ: Float = 0.0f,
    val rotationalRateRadS: Float = 0.0f,
    val barometricPressureHpa: Float = 1013.25f,
    val detectedStepsCount: Int = 0,
    val timestampNs: Long = 0L
)

@Singleton
class ImuSensorManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val barometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val stepDetector: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val highPassFilter = HighPassFilter()
    val circularBuffer = CircularSensorBuffer(capacity = 500) // 5s at 100 Hz

    private val _imuTelemetryFlow = MutableStateFlow(ImuTelemetryData())
    val imuTelemetryFlow: StateFlow<ImuTelemetryData> = _imuTelemetryFlow.asStateFlow()

    private val _stepDetectorEvents = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val stepDetectorEvents: SharedFlow<Int> = _stepDetectorEvents.asSharedFlow()

    private var isListening = false
    private var stepCounter = 0
    private var currentPressureHpa = 1013.25f
    private var currentGyroRotationalRate = 0.0f
    private var gyroX = 0.0f
    private var gyroY = 0.0f
    private var gyroZ = 0.0f

    private val scope = CoroutineScope(Dispatchers.Default)

    fun startListening() {
        if (isListening || sensorManager == null) return
        isListening = true
        highPassFilter.reset()
        circularBuffer.clear()

        // High-frequency 100 Hz IMU registration
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        barometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        stepDetector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        sensorManager?.unregisterListener(this)
    }

    fun resetStepCounter() {
        stepCounter = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]
                val timestampNs = event.timestamp

                val hpResult = highPassFilter.filter(rawX, rawY, rawZ, timestampNs)
                val totalSvmG = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ) / HighPassFilter.STANDARD_GRAVITY_M_S2

                val sample = ImuSample(
                    timestampEpochMs = System.currentTimeMillis(),
                    rawX = rawX,
                    rawY = rawY,
                    rawZ = rawZ,
                    linearSvmG = hpResult.linearSvmG,
                    totalSvmG = totalSvmG,
                    gyroRotationalRateRadS = currentGyroRotationalRate,
                    pressureHpa = currentPressureHpa
                )
                circularBuffer.add(sample)

                _imuTelemetryFlow.value = ImuTelemetryData(
                    accelX = rawX,
                    accelY = rawY,
                    accelZ = rawZ,
                    linearSvmG = hpResult.linearSvmG,
                    totalSvmG = totalSvmG,
                    jerkGPerSec = hpResult.jerkGPerSec,
                    gyroX = gyroX,
                    gyroY = gyroY,
                    gyroZ = gyroZ,
                    rotationalRateRadS = currentGyroRotationalRate,
                    barometricPressureHpa = currentPressureHpa,
                    detectedStepsCount = stepCounter,
                    timestampNs = timestampNs
                )
            }

            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
                currentGyroRotationalRate = sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)
            }

            Sensor.TYPE_PRESSURE -> {
                currentPressureHpa = event.values[0]
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                stepCounter++
                scope.launch {
                    _stepDetectorEvents.emit(stepCounter)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handled automatically by Android sensor HAL
    }
}

