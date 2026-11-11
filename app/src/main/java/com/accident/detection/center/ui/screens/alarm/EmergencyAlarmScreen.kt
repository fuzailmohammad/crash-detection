package com.accident.detection.center.ui.screens.alarm

import androidx.compose.runtime.Composable

/**
 * Backward-compatible entry composable delegating to [EmergencyCountdownScreen].
 */
@Composable
fun EmergencyAlarmScreen(
    viewModel: EmergencyAlarmViewModel,
    onDismissActivity: () -> Unit
) {
    EmergencyCountdownScreen(
        viewModel = viewModel,
        onDismissActivity = onDismissActivity
    )
}
