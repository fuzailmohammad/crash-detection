package com.accident.detection.center.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * High-urgency hardware controller governing the 30-second crash alarm cascade.
 *
 * Capabilities:
 * 1. Two-tone emergency siren (880 Hz / 1400 Hz alternating every 400ms) synthesized programmatically
 *    in PCM 16-bit without relying on external audio assets, playing via AudioTrack on STREAM_ALARM.
 * 2. Silent/DND bypass: Automatically maximizes STREAM_ALARM and acquires AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE.
 * 3. 2 Hz rear camera LED SOS strobe using CameraManager.setTorchMode().
 * 4. Repeating heavy haptic vibration pulse (600ms ON / 400ms OFF) with USAGE_ALARM attributes.
 * 5. TextToSpeech spoken countdown alerts (at 28s, 20s, 10s, 5s) with automated 25% siren audio ducking.
 */
@Singleton
class EmergencyHardwareController @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private val tag = "EmergencyHardwareCtrl"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Audio & Siren State
    private var audioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var originalAlarmVolume: Int = -1
    private var isSirenRunning = false

    // Camera Torch Strobe State
    private var torchJob: Job? = null
    private var rearCameraId: String? = null
    private var isTorchRunning = false

    // TTS State
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        initializeTextToSpeech()
        detectRearCameraWithFlash()
    }

    private fun initializeTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to instantiate TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                val result = tts.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.language = Locale.getDefault()
                }
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // Duck siren volume to 25% during speech
                        duckSirenVolume(ducked = true)
                    }

                    override fun onDone(utteranceId: String?) {
                        // Restore siren volume to 100% after speech
                        duckSirenVolume(ducked = false)
                    }

                    @Deprecated("Deprecated in Java", ReplaceWith("duckSirenVolume(ducked = false)"))
                    override fun onError(utteranceId: String?) {
                        duckSirenVolume(ducked = false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        duckSirenVolume(ducked = false)
                    }
                })
                isTtsInitialized = true
                Log.d(tag, "TextToSpeech successfully initialized")
            }
        } else {
            Log.w(tag, "TextToSpeech initialization failed with status $status")
        }
    }

    private fun detectRearCameraWithFlash() {
        val cm = cameraManager ?: return
        try {
            rearCameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            Log.w(tag, "Unable to inspect camera flash characteristics: ${e.message}")
            rearCameraId = null
        }
    }

    /**
     * Activates full emergency hardware cascade:
     * 1. Silent/DND bypass on STREAM_ALARM.
     * 2. Programmatic two-tone siren.
     * 3. 2 Hz camera strobe.
     * 4. Heavy haptic vibration waveform.
     */
    @Synchronized
    fun startEmergencyHardware() {
        startSiren()
        startStrobe()
        startHapticPulse()
    }

    /**
     * Synthesizes and loops a high-decibel two-tone emergency siren (880 Hz / 1400 Hz)
     * alternating every 400ms using programmatic PCM AudioTrack on STREAM_ALARM.
     */
    @Synchronized
    fun startSiren() {
        if (isSirenRunning) return
        isSirenRunning = true

        try {
            // 1. Save original volume and force maximum alarm volume
            originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // 2. Request Exclusive AudioFocus
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(tag, "Audio focus changed: $focusChange")
                }
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)

            // 3. Generate 880 Hz & 1400 Hz alternating PCM buffer (800ms total period)
            val sampleRate = 44100
            val toneDurationMs = 400
            val samplesPerTone = (sampleRate * toneDurationMs) / 1000
            val totalSamples = samplesPerTone * 2
            val pcmData = ShortArray(totalSamples)

            val freq1 = 880.0   // 880 Hz (A5 tone)
            val freq2 = 1400.0  // 1400 Hz (F6 piercing tone)

            for (i in 0 until samplesPerTone) {
                val angle = 2.0 * Math.PI * i * freq1 / sampleRate
                pcmData[i] = (sin(angle) * 32000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            for (i in 0 until samplesPerTone) {
                val angle = 2.0 * Math.PI * i * freq2 / sampleRate
                pcmData[samplesPerTone + i] = (sin(angle) * 32000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            // 4. Initialize Static AudioTrack with continuous loop points
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSizeBytes = maxOf(minBufferSize, totalSamples * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcmData, 0, pcmData.size)
            track.setLoopPoints(0, totalSamples, -1) // Loop continuously
            track.play()
            audioTrack = track
            Log.d(tag, "Two-tone programmatic siren started at max alarm volume")
        } catch (e: Exception) {
            Log.e(tag, "Error starting AudioTrack siren: ${e.message}", e)
        }
    }

    /**
     * Ducks or restores the AudioTrack siren volume (25% for voice announcements).
     */
    fun duckSirenVolume(ducked: Boolean) {
        try {
            audioTrack?.let { track ->
                val targetVolume = if (ducked) 0.25f else 1.0f
                track.setVolume(targetVolume)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to duck/restore siren volume: ${e.message}")
        }
    }

    /**
     * Speaks the countdown warning alert over STREAM_ALARM.
     */
    fun announceWarning(remainingSeconds: Int) {
        val tts = textToSpeech ?: return
        if (!isTtsInitialized) return

        val speechText = "Crash detected. Calling emergency contacts in $remainingSeconds seconds. Slide across the screen to cancel if you are safe."
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }

        try {
            duckSirenVolume(ducked = true)
            tts.speak(
                speechText,
                TextToSpeech.QUEUE_FLUSH,
                params,
                "ALARM_COUNTDOWN_$remainingSeconds"
            )
        } catch (e: Exception) {
            Log.e(tag, "TTS speak failed: ${e.message}")
            duckSirenVolume(ducked = false)
        }
    }

    /**
     * Activates 2 Hz camera flash LED strobe.
     */
    @Synchronized
    fun startStrobe() {
        val cm = cameraManager ?: return
        val camId = rearCameraId ?: return
        if (isTorchRunning) return
        isTorchRunning = true

        torchJob?.cancel()
        torchJob = scope.launch {
            var state = false
            while (isActive && isTorchRunning) {
                try {
                    state = !state
                    cm.setTorchMode(camId, state)
                } catch (e: Exception) {
                    Log.w(tag, "Torch strobe error: ${e.message}")
                    break
                }
                delay(250) // 2 Hz strobe: 250ms ON, 250ms OFF
            }
        }
        Log.d(tag, "Camera LED strobe activated at 2 Hz")
    }

    /**
     * Activates repeating heavy haptic vibration pulse (600ms ON, 400ms OFF).
     */
    @Synchronized
    fun startHapticPulse() {
        try {
            val timings = longArrayOf(0, 600, 400)
            val amplitudes = intArrayOf(0, 255, 0)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val vibrationAttrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                vibrator.vibrate(effect, vibrationAttrs)
            } else {
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, audioAttrs)
            }
            Log.d(tag, "Repeating heavy haptic vibration started")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start vibrator: ${e.message}")
        }
    }

    /**
     * Stops the siren, strobe, vibration, abandons audio focus, and restores original volume.
     */
    @Synchronized
    fun stopAllAlarms() {
        stopSiren()
        stopStrobe()
        stopHapticPulse()
        stopTts()
    }

    @Synchronized
    fun stopSiren() {
        isSirenRunning = false
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
            audioTrack = null

            audioFocusRequest?.let { focus ->
                audioManager.abandonAudioFocusRequest(focus)
            }
            audioFocusRequest = null

            // Restore user's original alarm volume
            if (originalAlarmVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
                originalAlarmVolume = -1
            }
            Log.d(tag, "Siren stopped, AudioTrack released, and alarm volume restored")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping siren: ${e.message}")
        }
    }

    @Synchronized
    fun stopStrobe() {
        isTorchRunning = false
        torchJob?.cancel()
        torchJob = null
        val cm = cameraManager ?: return
        val camId = rearCameraId ?: return
        try {
            cm.setTorchMode(camId, false)
        } catch (e: Exception) {
            // Ignore camera release errors
        }
    }

    @Synchronized
    fun stopHapticPulse() {
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            Log.w(tag, "Error cancelling vibrator: ${e.message}")
        }
    }

    private fun stopTts() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun release() {
        stopAllAlarms()
        try {
            textToSpeech?.shutdown()
            textToSpeech = null
            isTtsInitialized = false
        } catch (e: Exception) {
            // Ignore
        }
    }
}
