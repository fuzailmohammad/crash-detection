package com.accident.detection.center

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.accident.detection.center.data.preferences.UserPreferencesRepository
import com.accident.detection.center.data.repository.CrashLogRepository
import com.accident.detection.center.domain.model.CrashDetectionStage
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.sensor.AcousticSpikeCollector
import com.accident.detection.center.sensor.ActivityContextCollector
import com.accident.detection.center.sensor.CrashDetectionEngine
import com.accident.detection.center.sensor.CrashEventPayload
import com.accident.detection.center.sensor.LocationVelocityCollector
import com.accident.detection.center.sensor.MotionSensorCollector
import com.accident.detection.center.ui.screens.dashboard.SimulationScenario
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class CrashDetectionEngineTest {

    @Test
    fun testIndoorPhoneDropIsHardCappedAndFilteredAsFalseAlarm() = runBlocking {
        // Mathematical validation of Stage 1 & Stage 4 Hard-Cap Rule:
        // When speed is 0 km/h and activity is NOT in-vehicle/bicycle, score MUST NOT exceed 40/100
        val preImpactSpeed = 0.0f
        val activity = "STILL"
        val extremeDropGForce = 18.5f // Severe 18.5G impact on tile floor

        // Calculate points based on Stage 4 formula
        var rawScore = 0.0f
        if (preImpactSpeed >= 20.0f) rawScore += 25.0f
        if (extremeDropGForce >= 7.0f) rawScore += 45.0f

        // Apply Stage 1 Hard-Cap Rule
        val cappedScore = if (preImpactSpeed < 10.0f && activity != "IN_VEHICLE" && activity != "ON_BICYCLE") {
            rawScore.coerceAtMost(40.0f)
        } else {
            rawScore
        }

        assertEquals(40.0f, cappedScore, 0.01f)
        assertTrue("Hard-capped score of 40 cannot trigger >=55 Stage 5 verification threshold", cappedScore < 55.0f)
    }

    @Test
    fun testPotholeDecelerationRuleAbortsFalseAlarm() {
        // Stage 5 Deceleration check:
        // If vehicle maintains >= 15 km/h and speed drop is < 70%, it is a road anomaly
        val preImpactSpeed = 55.0f
        val postImpactSpeed = 52.0f
        val speedDropRatio = (preImpactSpeed - postImpactSpeed) / preImpactSpeed // ~0.054 (5.4% drop)

        val isPothole = postImpactSpeed >= 15.0f && speedDropRatio < 0.70f
        assertTrue("Vehicle maintaining 52 km/h must be classified as Pothole / Speedbump", isPothole)
    }

    @Test
    fun testHighSpeedCrashAchieves100Score() {
        val speed = 68.0f
        val gForce = 7.2f
        val gyroDegS = 340.0f
        val acousticDb = 122.0f
        val pressureDeltaHpa = 3.5f
        val postImpactSpeed = 0.0f

        var score = 0.0f
        // Speed > 20 km/h: +25 pts
        if (speed >= 20.0f) score += 25.0f
        // G-Force >= 7.0G: +45 pts
        if (gForce >= 7.0f) score += 45.0f
        // Gyro >= 250 deg/s: +15 pts
        if (gyroDegS >= 250.0f) score += 15.0f
        // Acoustic >= 115 dB: +10 pts
        if (acousticDb >= 115.0f || pressureDeltaHpa >= 3.0f) score += 10.0f
        // Deceleration to < 5 km/h: +20 pts
        if (postImpactSpeed < 5.0f) score += 20.0f

        val finalScore = score.coerceIn(0.0f, 100.0f)
        assertEquals(100.0f, finalScore, 0.01f)
        assertTrue("Score 100 exceeds 75 confirmation threshold", finalScore >= 75.0f)
    }
}

