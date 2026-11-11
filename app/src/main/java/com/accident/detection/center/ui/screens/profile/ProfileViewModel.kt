package com.accident.detection.center.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accident.detection.center.data.preferences.UserPreferencesRepository
import com.accident.detection.center.domain.model.BloodGroup
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.domain.model.VehicleMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val savedProfile: StateFlow<UserProfile> = preferencesRepository.userProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )

    private val _fullName = MutableStateFlow("")
    val fullName: StateFlow<String> = _fullName.asStateFlow()

    private val _bloodGroup = MutableStateFlow(BloodGroup.UNKNOWN)
    val bloodGroup: StateFlow<BloodGroup> = _bloodGroup.asStateFlow()

    private val _allergies = MutableStateFlow("")
    val allergies: StateFlow<String> = _allergies.asStateFlow()

    private val _medicalConditions = MutableStateFlow("")
    val medicalConditions: StateFlow<String> = _medicalConditions.asStateFlow()

    private val _medications = MutableStateFlow("")
    val medications: StateFlow<String> = _medications.asStateFlow()

    private val _organDonor = MutableStateFlow(false)
    val organDonor: StateFlow<Boolean> = _organDonor.asStateFlow()

    private val _selectedVehicleMode = MutableStateFlow(VehicleMode.CAR)
    val selectedVehicleMode: StateFlow<VehicleMode> = _selectedVehicleMode.asStateFlow()

    private val _saveSuccessEvent = MutableStateFlow(false)
    val saveSuccessEvent: StateFlow<Boolean> = _saveSuccessEvent.asStateFlow()

    init {
        viewModelScope.launch {
            savedProfile.collect { profile ->
                if (profile.fullName.isNotBlank()) {
                    _fullName.value = profile.fullName
                    _bloodGroup.value = profile.bloodGroup
                    _allergies.value = profile.allergies
                    _medicalConditions.value = profile.medicalConditions
                    _medications.value = profile.medications
                    _organDonor.value = profile.organDonor
                    _selectedVehicleMode.value = profile.selectedVehicleMode
                }
            }
        }
    }

    fun onFullNameChanged(name: String) { _fullName.value = name }
    fun onBloodGroupChanged(group: BloodGroup) { _bloodGroup.value = group }
    fun onAllergiesChanged(value: String) { _allergies.value = value }
    fun onMedicalConditionsChanged(value: String) { _medicalConditions.value = value }
    fun onMedicationsChanged(value: String) { _medications.value = value }
    fun onOrganDonorChanged(value: Boolean) { _organDonor.value = value }
    fun onVehicleModeChanged(mode: VehicleMode) { _selectedVehicleMode.value = mode }

    val isFormValid: StateFlow<Boolean> = combine(
        fullName,
        bloodGroup
    ) { name, group ->
        name.trim().isNotBlank() && group != BloodGroup.UNKNOWN
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun saveProfile(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val updated = UserProfile(
                fullName = _fullName.value.trim(),
                bloodGroup = _bloodGroup.value,
                allergies = _allergies.value.trim(),
                medicalConditions = _medicalConditions.value.trim(),
                medications = _medications.value.trim(),
                organDonor = _organDonor.value,
                selectedVehicleMode = _selectedVehicleMode.value,
                isProtectionShieldActive = true,
                isProfileCompleted = true
            )
            preferencesRepository.saveUserProfile(updated)
            _saveSuccessEvent.value = true
            onSuccess()
        }
    }
}

