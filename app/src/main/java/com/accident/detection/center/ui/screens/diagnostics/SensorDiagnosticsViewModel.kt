package com.accident.detection.center.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accident.detection.center.calibration.BatteryDiagnostics
import com.accident.detection.center.calibration.CalibrationOffsets
import com.accident.detection.center.calibration.HardwareSensorSpec
import com.accident.detection.center.calibration.SensorCalibrationManager
import com.accident.detection.center.sensor.CrashDetectionEngine
import com.accident.detection.center.sensor.MotionSensorCollector
import com.accident.detection.center.service.PowerGovernor
import com.accident.detection.center.service.PowerPolicy
import com.accident.detection.center.ui.components.WaveformPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorDiagnosticsViewModel @Inject constructor(
    private val calibrationManager: SensorCalibrationManager,
    private val crashDetectionEngine: CrashDetectionEngine,
    private val motionCollector: MotionSensorCollector,
    private val powerGovernor: PowerGovernor
) : ViewModel() {

    val powerPolicy: StateFlow<PowerPolicy> = powerGovernor.currentPolicy
    val calibrationOffsets: StateFlow<CalibrationOffsets> = calibrationManager.currentOffsets

    private val _waveformPoints = MutableStateFlow<List<WaveformPoint>>(emptyList())
    val waveformPoints: StateFlow<List<WaveformPoint>> = _waveformPoints.asStateFlow()

    private val _calibrationProgress = MutableStateFlow<Int?>(null)
    val calibrationProgress: StateFlow<Int?> = _calibrationProgress.asStateFlow()

    private val _batteryDiagnostics = MutableStateFlow(BatteryDiagnostics())
    val batteryDiagnostics: StateFlow<BatteryDiagnostics> = _batteryDiagnostics.asStateFlow()

    val accelSpec: HardwareSensorSpec = calibrationManager.getAccelerometerSpec()
    val gyroSpec: HardwareSensorSpec = calibrationManager.getGyroscopeSpec()
    val barometerSpec: HardwareSensorSpec = calibrationManager.getBarometerSpec()
    val hasStepDetector: Boolean = calibrationManager.hasStepDetectorHardware()

    init {
        // Collect live telemetry for waveform graph
        viewModelScope.launch {
            val buffer = mutableListOf<WaveformPoint>()
            crashDetectionEngine.telemetryState.collectLatest { t ->
                buffer.add(
                    WaveformPoint(
                        ax = t.accelX,
                        ay = t.accelY,
                        az = t.accelZ,
                        svmG = t.svmGForce
                    )
                )
                if (buffer.size > 80) {
                    buffer.removeAt(0)
                }
                _waveformPoints.value = buffer.toList()
            }
        }

        // Refresh battery status
        _batteryDiagnostics.value = calibrationManager.getBatteryDiagnostics()
    }

    fun startCalibration() {
        viewModelScope.launch {
            _calibrationProgress.value = 0
            calibrationManager.runInteractiveCalibration().collect { progress ->
                _calibrationProgress.value = progress
            }
            _calibrationProgress.value = null
        }
    }
}

