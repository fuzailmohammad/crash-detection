package com.accident.detection.center.ui.screens.dashboard

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accident.detection.center.data.preferences.UserPreferencesRepository
import com.accident.detection.center.domain.repository.CrashLogRepository
import com.accident.detection.center.domain.repository.EmergencyContactRepository
import com.accident.detection.center.data.repository.PermissionChecker
import com.accident.detection.center.domain.model.PermissionHealthState
import com.accident.detection.center.domain.model.SensorTelemetryState
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.sensor.CrashDetectionEngine
import com.accident.detection.center.sensor.CrashEventPayload
import com.accident.detection.center.service.AccidentMonitoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SimulationScenario(val label: String, val description: String) {
    data object PotholeSpeedbump : SimulationScenario(
        label = "Pothole / Severe Speedbump",
        description = "Sudden 2.8G vertical shock, but vehicular speed maintains 52 km/h post-impact. Verifies Stage 5 false-alarm filtering."
    )
    data object PhoneDropFloor : SimulationScenario(
        label = "Phone Dropped to Tile Floor",
        description = "Instantaneous 14.2G shock at 0 km/h, followed by user walking steps. Stage 1 gate caps score at 40 and filters false alarm."
    )
    data object SevereSideCollision : SimulationScenario(
        label = "Severe T-Bone Collision (7.2G)",
        description = "68 km/h travel + 7.2G impact + 340 deg/s spin + 122 dB airbag crunch + 0 km/h halt. Verified crash triggers emergency intercept!"
    )
    data object HighSpeedRollover : SimulationScenario(
        label = "High-Speed Rollover (5.8G)",
        description = "75 km/h highway speed + 5.8G shock + 420 deg/s rollover spin + 0 km/h halt. Verified crash triggers emergency intercept!"
    )
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val contactRepository: EmergencyContactRepository,
    private val crashLogRepository: CrashLogRepository,
    private val permissionChecker: PermissionChecker,
    private val crashDetectionEngine: CrashDetectionEngine
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = preferencesRepository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val permissionState: StateFlow<PermissionHealthState> = permissionChecker.permissionState

    val emergencyContactsCount: StateFlow<Int> = contactRepository.getContactCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Direct binding to the 5-sensor fusion engine's live telemetry state
    val telemetryState: StateFlow<SensorTelemetryState> = crashDetectionEngine.telemetryState

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _simulationMessage = MutableStateFlow<String?>(null)
    val simulationMessage: StateFlow<String?> = _simulationMessage.asStateFlow()

    private val _triggerEmergencyAlarmEvent = MutableSharedFlow<CrashEventPayload>()
    val triggerEmergencyAlarmEvent: SharedFlow<CrashEventPayload> = _triggerEmergencyAlarmEvent.asSharedFlow()

    init {
        // Automatically start engine and foreground service if shield is active
        viewModelScope.launch {
            userProfile.collectLatest { profile ->
                if (profile.isProtectionShieldActive) {
                    crashDetectionEngine.startEngine()
                    try {
                        val serviceIntent = Intent(context, AccidentMonitoringService::class.java)
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } catch (e: Exception) {
                        // Background launch restriction fallback
                    }
                } else {
                    crashDetectionEngine.stopEngine()
                    try {
                        val serviceIntent = Intent(context, AccidentMonitoringService::class.java)
                        context.stopService(serviceIntent)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        // Forward engine crash confirmation events to UI
        viewModelScope.launch {
            crashDetectionEngine.confirmedCrashEvents.collectLatest { payload ->
                _triggerEmergencyAlarmEvent.emit(payload)
            }
        }
    }

    fun toggleProtectionShield() {
        viewModelScope.launch {
            val current = userProfile.value.isProtectionShieldActive
            preferencesRepository.setProtectionShieldActive(!current)
        }
    }

    fun setVehicleMode(vehicleMode: VehicleMode) {
        viewModelScope.launch {
            preferencesRepository.updateVehicleMode(vehicleMode)
        }
    }

    fun runSimulation(scenario: SimulationScenario) {
        viewModelScope.launch {
            _isSimulating.value = true
            _simulationMessage.value = "Executing ${scenario.label}..."
            crashDetectionEngine.simulateScenario(scenario)
            _isSimulating.value = false
            _simulationMessage.value = null
        }
    }
}
