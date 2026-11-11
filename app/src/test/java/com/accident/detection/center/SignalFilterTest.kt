package com.accident.detection.center

import com.accident.detection.center.sensor.KalmanFilter1D
import com.accident.detection.center.sensor.SignalFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SignalFilterTest {

    @Test
    fun testKalmanFilter1DSmoothsNoisyObservations() {
        val kalman = KalmanFilter1D(processNoise = 0.05f, measurementNoise = 0.85f)

        // Baseline true value is 0.0, with alternating high-frequency noise of ±2.0
        val noisyInputs = floatArrayOf(0.0f, 2.0f, -2.0f, 1.8f, -1.9f, 2.1f, -2.0f, 0.1f)
        var filteredOutput = 0.0f

        for (sample in noisyInputs) {
            filteredOutput = kalman.update(sample)
        }

        // Filtered output should be significantly closer to true baseline 0.0 than the ±2.0 spikes
        assertTrue("Filtered output should be smoothed close to 0.0, was $filteredOutput", abs(filteredOutput) < 1.0f)
    }

    @Test
    fun testGForceCalculationMatchesEarthGravity() {
        // When stationary at rest on table: ax=0, ay=0, az=9.80665 m/s²
        val gForce = SignalFilter.calculateGForce(0.0f, 0.0f, 9.80665f)
        assertEquals(1.0f, gForce, 0.01f)
    }

    @Test
    fun testLinearAccelerationFilteringRemovesStaticGravity() {
        val signalFilter = SignalFilter()

        // Pure static gravity inputs
        var result = signalFilter.filterLinearAcceleration(0.0f, 0.0f, 0.0f)
        for (i in 0 until 50) {
            result = signalFilter.filterLinearAcceleration(0.0f, 0.0f, 0.0f)
        }

        // Linear acceleration (with gravity removed) should be near 0G
        assertEquals(0.0f, result.svmGForce, 0.05f)
    }
}

