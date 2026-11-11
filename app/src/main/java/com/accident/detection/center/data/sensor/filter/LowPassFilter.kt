package com.accident.detection.center.data.sensor.filter

/**
 * Exponential Moving Average (EMA) Low-Pass Filter.
 * Used to isolate the steady-state Earth gravity vector (1G ≈ 9.80665 m/s²)
 * and device orientation from high-frequency dynamic accelerations.
 */
class LowPassFilter(
    private val alpha: Float = 0.85f // Smoothing factor between 0.0 and 1.0
) {
    private var gravityX: Float = 0.0f
    private var gravityY: Float = 0.0f
    private var gravityZ: Float = 9.80665f
    private var isInitialized: Boolean = false

    fun filter(x: Float, y: Float, z: Float): FloatArray {
        if (!isInitialized) {
            gravityX = x
            gravityY = y
            gravityZ = z
            isInitialized = true
            return floatArrayOf(gravityX, gravityY, gravityZ)
        }

        // EMA: g_t = alpha * g_{t-1} + (1 - alpha) * a_t
        gravityX = alpha * gravityX + (1.0f - alpha) * x
        gravityY = alpha * gravityY + (1.0f - alpha) * y
        gravityZ = alpha * gravityZ + (1.0f - alpha) * z

        return floatArrayOf(gravityX, gravityY, gravityZ)
    }

    val gravity: FloatArray
        get() = floatArrayOf(gravityX, gravityY, gravityZ)

    fun reset() {
        isInitialized = false
        gravityX = 0.0f
        gravityY = 0.0f
        gravityZ = 9.80665f
    }
}

