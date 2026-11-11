package com.accident.detection.center

import com.accident.detection.center.sensor.SensorRingBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorRingBufferTest {

    @Test
    fun testRingBufferCapacityWrapAround() {
        val ringBuffer = SensorRingBuffer(capacity = 5)

        // Insert 8 samples into a buffer of size 5
        for (i in 1..8) {
            ringBuffer.record(
                speedKmh = i * 10.0f,
                gForce = 1.0f,
                gyroAngularVelocityDegS = 0.0f,
                cabinPressureHpa = 1013.0f,
                soundLevelDb = 40.0f,
                timestampEpochMs = System.currentTimeMillis() + i * 100
            )
        }

        val snapshot = ringBuffer.getTelemetryInRecentWindow(15_000L)
        assertEquals(5, snapshot.size)
        // Earliest sample kept should be sample 4 (40.0 km/h)
        assertEquals(40.0f, snapshot.first().speedKmh, 0.1f)
        // Latest sample should be sample 8 (80.0 km/h)
        assertEquals(80.0f, snapshot.last().speedKmh, 0.1f)
    }

    @Test
    fun testMaxSpeedInWindowRetrieval() {
        val ringBuffer = SensorRingBuffer(capacity = 100)
        val now = System.currentTimeMillis()

        ringBuffer.record(speedKmh = 25.0f, gForce = 1.0f, gyroAngularVelocityDegS = 0.0f, cabinPressureHpa = 1013f, timestampEpochMs = now - 5000)
        ringBuffer.record(speedKmh = 72.0f, gForce = 1.2f, gyroAngularVelocityDegS = 10.0f, cabinPressureHpa = 1013f, timestampEpochMs = now - 2000)
        ringBuffer.record(speedKmh = 10.0f, gForce = 1.0f, gyroAngularVelocityDegS = 2.0f, cabinPressureHpa = 1013f, timestampEpochMs = now)

        val maxSpeed = ringBuffer.getMaxSpeedInWindow(10_000L)
        assertEquals(72.0f, maxSpeed, 0.1f)
    }

    @Test
    fun testRapidPressureJumpDetection() {
        val ringBuffer = SensorRingBuffer(capacity = 100)
        val now = System.currentTimeMillis()

        // Normal cabin pressure
        ringBuffer.record(speedKmh = 60f, gForce = 1.0f, gyroAngularVelocityDegS = 0f, cabinPressureHpa = 1013.2f, timestampEpochMs = now - 100)
        // Airbag explosive blast increases cabin pressure by 3.8 hPa
        ringBuffer.record(speedKmh = 60f, gForce = 6.0f, gyroAngularVelocityDegS = 120f, cabinPressureHpa = 1017.0f, timestampEpochMs = now - 20)

        val deltaP = ringBuffer.getRapidPressureJumpHpa(150L)
        assertEquals(3.8f, deltaP, 0.1f)
    }

    @Test
    fun testStillnessDetection() {
        val ringBuffer = SensorRingBuffer(capacity = 100)
        val now = System.currentTimeMillis()

        // 30 consecutive samples with low variance (motionless phone resting at 1.0G)
        for (i in 0 until 30) {
            ringBuffer.record(
                speedKmh = 0.0f,
                gForce = 1.0f + (i % 2) * 0.02f,
                gyroAngularVelocityDegS = 0.0f,
                cabinPressureHpa = 1013.0f,
                timestampEpochMs = now + i * 50
            )
        }

        assertTrue(ringBuffer.isMotionless(windowMs = 5000L, maxAllowedVarianceG = 0.1f))
    }
}

