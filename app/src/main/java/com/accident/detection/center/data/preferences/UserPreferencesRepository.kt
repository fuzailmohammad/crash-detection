package com.accident.detection.center.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.accident.detection.center.domain.model.BloodGroup
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.domain.model.VehicleMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_safety_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.userDataStore

    private object PreferencesKeys {
        val FULL_NAME = stringPreferencesKey("full_name")
        val BLOOD_GROUP = stringPreferencesKey("blood_group")
        val ALLERGIES = stringPreferencesKey("allergies")
        val MEDICAL_CONDITIONS = stringPreferencesKey("medical_conditions")
        val MEDICATIONS = stringPreferencesKey("medications")
        val ORGAN_DONOR = booleanPreferencesKey("organ_donor")
        val VEHICLE_MODE = stringPreferencesKey("vehicle_mode")
        val PROTECTION_SHIELD_ACTIVE = booleanPreferencesKey("protection_shield_active")
        val PROFILE_COMPLETED = booleanPreferencesKey("profile_completed")
    }

    val userProfileFlow: Flow<UserProfile> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val fullName = preferences[PreferencesKeys.FULL_NAME] ?: ""
            val bloodGroupStr = preferences[PreferencesKeys.BLOOD_GROUP] ?: BloodGroup.UNKNOWN.name
            val bloodGroup = BloodGroup.fromLabel(bloodGroupStr)
            val allergies = preferences[PreferencesKeys.ALLERGIES] ?: ""
            val medicalConditions = preferences[PreferencesKeys.MEDICAL_CONDITIONS] ?: ""
            val medications = preferences[PreferencesKeys.MEDICATIONS] ?: ""
            val organDonor = preferences[PreferencesKeys.ORGAN_DONOR] ?: false
            val vehicleModeStr = preferences[PreferencesKeys.VEHICLE_MODE] ?: VehicleMode.CAR.name
            val vehicleMode = try {
                VehicleMode.valueOf(vehicleModeStr)
            } catch (e: Exception) {
                VehicleMode.CAR
            }
            val isShieldActive = preferences[PreferencesKeys.PROTECTION_SHIELD_ACTIVE] ?: true
            val isCompleted = preferences[PreferencesKeys.PROFILE_COMPLETED] ?: false

            UserProfile(
                fullName = fullName,
                bloodGroup = bloodGroup,
                allergies = allergies,
                medicalConditions = medicalConditions,
                medications = medications,
                organDonor = organDonor,
                selectedVehicleMode = vehicleMode,
                isProtectionShieldActive = isShieldActive,
                isProfileCompleted = isCompleted
            )
        }

    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_NAME] = profile.fullName
            preferences[PreferencesKeys.BLOOD_GROUP] = profile.bloodGroup.name
            preferences[PreferencesKeys.ALLERGIES] = profile.allergies
            preferences[PreferencesKeys.MEDICAL_CONDITIONS] = profile.medicalConditions
            preferences[PreferencesKeys.MEDICATIONS] = profile.medications
            preferences[PreferencesKeys.ORGAN_DONOR] = profile.organDonor
            preferences[PreferencesKeys.VEHICLE_MODE] = profile.selectedVehicleMode.name
            preferences[PreferencesKeys.PROTECTION_SHIELD_ACTIVE] = profile.isProtectionShieldActive
            preferences[PreferencesKeys.PROFILE_COMPLETED] = profile.isProfileCompleted
        }
    }

    suspend fun updateVehicleMode(vehicleMode: VehicleMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.VEHICLE_MODE] = vehicleMode.name
        }
    }

    suspend fun setProtectionShieldActive(active: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROTECTION_SHIELD_ACTIVE] = active
        }
    }

    suspend fun markProfileCompleted() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROFILE_COMPLETED] = true
        }
    }
}

