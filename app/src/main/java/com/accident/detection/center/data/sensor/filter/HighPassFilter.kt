package com.accident.detection.center.data.sensor.filter

import kotlin.math.sqrt

data class HighPassResult(
    val linearX: Float,
    val linearY: Float,
    val linearZ: Float,
    val linearSvmG: Float,
    val jerkGPerSec: Float
)

/**
 * High-Pass Filter and Jerk (rate of change of acceleration) calculator.
 * Eliminates static gravity to extract high-frequency collision impact dynamics.
 */
class HighPassFilter(
    private val lowPassFilter: LowPassFilter = LowPassFilter(alpha = 0.85f)
) {
    private var prevLinearX: Float = 0.0f
    private var prevLinearY: Float = 0.0f
    private var prevLinearZ: Float = 0.0f
    private var prevTimestampNs: Long = 0L

    companion object {
        const val STANDARD_GRAVITY_M_S2 = 9.80665f
    }

    fun filter(rawX: Float, rawY: Float, rawZ: Float, timestampNs: Long): HighPassResult {
        // 1. Extract gravity via LPF
        val gravity = lowPassFilter.filter(rawX, rawY, rawZ)

        // 2. Subtract gravity to obtain dynamic linear acceleration (in m/s²)
        val linearXMps2 = rawX - gravity[0]
        val linearYMps2 = rawY - gravity[1]
        val linearZMps2 = rawZ - gravity[2]

        // 3. Convert to G-Force units
        val linearXG = linearXMps2 / STANDARD_GRAVITY_M_S2
        val linearYG = linearYMps2 / STANDARD_GRAVITY_M_S2
        val linearZG = linearZMps2 / STANDARD_GRAVITY_M_S2

        val linearSvmG = sqrt(linearXG * linearXG + linearYG * linearYG + linearZG * linearZG)

        // 4. Calculate Jerk: d(acceleration) / dt
        var jerkGPerSec = 0.0f
        if (prevTimestampNs > 0L && timestampNs > prevTimestampNs) {
            val deltaSec = (timestampNs - prevTimestampNs) / 1_000_000_000.0f
            if (deltaSec in 0.001f..0.2f) {
                val deltaX = linearXG - prevLinearX
                val deltaY = linearYG - prevLinearY
                val deltaZ = linearZG - prevLinearZ
                val deltaMagnitude = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
                jerkGPerSec = deltaMagnitude / deltaSec
            }
        }

        prevLinearX = linearXG
        prevLinearY = linearYG
        prevLinearZ = linearZG
        prevTimestampNs = timestampNs

        return HighPassResult(
            linearX = linearXG,
            linearY = linearYG,
            linearZ = linearZG,
            linearSvmG = linearSvmG,
            jerkGPerSec = jerkGPerSec
        )
    }

    fun reset() {
        lowPassFilter.reset()
        prevLinearX = 0.0f
        prevLinearY = 0.0f
        prevLinearZ = 0.0f
        prevTimestampNs = 0L
    }
}

