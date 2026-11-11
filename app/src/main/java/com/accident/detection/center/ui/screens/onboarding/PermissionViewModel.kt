package com.accident.detection.center.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accident.detection.center.data.repository.PermissionChecker
import com.accident.detection.center.domain.model.PermissionHealthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker
) : ViewModel() {

    val permissionState: StateFlow<PermissionHealthState> = permissionChecker.permissionState

    fun refreshPermissions() {
        viewModelScope.launch {
            permissionChecker.checkAllPermissions()
        }
    }
}

