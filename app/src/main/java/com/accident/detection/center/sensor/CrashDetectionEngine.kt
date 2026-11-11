package com.accident.detection.center.sensor

import android.app.KeyguardManager
import android.content.Context
import com.accident.detection.center.data.local.entity.CrashEventLogEntity
import com.accident.detection.center.data.preferences.UserPreferencesRepository
import com.accident.detection.center.data.repository.CrashLogRepository
import com.accident.detection.center.domain.model.CrashDetectionStage
import com.accident.detection.center.domain.model.SensorTelemetryState
import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.ui.screens.dashboard.SimulationScenario
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class CrashDetectionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val motionCollector: MotionSensorCollector,
    private val locationCollector: LocationVelocityCollector,
    private val activityCollector: ActivityContextCollector,
    private val acousticCollector: AcousticSpikeCollector,
    private val preferencesRepository: UserPreferencesRepository,
    private val crashLogRepository: CrashLogRepository
) {
    private val ringBuffer = SensorRingBuffer(capacity = 1500) // 15s at 100 Hz
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    private val _telemetryState = MutableStateFlow(SensorTelemetryState())
    val telemetryState: StateFlow<SensorTelemetryState> = _telemetryState.asStateFlow()

    private val _confirmedCrashEvents = MutableSharedFlow<CrashEventPayload>(extraBufferCapacity = 4)
    val confirmedCrashEvents: SharedFlow<CrashEventPayload> = _confirmedCrashEvents.asSharedFlow()

    private val _pipelineStage = MutableStateFlow(CrashDetectionStage.IDLE)
    val pipelineStage: StateFlow<CrashDetectionStage> = _pipelineStage.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.Default)

    private var motionJob: Job? = null
    private var locationJob: Job? = null
    private var activityJob: Job? = null
    private var stepJob: Job? = null
    private var verificationJob: Job? = null
    private var isEngineRunning = false

    // State machine memory
    private var activeVehicleMode: VehicleMode = VehicleMode.CAR
    private var consecutiveImpactFrames = 0
    private var postImpactStepsCounter = 0
    private var isSimulating = false

    fun startEngine() {
        if (isEngineRunning) return
        isEngineRunning = true
        consecutiveImpactFrames = 0
        postImpactStepsCounter = 0
        ringBuffer.clear()

        // 1. Observe User Preferences (Vehicle Mode & Shield)
        engineScope.launch {
            preferencesRepository.userProfileFlow.collectLatest { profile ->
                activeVehicleMode = profile.selectedVehicleMode
                if (!profile.isProtectionShieldActive) {
                    stopEngine()
                }
            }
        }

        // 2. Start Motion Sensor Collector
        motionJob = engineScope.launch {
            motionCollector.motionReadingsFlow(SensorSamplingMode.ActiveDrivingMode).collect { reading ->
                processMotionReading(reading)
            }
        }

        // 3. Start Location Velocity Collector
        locationJob = engineScope.launch {
            locationCollector.locationFlow(highAccuracy = true).collect { loc ->
                processLocationUpdate(loc)
            }
        }

        // 4. Start Activity Context Collector
        activityJob = engineScope.launch {
            activityCollector.activityFlow().collect { act ->
                processActivityUpdate(act)
            }
        }

        // 5. Track Step Detector Events
        stepJob = engineScope.launch {
            motionCollector.stepDetectorEvents.collect {
                if (_pipelineStage.value == CrashDetectionStage.VERIFYING_POST_IMPACT) {
                    postImpactStepsCounter++
                }
            }
        }
    }

    fun stopEngine() {
        if (!isEngineRunning) return
        isEngineRunning = false
        motionJob?.cancel()
        locationJob?.cancel()
        activityJob?.cancel()
        stepJob?.cancel()
        verificationJob?.cancel()
        acousticCollector.stopSampling()
        _pipelineStage.value = CrashDetectionStage.IDLE
    }

    private fun processMotionReading(reading: MotionSensorReading) {
        if (isSimulating) return

        val currentSpeed = _telemetryState.value.speedKmh
        val currentPressure = reading.cabinPressureHpa
        val currentSoundDb = acousticCollector.peakDecibels.value

        // Record telemetry to 15-second circular ring buffer
        ringBuffer.record(
            speedKmh = currentSpeed,
            gForce = reading.filteredLinearSvmG,
            gyroAngularVelocityDegS = reading.gyroResultantDegS,
            cabinPressureHpa = currentPressure,
            soundLevelDb = currentSoundDb
        )

        // Update live telemetry HUD state
        _telemetryState.value = _telemetryState.value.copy(
            accelX = reading.linearAx,
            accelY = reading.linearAy,
            accelZ = reading.linearAz,
            svmGForce = reading.filteredLinearSvmG,
            rotationalRateRadS = reading.gyroResultantDegS / (180.0f / Math.PI.toFloat()),
            barometricPressureHpa = currentPressure,
            soundLevelDb = currentSoundDb,
            lastUpdatedTimestampEpochMs = System.currentTimeMillis()
        )

        // Evaluate 5-Stage Fusion State Machine
        evaluatePipeline(reading)
    }

    private fun processLocationUpdate(loc: GpsVelocitySnapshot) {
        if (isSimulating) return

        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = loc.speedKmh,
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitudeMeters = loc.altitudeMeters,
            gpsAccuracyMeters = loc.horizontalAccuracyMeters
        )

        // Dynamic sampling rate adjustment:
        // Driving (>15 km/h) -> ActiveDrivingMode (100Hz)
        // Parked / Stationary (<5 km/h for 30s) -> StandbyMode
        if (loc.speedKmh >= 15.0f && motionCollector.currentSamplingMode.value != SensorSamplingMode.ActiveDrivingMode) {
            motionCollector.setSamplingMode(SensorSamplingMode.ActiveDrivingMode)
        } else if (loc.speedKmh < 5.0f && ringBuffer.getMaxSpeedInWindow(30_000L) < 5.0f &&
            motionCollector.currentSamplingMode.value != SensorSamplingMode.StandbyMode
        ) {
            motionCollector.setSamplingMode(SensorSamplingMode.StandbyMode)
        }
    }

    private fun processActivityUpdate(act: ActivityContextSnapshot) {
        if (isSimulating) return

        _telemetryState.value = _telemetryState.value.copy(
            activityType = act.mostProbableActivity,
            activityConfidence = act.confidence
        )
    }

    /**
     * Core 5-Stage Multi-Sensor Fusion State Machine.
     */
    private fun evaluatePipeline(reading: MotionSensorReading) {
        // Do not interrupt verification window
        if (_pipelineStage.value == CrashDetectionStage.VERIFYING_POST_IMPACT ||
            _pipelineStage.value == CrashDetectionStage.CRASH_CONFIRMED
        ) {
            return
        }

        val speed = _telemetryState.value.speedKmh
        val activity = _telemetryState.value.activityType
        val maxSpeedInLast15s = ringBuffer.getMaxSpeedInWindow(15_000L)
        val isPreImpactVelocityGated = maxSpeedInLast15s >= activeVehicleMode.minSpeedGateKmh ||
                activity == "IN_VEHICLE" || activity == "ON_BICYCLE"

        // ==========================================
        // STAGE 1: Velocity & Context Pre-Impact Gate
        // ==========================================
        if (!isPreImpactVelocityGated && speed < 10.0f) {
            // Speed is <10 km/h and user is not in vehicle/bike.
            // HARD-CAP: Confidence score cannot exceed 40.
            _pipelineStage.value = CrashDetectionStage.IDLE
            _telemetryState.value = _telemetryState.value.copy(
                currentStage = CrashDetectionStage.IDLE,
                crashConfidenceScore = 0.0f
            )
            consecutiveImpactFrames = 0
            return
        } else {
            _pipelineStage.value = CrashDetectionStage.MOVING_GATE_ACTIVE
        }

        // ==========================================
        // STAGE 2: Impact Vector Gate
        // ==========================================
        val impactThresholdG = activeVehicleMode.impactGThreshold
        if (reading.filteredLinearSvmG >= impactThresholdG) {
            consecutiveImpactFrames++
        } else {
            consecutiveImpactFrames = 0
        }

        // Must be sustained across at least 2 consecutive frames (filters out electrostatic glitch)
        if (consecutiveImpactFrames < 2) {
            return
        }

        _pipelineStage.value = CrashDetectionStage.IMPACT_DETECTED

        // ==========================================
        // STAGE 3: Rotational & Environmental Multiplier
        // ==========================================
        val gyroSpike = reading.gyroResultantDegS >= 250.0f
        val pressureDeltaHpa = ringBuffer.getRapidPressureJumpHpa(150L)
        val pressureSpike = pressureDeltaHpa >= 3.0f

        // Trigger immediate 3-second acoustic burst to capture crash decibels
        acousticCollector.triggerAcousticBurst(3000L)
        val soundDb = acousticCollector.peakDecibels.value
        val acousticSpike = soundDb >= AcousticSpikeCollector.AIRBAG_ACOUSTIC_THRESHOLD_DB

        // ==========================================
        // STAGE 4: Weighted Confidence Score Calculation
        // ==========================================
        var score = calculateConfidenceScore(
            preImpactSpeedKmh = maxSpeedInLast15s,
            activityType = activity,
            gForce = reading.filteredLinearSvmG,
            gyroDegS = reading.gyroResultantDegS,
            acousticDb = soundDb,
            pressureDeltaHpa = pressureDeltaHpa
        )

        // CRITICAL FALSE-ALARM RULE ENFORCEMENT:
        if (maxSpeedInLast15s < 10.0f && activity != "IN_VEHICLE" && activity != "ON_BICYCLE") {
            // Hard-cap at 40/100 for indoor phone drops!
            score = score.coerceAtMost(40.0f)
            _pipelineStage.value = CrashDetectionStage.FALSE_ALARM_ABORTED
            _telemetryState.value = _telemetryState.value.copy(
                currentStage = CrashDetectionStage.FALSE_ALARM_ABORTED,
                crashConfidenceScore = score
            )
            return
        }

        _telemetryState.value = _telemetryState.value.copy(
            currentStage = CrashDetectionStage.IMPACT_DETECTED,
            crashConfidenceScore = score
        )

        // If candidate impact achieves score >= 55, enter Stage 5 Post-Impact Verification
        if (score >= 55.0f) {
            enterStage5VerificationWindow(
                impactG = reading.filteredLinearSvmG,
                gyroDegS = reading.gyroResultantDegS,
                soundDb = soundDb,
                pressureDeltaHpa = pressureDeltaHpa,
                preImpactSpeedKmh = maxSpeedInLast15s,
                currentScore = score
            )
        }
    }

    /**
     * Stage 4 Score Weights:
     * - Pre-impact Velocity > 20 km/h (+25 pts) or IN_VEHICLE (+15 pts)
     * - G-Force > 4.5G (+30 pts) or > 7.0G (+45 pts)
     * - Gyroscope > 250 deg/s (+15 pts)
     * - Acoustic > 115 dB (+10 pts) or Pressure Spike > 3 hPa (+10 pts)
     */
    private fun calculateConfidenceScore(
        preImpactSpeedKmh: Float,
        activityType: String,
        gForce: Float,
        gyroDegS: Float,
        acousticDb: Float,
        pressureDeltaHpa: Float
    ): Float {
        var points = 0.0f

        // Velocity & Context (Max 25 pts)
        if (preImpactSpeedKmh >= 20.0f) {
            points += 25.0f
        } else if (activityType == "IN_VEHICLE" || activityType == "ON_BICYCLE") {
            points += 15.0f
        }

        // G-Force (Max 45 pts)
        if (gForce >= 7.0f) {
            points += 45.0f
        } else if (gForce >= 4.5f) {
            points += 30.0f
        } else if (gForce >= 3.0f) {
            points += 15.0f
        }

        // Gyroscope Rotational Spike (Max 15 pts)
        if (gyroDegS >= 250.0f) {
            points += 15.0f
        }

        // Acoustic & Pressure Shock (Max 10 pts)
        if (acousticDb >= 115.0f || pressureDeltaHpa >= 3.0f) {
            points += 10.0f
        }

        return points.coerceIn(0.0f, 100.0f)
    }

    /**
     * ==========================================
     * STAGE 5: 5-Second Post-Impact Verification Window
     * ==========================================
     */
    private fun enterStage5VerificationWindow(
        impactG: Float,
        gyroDegS: Float,
        soundDb: Float,
        pressureDeltaHpa: Float,
        preImpactSpeedKmh: Float,
        currentScore: Float
    ) {
        verificationJob?.cancel()
        postImpactStepsCounter = 0
        _pipelineStage.value = CrashDetectionStage.VERIFYING_POST_IMPACT
        _telemetryState.value = _telemetryState.value.copy(
            currentStage = CrashDetectionStage.VERIFYING_POST_IMPACT,
            crashConfidenceScore = currentScore
        )

        verificationJob = engineScope.launch {
            val initialSpeed = preImpactSpeedKmh
            delay(5000L) // 5-second post-impact observation window

            val latestSpeed = _telemetryState.value.speedKmh
            val speedDropRatio = if (initialSpeed > 0f) (initialSpeed - latestSpeed) / initialSpeed else 1.0f

            // Verification Rule 1: Vehicle deceleration verification
            // If vehicle continues driving smoothly (speed drop < 70% and speed >= 15 km/h), abort as Pothole/Speedbump!
            if (latestSpeed >= 15.0f && speedDropRatio < 0.70f) {
                abortFalseAlarm(
                    reason = "FALSE_ALARM_POTHOLE_OR_BUMP: Vehicle maintained ${latestSpeed.toInt()} km/h speed post-impact."
                )
                return@launch
            }

            // Verification Rule 2: Pedometer & Human Movement Rejection
            // If user takes > 5 walking steps post-impact, phone drop onto ground occurred!
            if (postImpactStepsCounter > 5) {
                abortFalseAlarm(
                    reason = "FALSE_ALARM_PHONE_DROP: $postImpactStepsCounter walking steps detected post-impact."
                )
                return@launch
            }

            // Add +20 pts for confirmed vehicular halt / rapid stop (< 5 km/h)
            var finalScore = currentScore
            if (latestSpeed < 5.0f) {
                finalScore = (finalScore + 20.0f).coerceAtMost(100.0f)
            }

            // Must score >= 75 after verification to confirm crash
            if (finalScore >= 75.0f) {
                confirmCrash(
                    impactG = impactG,
                    gyroDegS = gyroDegS,
                    soundDb = soundDb,
                    pressureDeltaHpa = pressureDeltaHpa,
                    speedAtImpact = initialSpeed,
                    finalScore = finalScore
                )
            } else {
                abortFalseAlarm(
                    reason = "FALSE_ALARM_INSUFFICIENT_EVIDENCE: Final score $finalScore/100 below 75 threshold."
                )
            }
        }
    }

    private fun abortFalseAlarm(reason: String) {
        _pipelineStage.value = CrashDetectionStage.FALSE_ALARM_ABORTED
        _telemetryState.value = _telemetryState.value.copy(
            currentStage = CrashDetectionStage.FALSE_ALARM_ABORTED,
            crashConfidenceScore = 0.0f
        )
        consecutiveImpactFrames = 0

        engineScope.launch {
            // Log forensic record of false alarm
            val log = CrashEventLogEntity(
                latitude = _telemetryState.value.latitude,
                longitude = _telemetryState.value.longitude,
                speedAtImpactKmh = _telemetryState.value.speedKmh,
                peakGForce = _telemetryState.value.svmGForce,
                gyroRotationalRateRadS = _telemetryState.value.rotationalRateRadS,
                ambientNoiseDb = _telemetryState.value.soundLevelDb,
                barometricPressureSpikeHpa = 0.0f,
                vehicleMode = activeVehicleMode,
                finalDetectionStage = CrashDetectionStage.FALSE_ALARM_ABORTED,
                crashConfidenceScore = 0.0f,
                wasCancelledByUser = false,
                falseAlarmReason = reason
            )
            crashLogRepository.insertLog(log)
            delay(3000L)
            if (_pipelineStage.value == CrashDetectionStage.FALSE_ALARM_ABORTED) {
                _pipelineStage.value = CrashDetectionStage.IDLE
            }
        }
    }

    private fun confirmCrash(
        impactG: Float,
        gyroDegS: Float,
        soundDb: Float,
        pressureDeltaHpa: Float,
        speedAtImpact: Float,
        finalScore: Float
    ) {
        _pipelineStage.value = CrashDetectionStage.CRASH_CONFIRMED
        _telemetryState.value = _telemetryState.value.copy(
            currentStage = CrashDetectionStage.CRASH_CONFIRMED,
            crashConfidenceScore = finalScore
        )

        val payload = CrashEventPayload(
            latitude = _telemetryState.value.latitude,
            longitude = _telemetryState.value.longitude,
            speedAtImpactKmh = speedAtImpact,
            peakGForce = impactG,
            gyroAngularVelocityDegS = gyroDegS,
            ambientNoiseDb = soundDb,
            pressureJumpHpa = pressureDeltaHpa,
            crashConfidenceScore = finalScore,
            vehicleMode = activeVehicleMode,
            preImpactTelemetry = ringBuffer.getTelemetryInRecentWindow(15_000L),
            isSimulation = isSimulating,
            forensicSummary = "Collision verified: Peak ${String.format("%.1f", impactG)}G, Gyro ${gyroDegS.toInt()}°/s, Decibels ${soundDb.toInt()}dB."
        )

        engineScope.launch {
            val log = CrashEventLogEntity(
                latitude = payload.latitude,
                longitude = payload.longitude,
                speedAtImpactKmh = payload.speedAtImpactKmh,
                peakGForce = payload.peakGForce,
                gyroRotationalRateRadS = gyroDegS / (180.0f / Math.PI.toFloat()),
                ambientNoiseDb = payload.ambientNoiseDb,
                barometricPressureSpikeHpa = payload.pressureJumpHpa,
                vehicleMode = payload.vehicleMode,
                finalDetectionStage = CrashDetectionStage.CRASH_CONFIRMED,
                crashConfidenceScore = payload.crashConfidenceScore,
                isSimulation = payload.isSimulation,
                dispatchSummary = payload.forensicSummary
            )
            val logId = crashLogRepository.insertLog(log)
            _confirmedCrashEvents.emit(payload)
        }
    }

    /**
     * ==========================================
     * CRASH SIMULATION INJECTION HOOK
     * ==========================================
     */
    fun simulateScenario(scenario: SimulationScenario) {
        engineScope.launch {
            isSimulating = true
            when (scenario) {
                SimulationScenario.PhoneDropFloor -> runPhoneDropSimulation()
                SimulationScenario.PotholeSpeedbump -> runPotholeSimulation()
                SimulationScenario.SevereSideCollision -> runSevereCrashSimulation()
                SimulationScenario.HighSpeedRollover -> runRolloverSimulation()
            }
            isSimulating = false
        }
    }

    private suspend fun runPhoneDropSimulation() {
        // 1. Initial desk/stationary state (0 km/h, STILL)
        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = 0.0f,
            activityType = "STILL",
            currentStage = CrashDetectionStage.IDLE,
            crashConfidenceScore = 0.0f
        )
        delay(800)

        // 2. High 14.2G shock from hitting tile floor
        _pipelineStage.value = CrashDetectionStage.IMPACT_DETECTED
        _telemetryState.value = _telemetryState.value.copy(
            svmGForce = 14.2f,
            currentStage = CrashDetectionStage.IMPACT_DETECTED,
            crashConfidenceScore = 40.0f // Capped at 40 max due to Stage 1 gate!
        )
        delay(1000)

        // 3. User picks up phone -> 6 walking steps detected
        _pipelineStage.value = CrashDetectionStage.VERIFYING_POST_IMPACT
        _telemetryState.value = _telemetryState.value.copy(
            currentStage = CrashDetectionStage.VERIFYING_POST_IMPACT,
            postImpactStepCount = 6
        )
        delay(1200)

        // 4. Rejected as false alarm
        abortFalseAlarm("FALSE_ALARM_PHONE_DROP: 14G impact with 0 km/h speed and post-impact walking steps.")
    }

    private suspend fun runPotholeSimulation() {
        // 1. Driving on road at 55 km/h
        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = 55.0f,
            activityType = "IN_VEHICLE",
            currentStage = CrashDetectionStage.MOVING_GATE_ACTIVE,
            crashConfidenceScore = 25.0f
        )
        delay(800)

        // 2. Severe 2.8G pothole jolt
        _pipelineStage.value = CrashDetectionStage.IMPACT_DETECTED
        _telemetryState.value = _telemetryState.value.copy(
            svmGForce = 2.8f,
            currentStage = CrashDetectionStage.IMPACT_DETECTED,
            crashConfidenceScore = 45.0f
        )
        delay(1000)

        // 3. Post-impact: vehicle continues cruising at 52 km/h
        _pipelineStage.value = CrashDetectionStage.VERIFYING_POST_IMPACT
        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = 52.0f,
            currentStage = CrashDetectionStage.VERIFYING_POST_IMPACT
        )
        delay(1200)

        // 4. Rejected as false alarm
        abortFalseAlarm("FALSE_ALARM_POTHOLE_OR_BUMP: Speed maintained at 52 km/h post-impact.")
    }

    private suspend fun runSevereCrashSimulation() {
        val lat = 37.7749
        val lon = -122.4194

        // 1. High-speed highway travel (68 km/h)
        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = 68.0f,
            latitude = lat,
            longitude = lon,
            activityType = "IN_VEHICLE",
            currentStage = CrashDetectionStage.MOVING_GATE_ACTIVE,
            crashConfidenceScore = 25.0f
        )
        delay(800)

        // 2. Violent T-Bone: 7.2G + 340 deg/s rotation + 122 dB airbag pop!
        _pipelineStage.value = CrashDetectionStage.IMPACT_DETECTED
        _telemetryState.value = _telemetryState.value.copy(
            svmGForce = 7.2f,
            rotationalRateRadS = 340.0f / (180.0f / Math.PI.toFloat()),
            soundLevelDb = 122.0f,
            currentStage = CrashDetectionStage.IMPACT_DETECTED,
            crashConfidenceScore = 80.0f
        )
        delay(1000)

        // 3. Post-impact vehicle halt: 0 km/h and zero walking steps
        _pipelineStage.value = CrashDetectionStage.VERIFYING_POST_IMPACT
        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = 0.0f,
            postImpactStepCount = 0,
            currentStage = CrashDetectionStage.VERIFYING_POST_IMPACT,
            crashConfidenceScore = 100.0f
        )
        delay(1500)

        // 4. Confirmed Crash!
        confirmCrash(
            impactG = 7.2f,
            gyroDegS = 340.0f,
            soundDb = 122.0f,
            pressureDeltaHpa = 3.5f,
            speedAtImpact = 68.0f,
            finalScore = 100.0f
        )
    }

    private suspend fun runRolloverSimulation() {
        val lat = 37.7749
        val lon = -122.4194

        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = 75.0f,
            latitude = lat,
            longitude = lon,
            activityType = "IN_VEHICLE",
            currentStage = CrashDetectionStage.MOVING_GATE_ACTIVE,
            crashConfidenceScore = 25.0f
        )
        delay(800)

        // 5.8G impact + 420 deg/s rollover spin + 118 dB sound
        _pipelineStage.value = CrashDetectionStage.IMPACT_DETECTED
        _telemetryState.value = _telemetryState.value.copy(
            svmGForce = 5.8f,
            rotationalRateRadS = 420.0f / (180.0f / Math.PI.toFloat()),
            soundLevelDb = 118.0f,
            currentStage = CrashDetectionStage.IMPACT_DETECTED,
            crashConfidenceScore = 85.0f
        )
        delay(1000)

        // 0 km/h stop
        _pipelineStage.value = CrashDetectionStage.VERIFYING_POST_IMPACT
        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = 0.0f,
            postImpactStepCount = 0,
            currentStage = CrashDetectionStage.VERIFYING_POST_IMPACT,
            crashConfidenceScore = 100.0f
        )
        delay(1500)

        confirmCrash(
            impactG = 5.8f,
            gyroDegS = 420.0f,
            soundDb = 118.0f,
            pressureDeltaHpa = 1.8f,
            speedAtImpact = 75.0f,
            finalScore = 100.0f
        )
    }
}

