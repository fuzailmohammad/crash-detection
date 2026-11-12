package com.accident.detection.center.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

enum class SensorSamplingMode(val delayMicroseconds: Int) {
    StandbyMode(SensorManager.SENSOR_DELAY_NORMAL),      // ~5-10 Hz (Power-saving parked/stationary mode)
    ActiveDrivingMode(SensorManager.SENSOR_DELAY_GAME)    // ~50-100 Hz (High-frequency impact monitoring)
}

data class MotionSensorReading(
    val linearAx: Float,
    val linearAy: Float,
    val linearAz: Float,
    val rawLinearSvmG: Float,
    val filteredLinearSvmG: Float,
    val gyroPitchDegS: Float,
    val gyroRollDegS: Float,
    val gyroYawDegS: Float,
    val gyroResultantDegS: Float,
    val cabinPressureHpa: Float,
    val stepDetected: Boolean,
    val timestampNs: Long = System.nanoTime(),
    val timestampEpochMs: Long = System.currentTimeMillis()
)

@Singleton
class MotionSensorCollector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rawAccelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccelerationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscopeSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val pressureSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val stepDetectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val signalFilter = SignalFilter()

    private val _currentSamplingMode = MutableStateFlow(SensorSamplingMode.StandbyMode)
    val currentSamplingMode: StateFlow<SensorSamplingMode> = _currentSamplingMode.asStateFlow()

    private val _stepDetectorEvents = MutableSharedFlow<Long>(extraBufferCapacity = 32)
    val stepDetectorEvents: SharedFlow<Long> = _stepDetectorEvents.asSharedFlow()

    private var activeListener: SensorEventListener? = null
    private var isListening = false

    companion object {
        private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
    }

    /**
     * Stream real-time motion sensor readings using callbackFlow.
     */
    fun motionReadingsFlow(mode: SensorSamplingMode = SensorSamplingMode.ActiveDrivingMode): Flow<MotionSensorReading> = callbackFlow {
        _currentSamplingMode.value = mode
        signalFilter.reset()

        var latestLinearX = 0.0f
        var latestLinearY = 0.0f
        var latestLinearZ = 0.0f
        var latestGyroPitch = 0.0f
        var latestGyroRoll = 0.0f
        var latestGyroYaw = 0.0f
        var latestGyroResultant = 0.0f
        var latestPressureHpa = 1013.25f

        // Gravity vector tracking for devices without hardware linear acceleration or gyroscope
        var gravityX = 0.0f
        var gravityY = 0.0f
        var gravityZ = 9.80665f
        var prevGravityX = 0.0f
        var prevGravityY = 0.0f
        var prevGravityZ = 9.80665f
        var lastAccelTimestampNs = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        latestLinearX = event.values[0]
                        latestLinearY = event.values[1]
                        latestLinearZ = event.values[2]

                        val filtered = signalFilter.filterLinearAcceleration(
                            latestLinearX,
                            latestLinearY,
                            latestLinearZ
                        )

                        val reading = MotionSensorReading(
                            linearAx = filtered.ax / SensorManager.GRAVITY_EARTH,
                            linearAy = filtered.ay / SensorManager.GRAVITY_EARTH,
                            linearAz = filtered.az / SensorManager.GRAVITY_EARTH,
                            rawLinearSvmG = filtered.rawSvmGForce,
                            filteredLinearSvmG = filtered.svmGForce,
                            gyroPitchDegS = latestGyroPitch,
                            gyroRollDegS = latestGyroRoll,
                            gyroYawDegS = latestGyroYaw,
                            gyroResultantDegS = latestGyroResultant,
                            cabinPressureHpa = latestPressureHpa,
                            stepDetected = false,
                            timestampNs = event.timestamp
                        )
                        trySend(reading)
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        val rawX = event.values[0]
                        val rawY = event.values[1]
                        val rawZ = event.values[2]

                        // Low-pass filter to isolate gravity (alpha = 0.8)
                        val alpha = 0.8f
                        gravityX = alpha * gravityX + (1.0f - alpha) * rawX
                        gravityY = alpha * gravityY + (1.0f - alpha) * rawY
                        gravityZ = alpha * gravityZ + (1.0f - alpha) * rawZ

                        // Dynamic linear acceleration (gravity-removed)
                        val linX = rawX - gravityX
                        val linY = rawY - gravityY
                        val linZ = rawZ - gravityZ

                        latestLinearX = linX
                        latestLinearY = linY
                        latestLinearZ = linZ

                        val filtered = signalFilter.filterLinearAcceleration(linX, linY, linZ)

                        // If hardware gyroscope is absent, derive angular rate of change from gravity vector
                        if (gyroscopeSensor == null) {
                            val nowNs = event.timestamp
                            val dtSec = if (lastAccelTimestampNs > 0) (nowNs - lastAccelTimestampNs) / 1_000_000_000.0f else 0.02f
                            lastAccelTimestampNs = nowNs
                            if (dtSec in 0.001f..0.5f) {
                                val dGx = gravityX - prevGravityX
                                val dGy = gravityY - prevGravityY
                                val dGz = gravityZ - prevGravityZ
                                val deltaGMag = sqrt(dGx * dGx + dGy * dGy + dGz * dGz)
                                val angularRateRadS = (deltaGMag / SensorManager.GRAVITY_EARTH) / dtSec
                                latestGyroResultant = (angularRateRadS * RAD_TO_DEG).coerceIn(0f, 1500f)
                                prevGravityX = gravityX
                                prevGravityY = gravityY
                                prevGravityZ = gravityZ
                            }
                        }

                        // Total G-force magnitude: sqrt(rawX^2 + rawY^2 + rawZ^2) / g
                        val totalSvmG = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ) / SensorManager.GRAVITY_EARTH

                        val reading = MotionSensorReading(
                            linearAx = rawX / SensorManager.GRAVITY_EARTH,
                            linearAy = rawY / SensorManager.GRAVITY_EARTH,
                            linearAz = rawZ / SensorManager.GRAVITY_EARTH,
                            rawLinearSvmG = totalSvmG,
                            filteredLinearSvmG = totalSvmG,
                            gyroPitchDegS = latestGyroPitch,
                            gyroRollDegS = latestGyroRoll,
                            gyroYawDegS = latestGyroYaw,
                            gyroResultantDegS = latestGyroResultant,
                            cabinPressureHpa = latestPressureHpa,
                            stepDetected = false,
                            timestampNs = event.timestamp
                        )
                        trySend(reading)
                    }

                    Sensor.TYPE_GYROSCOPE -> {
                        // event.values are in rad/s. Convert to deg/s
                        latestGyroPitch = event.values[0] * RAD_TO_DEG
                        latestGyroRoll = event.values[1] * RAD_TO_DEG
                        latestGyroYaw = event.values[2] * RAD_TO_DEG
                        val radS = sqrt(
                            event.values[0] * event.values[0] +
                                    event.values[1] * event.values[1] +
                                    event.values[2] * event.values[2]
                        )
                        latestGyroResultant = radS * RAD_TO_DEG
                    }

                    Sensor.TYPE_PRESSURE -> {
                        latestPressureHpa = event.values[0]
                    }

                    Sensor.TYPE_STEP_DETECTOR -> {
                        _stepDetectorEvents.tryEmit(System.currentTimeMillis())
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Handled by Android hardware abstraction layer
            }
        }

        activeListener = listener
        isListening = true

        registerSensors(listener, mode.delayMicroseconds)

        awaitClose {
            unregisterSensors(listener)
            activeListener = null
            isListening = false
        }
    }

    /**
     * Dynamically adjust sensor sampling rates between Standby (normal) and Active (high-frequency).
     */
    fun setSamplingMode(mode: SensorSamplingMode) {
        if (_currentSamplingMode.value == mode) return
        _currentSamplingMode.value = mode
        val listener = activeListener
        if (isListening && listener != null) {
            unregisterSensors(listener)
            registerSensors(listener, mode.delayMicroseconds)
        }
    }

    private fun registerSensors(listener: SensorEventListener, delay: Int) {
        val sm = sensorManager ?: return
        if (linearAccelerationSensor != null) {
            sm.registerListener(listener, linearAccelerationSensor, delay)
        } else if (rawAccelerometer != null) {
            sm.registerListener(listener, rawAccelerometer, delay)
        }
        gyroscopeSensor?.let { sm.registerListener(listener, it, delay) }
        pressureSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepDetectorSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST) }
    }

    private fun unregisterSensors(listener: SensorEventListener) {
        sensorManager?.unregisterListener(listener)
    }

    fun hasBarometer(): Boolean = pressureSensor != null
    fun hasStepDetector(): Boolean = stepDetectorSensor != null
}

