package com.accident.detection.center.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.max

/**
 * Privacy-Centric Acoustic Spike & Airbag Decibel Collector.
 *
 * Samples raw 16-bit PCM audio chunks in volatile memory only to calculate instantaneous
 * peak sound pressure level in Decibels ($dB = 20 \times \log_{10}(\text{amplitude})$).
 *
 * Privacy Guarantees:
 * - NEVER records audio files to disk.
 * - NEVER transmits audio packets over network.
 * - Runs in lightweight 3-second bursts upon candidate shock detection or during active driving.
 */
@Singleton
class AcousticSpikeCollector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _peakDecibels = MutableStateFlow(40.0f) // Ambient quiet baseline (~40 dB)
    val peakDecibels: StateFlow<Float> = _peakDecibels.asStateFlow()

    private val _isRecordingBurst = MutableStateFlow(false)
    val isRecordingBurst: StateFlow<Boolean> = _isRecordingBurst.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var collectorJob: Job? = null
    private var burstTimeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val SAMPLE_RATE_HZ = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val AIRBAG_ACOUSTIC_THRESHOLD_DB = 115.0f
    }

    /**
     * Activates a high-priority 3-second acoustic sampling burst to verify
     * structural metal impact or airbag explosion crunch.
     */
    fun triggerAcousticBurst(durationMs: Long = 3000L) {
        if (!hasMicPermission()) return

        burstTimeoutJob?.cancel()
        if (!_isRecordingBurst.value) {
            startSampling()
        }

        burstTimeoutJob = scope.launch {
            delay(durationMs)
            stopSampling()
        }
    }

    @SuppressLint("MissingPermission")
    fun startSampling() {
        if (_isRecordingBurst.value || !hasMicPermission()) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        if (minBufferSize <= 0) return

        val bufferSize = max(minBufferSize, 2048)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return
            }

            record.startRecording()
            audioRecord = record
            _isRecordingBurst.value = true

            collectorJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)
                while (isActive && _isRecordingBurst.value) {
                    val readCount = record.read(buffer, 0, buffer.size)
                    if (readCount > 0) {
                        var maxPeak = 1
                        for (i in 0 until readCount) {
                            val absVal = kotlin.math.abs(buffer[i].toInt())
                            if (absVal > maxPeak) {
                                maxPeak = absVal
                            }
                        }

                        // Convert peak 16-bit PCM amplitude to Decibels: dB = 20 * log10(amplitude)
                        // With reference calibrated to ~130 dB SPL ceiling
                        val db = (20.0 * log10(maxPeak.toDouble() / 32767.0) + 120.0)
                            .toFloat()
                            .coerceIn(30.0f, 135.0f)

                        _peakDecibels.value = db
                    }
                }
            }
        } catch (e: Exception) {
            stopSampling()
        }
    }

    fun stopSampling() {
        burstTimeoutJob?.cancel()
        collectorJob?.cancel()
        _isRecordingBurst.value = false

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignored
        } finally {
            audioRecord = null
            _peakDecibels.value = 40.0f
        }
    }

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}

