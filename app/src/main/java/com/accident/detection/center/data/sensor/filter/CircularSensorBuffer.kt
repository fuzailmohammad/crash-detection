package com.accident.detection.center.data.sensor.filter

data class ImuSample(
    val timestampEpochMs: Long,
    val rawX: Float,
    val rawY: Float,
    val rawZ: Float,
    val linearSvmG: Float,
    val totalSvmG: Float,
    val gyroRotationalRateRadS: Float,
    val pressureHpa: Float
)

/**
 * Thread-safe fixed-capacity Circular Ring Buffer.
 * Continuously records 5 seconds of 100 Hz IMU telemetry (500 samples)
 * to provide pre-impact waveforms and forensic snapshots upon crash confirmation.
 */
class CircularSensorBuffer(
    val capacity: Int = 500 // 5 seconds at 100 Hz
) {
    private val buffer = arrayOfNulls<ImuSample>(capacity)
    private var writeIndex = 0
    private var size = 0
    private val lock = Any()

    fun add(sample: ImuSample) {
        synchronized(lock) {
            buffer[writeIndex] = sample
            writeIndex = (writeIndex + 1) % capacity
            if (size < capacity) {
                size++
            }
        }
    }

    fun getSnapshot(): List<ImuSample> {
        synchronized(lock) {
            if (size == 0) return emptyList()
            val result = ArrayList<ImuSample>(size)
            val startIndex = if (size < capacity) 0 else writeIndex
            for (i in 0 until size) {
                val index = (startIndex + i) % capacity
                buffer[index]?.let { result.add(it) }
            }
            return result
        }
    }

    /**
     * Calculates the peak G-force experienced across the buffered window.
     */
    fun getPeakGForce(): Float {
        synchronized(lock) {
            var peak = 0.0f
            for (i in 0 until size) {
                buffer[i]?.let {
                    if (it.totalSvmG > peak) peak = it.totalSvmG
                }
            }
            return peak
        }
    }

    /**
     * Evaluates whether the device has stabilized into relative stillness
     * over the last [sampleCount] samples.
     */
    fun isDeviceStill(sampleCount: Int = 100, maxVarianceG: Float = 0.35f): Boolean {
        synchronized(lock) {
            val count = sampleCount.coerceAtMost(size)
            if (count < 10) return false

            var sum = 0.0f
            val samples = ArrayList<Float>(count)

            // Read the latest [count] samples in reverse
            var idx = (writeIndex - 1 + capacity) % capacity
            for (i in 0 until count) {
                val s = buffer[idx]
                if (s != null) {
                    samples.add(s.totalSvmG)
                    sum += s.totalSvmG
                }
                idx = (idx - 1 + capacity) % capacity
            }

            if (samples.isEmpty()) return false
            val mean = sum / samples.size

            var varianceSum = 0.0f
            for (g in samples) {
                val diff = g - mean
                varianceSum += diff * diff
            }
            val variance = varianceSum / samples.size

            // Device is considered still if acceleration variance is very low and mean is near 1G (gravity)
            return variance <= maxVarianceG && (mean in 0.7f..1.3f)
        }
    }

    fun clear() {
        synchronized(lock) {
            for (i in buffer.indices) {
                buffer[i] = null
            }
            writeIndex = 0
            size = 0
        }
    }
}

