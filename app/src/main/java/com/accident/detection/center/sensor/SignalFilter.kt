package com.accident.detection.center.sensor

import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * 1D Discrete Kalman Filter for smoothing noisy sensor observations
 * and isolating true mechanical motion from high-frequency road vibrations.
 *
 * @param processNoise (Q) Variance of process model error (true motion dynamic changes).
 * @param measurementNoise (R) Variance of sensor measurement noise (engine rumble, gravel road jitter).
 * @param estimationError (P) Initial estimation error covariance.
 */
class KalmanFilter1D(
    private val processNoise: Float = 0.05f,
    private val measurementNoise: Float = 0.85f,
    private var estimationError: Float = 1.0f
) {
    private var state: Float = 0.0f
    private var isInitialized: Boolean = false

    fun update(measurement: Float): Float {
        if (!isInitialized) {
            state = measurement
            isInitialized = true
            return state
        }

        // 1. Time Update (Predict)
        val predictedError = estimationError + processNoise

        // 2. Measurement Update (Correct)
        val kalmanGain = predictedError / (predictedError + measurementNoise)
        state += kalmanGain * (measurement - state)
        estimationError = (1.0f - kalmanGain) * predictedError

        return state
    }

    fun reset() {
        state = 0.0f
        estimationError = 1.0f
        isInitialized = false
    }
}

/**
 * Tri-Axial Kalman Filter maintaining independent filters for X, Y, and Z sensor axes.
 */
class TriAxialKalmanFilter(
    processNoise: Float = 0.05f,
    measurementNoise: Float = 0.85f
) {
    private val filterX = KalmanFilter1D(processNoise, measurementNoise)
    private val filterY = KalmanFilter1D(processNoise, measurementNoise)
    private val filterZ = KalmanFilter1D(processNoise, measurementNoise)

    fun filter(x: Float, y: Float, z: Float): FloatArray {
        return floatArrayOf(
            filterX.update(x),
            filterY.update(y),
            filterZ.update(z)
        )
    }

    fun reset() {
        filterX.reset()
        filterY.reset()
        filterZ.reset()
    }
}

/**
 * Processed acceleration output with filtered components and G-force SVM.
 */
data class FilteredAcceleration(
    val ax: Float,
    val ay: Float,
    val az: Float,
    val svmGForce: Float,
    val rawSvmGForce: Float
)

/**
 * Core signal filtering utility.
 */
class SignalFilter(
    processNoise: Float = 0.04f,
    measurementNoise: Float = 0.75f
) {
    private val triAxialKalman = TriAxialKalmanFilter(processNoise, measurementNoise)

    fun filterLinearAcceleration(rawAx: Float, rawAy: Float, rawAz: Float): FilteredAcceleration {
        val filtered = triAxialKalman.filter(rawAx, rawAy, rawAz)
        val fx = filtered[0]
        val fy = filtered[1]
        val fz = filtered[2]

        val rawSvm = sqrt(rawAx * rawAx + rawAy * rawAy + rawAz * rawAz) / SensorManager.GRAVITY_EARTH
        val filteredSvm = sqrt(fx * fx + fy * fy + fz * fz) / SensorManager.GRAVITY_EARTH

        return FilteredAcceleration(
            ax = fx,
            ay = fy,
            az = fz,
            svmGForce = filteredSvm,
            rawSvmGForce = rawSvm
        )
    }

    fun reset() {
        triAxialKalman.reset()
    }

    companion object {
        fun calculateGForce(ax: Float, ay: Float, az: Float): Float {
            return sqrt(ax * ax + ay * ay + az * az) / SensorManager.GRAVITY_EARTH
        }
    }
}

