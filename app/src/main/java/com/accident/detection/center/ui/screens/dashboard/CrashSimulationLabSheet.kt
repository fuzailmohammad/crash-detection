package com.accident.detection.center.ui.screens.dashboard

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable

/**
 * Backward-compatible wrapper delegating to [CrashSimulatorSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashSimulationLabSheet(
    sheetState: SheetState,
    isSimulating: Boolean,
    simulationMessage: String?,
    onDismiss: () -> Unit,
    onRunScenario: (SimulationScenario) -> Unit
) {
    CrashSimulatorSheet(
        sheetState = sheetState,
        isSimulating = isSimulating,
        simulationMessage = simulationMessage,
        onDismiss = onDismiss,
        onRunScenario = onRunScenario
    )
}
