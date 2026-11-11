package com.accident.detection.center.sensor

/**
 * Single temporal sample capturing multi-sensor snapshot.
 */
data class TelemetrySample(
    val timestampEpochMs: Long,
    val speedKmh: Float,
    val gForce: Float,
    val gyroAngularVelocityDegS: Float,
    val cabinPressureHpa: Float,
    val soundLevelDb: Float = 0.0f
)

/**
 * Thread-safe fixed-capacity Circular Buffer.
 * Stores up to 15 seconds of continuous telemetry (at 100 Hz = 1,500 samples)
 * for pre-impact and post-impact forensic analysis.
 */
class SensorRingBuffer(
    val capacity: Int = 1500
) {
    private val buffer = arrayOfNulls<TelemetrySample>(capacity)
    private var writeHead = 0
    private var count = 0
    private val lock = Any()

    fun record(
        speedKmh: Float,
        gForce: Float,
        gyroAngularVelocityDegS: Float,
        cabinPressureHpa: Float,
        soundLevelDb: Float = 0.0f,
        timestampEpochMs: Long = System.currentTimeMillis()
    ) {
        val sample = TelemetrySample(
            timestampEpochMs = timestampEpochMs,
            speedKmh = speedKmh,
            gForce = gForce,
            gyroAngularVelocityDegS = gyroAngularVelocityDegS,
            cabinPressureHpa = cabinPressureHpa,
            soundLevelDb = soundLevelDb
        )
        synchronized(lock) {
            buffer[writeHead] = sample
            writeHead = (writeHead + 1) % capacity
            if (count < capacity) {
                count++
            }
        }
    }

    /**
     * Extracts all samples recorded within the last [durationMs] milliseconds.
     */
    fun getTelemetryInRecentWindow(durationMs: Long = 15_000L): List<TelemetrySample> {
        val cutoff = System.currentTimeMillis() - durationMs
        val result = mutableListOf<TelemetrySample>()
        synchronized(lock) {
            if (count == 0) return emptyList()
            val startIdx = if (count < capacity) 0 else writeHead
            for (i in 0 until count) {
                val idx = (startIdx + i) % capacity
                val sample = buffer[idx]
                if (sample != null && sample.timestampEpochMs >= cutoff) {
                    result.add(sample)
                }
            }
        }
        return result
    }

    /**
     * Finds the maximum GPS speed recorded within the last [windowMs].
     */
    fun getMaxSpeedInWindow(windowMs: Long = 15_000L): Float {
        val samples = getTelemetryInRecentWindow(windowMs)
        return samples.maxOfOrNull { it.speedKmh } ?: 0.0f
    }

    /**
     * Finds the peak resultant G-force in the last [windowMs].
     */
    fun getPeakGForceInWindow(windowMs: Long = 1_000L): Float {
        val samples = getTelemetryInRecentWindow(windowMs)
        return samples.maxOfOrNull { it.gForce } ?: 0.0f
    }

    /**
     * Finds the maximum gyroscope angular velocity (deg/s) in the last [windowMs].
     */
    fun getPeakGyroDegSInWindow(windowMs: Long = 1_000L): Float {
        val samples = getTelemetryInRecentWindow(windowMs)
        return samples.maxOfOrNull { it.gyroAngularVelocityDegS } ?: 0.0f
    }

    /**
     * Evaluates instantaneous barometric pressure differential (ΔP in hPa)
     * across a rapid window (e.g., 150 ms) to identify airbag deployment blast.
     */
    fun getRapidPressureJumpHpa(windowMs: Long = 150L): Float {
        val samples = getTelemetryInRecentWindow(windowMs)
        if (samples.size < 2) return 0.0f
        val minP = samples.minOf { it.cabinPressureHpa }
        val maxP = samples.maxOf { it.cabinPressureHpa }
        return (maxP - minP).coerceAtLeast(0.0f)
    }

    /**
     * Checks if the device has experienced post-impact deceleration
     * and has remained motionless over [windowMs].
     */
    fun isMotionless(windowMs: Long = 4_000L, maxAllowedVarianceG: Float = 0.25f): Boolean {
        val samples = getTelemetryInRecentWindow(windowMs)
        if (samples.size < 20) return false

        val meanG = samples.map { it.gForce }.average().toFloat()
        var varianceSum = 0.0f
        for (s in samples) {
            val diff = s.gForce - meanG
            varianceSum += diff * diff
        }
        val variance = varianceSum / samples.size
        return variance <= maxAllowedVarianceG
    }

    fun clear() {
        synchronized(lock) {
            for (i in buffer.indices) {
                buffer[i] = null
            }
            writeHead = 0
            count = 0
        }
    }
}

