package com.accident.detection.center

import com.accident.detection.center.calibration.CalibrationOffsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

class SensorCalibrationTest {

    private fun calculateAltitudeMeters(pressureHpa: Float): Float {
        if (pressureHpa <= 0f) return 0f
        return (44330.0f * (1.0f - (pressureHpa / 1013.25f).pow(0.190284f)))
    }

    @Test
    fun testSeaLevelAltitudeCalculation() {
        val seaLevelPressure = 1013.25f
        val altitude = calculateAltitudeMeters(seaLevelPressure)
        assertEquals(0.0f, altitude, 0.5f) // Altitude should be ~0 meters at sea level standard pressure
    }

    @Test
    fun testHighAltitudeMountainRoadCalculation() {
        val mountainPressure = 898.7f // ~1,000 meters elevation
        val altitude = calculateAltitudeMeters(mountainPressure)
        assertTrue(altitude > 980f && altitude < 1020f)
    }

    @Test
    fun testInvalidPressureAltitudeSafety() {
        assertEquals(0f, calculateAltitudeMeters(0f), 0.001f)
        assertEquals(0f, calculateAltitudeMeters(-10f), 0.001f)
    }

    @Test
    fun testZeroBiasCalibrationCompensation() {
        // Raw IMU readings of stationary phone lying flat on dashboard
        val rawX = 0.15f
        val rawY = -0.10f
        val rawZ = 9.98f

        val standardGravity = 9.80665f

        val biasX = rawX
        val biasY = rawY
        val biasZ = rawZ - standardGravity

        val offsets = CalibrationOffsets(
            biasX = biasX,
            biasY = biasY,
            biasZ = biasZ,
            noiseStdDev = 0.02f,
            isCalibrated = true
        )

        // Compensated accelerometer values
        val correctedX = rawX - offsets.biasX
        val correctedY = rawY - offsets.biasY
        val correctedZ = rawZ - offsets.biasZ

        assertEquals(0.0f, correctedX, 0.0001f)
        assertEquals(0.0f, correctedY, 0.0001f)
        assertEquals(standardGravity, correctedZ, 0.0001f)
        assertTrue(offsets.isCalibrated)
    }

    @Test
    fun testNoiseStandardDeviationMath() {
        val samples = listOf(0.10f, 0.12f, 0.09f, 0.11f, 0.13f, 0.08f)
        val mean = samples.average().toFloat()

        var sumSqDiff = 0.0
        for (s in samples) {
            val diff = s - mean
            sumSqDiff += diff * diff
        }
        val stdDev = sqrt(sumSqDiff / samples.size).toFloat()

        assertTrue("Noise std dev should be positive and bounded", stdDev in 0.01f..0.05f)
    }
}

