package com.accident.detection.center.data.sensor.manager

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * High-performance ambient acoustic decibel sampler.
 * Computes live sound pressure level (dB SPL) to detect structural crash deformation
 * and explosive airbag pop sounds (~95–130 dB).
 *
 * Privacy guarantee: Audio is NEVER stored, recorded to file, or transmitted over network.
 * Buffer is analyzed in volatile memory for RMS amplitude and immediately cleared.
 */
@Singleton
class AcousticNoiseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _soundLevelDbFlow = MutableStateFlow(40.0f) // Ambient quiet baseline
    val soundLevelDbFlow: StateFlow<Float> = _soundLevelDbFlow.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var samplingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val SAMPLE_RATE_HZ = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    fun startSampling() {
        if (samplingJob?.isActive == true || !hasAudioPermission()) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        if (minBufferSize <= 0) return

        val bufferSize = max(minBufferSize, 2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()

            samplingJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)
                while (isActive) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readCount > 0) {
                        var sumSquare = 0.0
                        for (i in 0 until readCount) {
                            val sample = buffer[i].toDouble()
                            sumSquare += sample * sample
                        }
                        val rms = sqrt(sumSquare / readCount)
                        // Standard conversion to calibrated Sound Pressure Level (SPL) dB
                        val db = if (rms > 0.0) {
                            (20.0 * log10(rms / 32767.0) + 94.0).toFloat().coerceIn(30.0f, 130.0f)
                        } else {
                            30.0f
                        }
                        _soundLevelDbFlow.value = db
                    }
                }
            }
        } catch (e: Exception) {
            stopSampling()
        }
    }

    fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignored
        } finally {
            audioRecord = null
        }
    }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}

