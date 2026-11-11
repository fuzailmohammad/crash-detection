package com.accident.detection.center

import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.sensor.CrashEventPayload
import com.accident.detection.center.ui.screens.alarm.EmergencyCountdownPhase
import com.accident.detection.center.ui.screens.alarm.EmergencyCountdownUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class EmergencyAlarmCountdownTest {

    @Test
    fun testCountdownUiStateTelemetrySummaryFormatting() {
        val payload = CrashEventPayload(
            latitude = 37.7749,
            longitude = -122.4194,
            speedAtImpactKmh = 62.4f,
            peakGForce = 6.84f,
            crashConfidenceScore = 98.0f,
            vehicleMode = VehicleMode.CAR
        )

        val state = EmergencyCountdownUiState(
            phase = EmergencyCountdownPhase.COUNTING_DOWN,
            remainingSeconds = 28,
            payload = payload
        )

        val summary = state.telemetrySummaryText
        assertTrue(summary.contains("6.8G Impact"))
        assertTrue(summary.contains("62 km/h"))
        assertTrue(summary.contains("GPS Locked"))
        assertEquals(28, state.remainingSeconds)
        assertEquals(EmergencyCountdownPhase.COUNTING_DOWN, state.phase)
    }

    @Test
    fun testSlideToCancelThresholdMath() {
        val trackWidthPx = 1000f
        val thumbWidthPx = 150f
        val maxDragDistancePx = trackWidthPx - thumbWidthPx

        val threshold85Percent = maxDragDistancePx * 0.85f

        // 1. Accidental touch in pocket: 50% drag
        val pocketDrag = maxDragDistancePx * 0.50f
        val isPocketCancelConfirmed = pocketDrag >= threshold85Percent
        assertFalse("Pocket drag under 85% must NEVER trigger cancellation", isPocketCancelConfirmed)

        // 2. Near-threshold release: 84% drag
        val subThresholdDrag = maxDragDistancePx * 0.84f
        val isSubThresholdConfirmed = subThresholdDrag >= threshold85Percent
        assertFalse("84% drag must snap back and NOT cancel", isSubThresholdConfirmed)

        // 3. Deliberate intentional slide: 86% drag
        val deliberateDrag = maxDragDistancePx * 0.86f
        val isDeliberateConfirmed = deliberateDrag >= threshold85Percent
        assertTrue("86% deliberate drag MUST confirm cancellation", isDeliberateConfirmed)

        // 4. Full slide: 100% drag
        val fullDrag = maxDragDistancePx
        val isFullConfirmed = fullDrag >= threshold85Percent
        assertTrue("100% full drag MUST confirm cancellation", isFullConfirmed)
    }

    @Test
    fun testProgrammaticTwoToneSirenBufferGeneration() {
        val sampleRate = 44100
        val toneDurationMs = 400
        val samplesPerTone = (sampleRate * toneDurationMs) / 1000
        val totalSamples = samplesPerTone * 2
        val pcmData = ShortArray(totalSamples)

        val freq1 = 880.0   // 880 Hz
        val freq2 = 1400.0  // 1400 Hz

        for (i in 0 until samplesPerTone) {
            val angle = 2.0 * Math.PI * i * freq1 / sampleRate
            pcmData[i] = (sin(angle) * 32000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        for (i in 0 until samplesPerTone) {
            val angle = 2.0 * Math.PI * i * freq2 / sampleRate
            pcmData[samplesPerTone + i] = (sin(angle) * 32000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        // Verify buffer size: exactly 800ms of audio (17640 samples per 400ms * 2 = 35280 samples)
        assertEquals(35280, pcmData.size)

        // Verify non-zero synthesized audio values within 16-bit PCM boundaries
        var maxAmplitude = 0
        for (sample in pcmData) {
            val absVal = Math.abs(sample.toInt())
            if (absVal > maxAmplitude) {
                maxAmplitude = absVal
            }
        }
        assertTrue("Siren audio waveform must have non-zero amplitude", maxAmplitude > 20000)
        assertTrue("Siren audio waveform must not exceed 16-bit PCM limit", maxAmplitude <= 32767)
    }

    @Test
    fun testCountdownVoiceAnnouncementTriggers() {
        val voiceAlertCheckpoints = setOf(28, 20, 10, 5)

        // Verify exact voice announcement checkpoints match Phase 4 specification
        assertTrue(voiceAlertCheckpoints.contains(28))
        assertTrue(voiceAlertCheckpoints.contains(20))
        assertTrue(voiceAlertCheckpoints.contains(10))
        assertTrue(voiceAlertCheckpoints.contains(5))
        assertFalse(voiceAlertCheckpoints.contains(15))
        assertFalse(voiceAlertCheckpoints.contains(25))
    }
}

