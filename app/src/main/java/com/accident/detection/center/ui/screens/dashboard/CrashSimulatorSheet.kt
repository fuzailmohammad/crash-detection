package com.accident.detection.center.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accident.detection.center.ui.theme.AlertRed
import com.accident.detection.center.ui.theme.DarkBorder
import com.accident.detection.center.ui.theme.DarkCard
import com.accident.detection.center.ui.theme.DarkSurface
import com.accident.detection.center.ui.theme.EmeraldGreen
import com.accident.detection.center.ui.theme.InfoCyan
import com.accident.detection.center.ui.theme.TextMuted
import com.accident.detection.center.ui.theme.TextPrimary
import com.accident.detection.center.ui.theme.TextSecondary
import com.accident.detection.center.ui.theme.WarningAmber

/**
 * Crash Simulation Lab Bottom Sheet:
 * 1. Three interactive test buttons for zero-risk verification:
 *    - Simulate Indoor Phone Drop [False Alarm]
 *    - Simulate Pothole / Hard Brake [False Alarm]
 *    - Simulate High-Speed Crash [Triggers 30s Lockscreen Alarm]
 * 2. Live scrolling log console showing multi-stage state machine decisions in real time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashSimulatorSheet(
    sheetState: SheetState,
    isSimulating: Boolean,
    simulationMessage: String?,
    onDismiss: () -> Unit,
    onRunScenario: (SimulationScenario) -> Unit
) {
    val decisionLogs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    LaunchedEffect(simulationMessage) {
        simulationMessage?.let { msg ->
            if (decisionLogs.isEmpty() || decisionLogs.last() != msg) {
                decisionLogs.add("[${System.currentTimeMillis() % 100000}ms] $msg")
                if (decisionLogs.size > 30) decisionLogs.removeAt(0)
            }
        }
    }

    LaunchedEffect(decisionLogs.size) {
        if (decisionLogs.isNotEmpty()) {
            listState.animateScrollToItem(decisionLogs.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Crash Simulation Lab",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Real-time state machine test harness & false-alarm rejection audit",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Interactive Button 1: Simulate Indoor Phone Drop [False Alarm]
            SimulatorActionButton(
                title = "Simulate Indoor Phone Drop [False Alarm]",
                subtitle = "Injects 14G impact at 0 km/h STILL state. Filter caps confidence at 30% and aborts.",
                icon = Icons.Default.PhoneAndroid,
                accentColor = InfoCyan,
                enabled = !isSimulating,
                onClick = {
                    decisionLogs.add(">> Triggered: Phone Drop Simulation")
                    onRunScenario(SimulationScenario.PhoneDropFloor)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Interactive Button 2: Simulate Pothole / Hard Brake [False Alarm]
            SimulatorActionButton(
                title = "Simulate Pothole / Hard Brake [False Alarm]",
                subtitle = "Injects 4.8G spike at 55 km/h with continuous vehicle speed. Rejected by post-impact velocity gate.",
                icon = Icons.Default.Warning,
                accentColor = WarningAmber,
                enabled = !isSimulating,
                onClick = {
                    decisionLogs.add(">> Triggered: Pothole / Bump Simulation")
                    onRunScenario(SimulationScenario.PotholeSpeedbump)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Interactive Button 3: Simulate High-Speed Crash [Triggers 30s Lockscreen Alarm]
            SimulatorActionButton(
                title = "Simulate High-Speed Crash [Triggers Alarm]",
                subtitle = "Injects 8.5G vector + 320°/s gyro + 0 km/h stop. Confirms 98% crash score & wakes lockscreen.",
                icon = Icons.Default.CarCrash,
                accentColor = AlertRed,
                enabled = !isSimulating,
                onClick = {
                    decisionLogs.add(">> Triggered: Severe Collision Simulation (Emergency Alarm Intercept)")
                    onRunScenario(SimulationScenario.SevereSideCollision)
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Live Scrolling Log Console
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "STATE MACHINE DECISION LOG (LIVE CONSOLE)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                if (decisionLogs.isEmpty()) {
                    Text(
                        text = "System idle. Tap any simulation scenario above to observe real-time pipeline decisions...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = TextMuted
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(decisionLogs) { logLine ->
                            val color = when {
                                logLine.contains("Emergency") || logLine.contains("CONFIRMED") -> AlertRed
                                logLine.contains("Rejected") || logLine.contains("caps") -> InfoCyan
                                logLine.contains("Triggered") -> WarningAmber
                                else -> EmeraldGreen
                            }
                            Text(
                                text = logLine,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                ),
                                color = color
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SimulatorActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

