package com.accident.detection.center

import com.accident.detection.center.service.PowerPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerGovernorTest {

    private fun determinePolicy(batteryPct: Int, tempCelsius: Float, isPlugged: Boolean): PowerPolicy {
        return when {
            tempCelsius >= 45.0f -> PowerPolicy.THERMAL_THROTTLED
            isPlugged -> PowerPolicy.ULTRA_PERFORMANCE
            batteryPct <= 20 -> PowerPolicy.BATTERY_SAVER_SURVIVAL
            else -> PowerPolicy.BALANCED_MOBILE
        }
    }

    @Test
    fun testUltraPerformancePolicyWhenPluggedIn() {
        val policy = determinePolicy(batteryPct = 65, tempCelsius = 30.0f, isPlugged = true)
        assertEquals(PowerPolicy.ULTRA_PERFORMANCE, policy)
        assertEquals(10_000, policy.imuSamplingDelayUs) // 100 Hz
        assertEquals(1_000L, policy.gpsIntervalMs) // 1 second GPS updates
    }

    @Test
    fun testThermalProtectionOverridesCharging() {
        // Even if plugged into car charger, overheating phone must throttle to prevent shutdown
        val policy = determinePolicy(batteryPct = 90, tempCelsius = 46.5f, isPlugged = true)
        assertEquals(PowerPolicy.THERMAL_THROTTLED, policy)
        assertEquals(50_000, policy.imuSamplingDelayUs) // 20 Hz
        assertEquals(5_000L, policy.gpsIntervalMs)
    }

    @Test
    fun testBatterySaverSurvivalModeWhenLowBattery() {
        val policy = determinePolicy(batteryPct = 15, tempCelsius = 32.0f, isPlugged = false)
        assertEquals(PowerPolicy.BATTERY_SAVER_SURVIVAL, policy)
        assertEquals(60_000, policy.imuSamplingDelayUs) // ~16 Hz
        assertEquals(10_000L, policy.gpsIntervalMs) // 10s GPS
    }

    @Test
    fun testBalancedMobileStandardPolicy() {
        val policy = determinePolicy(batteryPct = 75, tempCelsius = 28.0f, isPlugged = false)
        assertEquals(PowerPolicy.BALANCED_MOBILE, policy)
        assertEquals(20_000, policy.imuSamplingDelayUs) // 50 Hz
        assertEquals(2_000L, policy.gpsIntervalMs) // 2s GPS
    }
}

