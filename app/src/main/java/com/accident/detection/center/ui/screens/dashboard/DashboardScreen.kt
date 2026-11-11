package com.accident.detection.center.ui.screens.dashboard

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accident.detection.center.domain.model.CrashDetectionStage
import com.accident.detection.center.domain.model.SensorTelemetryState
import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.ui.screens.alarm.EmergencyAlarmActivity
import com.accident.detection.center.ui.theme.AlertRed
import com.accident.detection.center.ui.theme.DarkBackground
import com.accident.detection.center.ui.theme.DarkBorder
import com.accident.detection.center.ui.theme.DarkCard
import com.accident.detection.center.ui.theme.DarkSurface
import com.accident.detection.center.ui.theme.EmeraldGreen
import com.accident.detection.center.ui.components.TelemetryWaveformGraph
import com.accident.detection.center.ui.components.WaveformPoint
import com.accident.detection.center.ui.theme.InfoCyan
import com.accident.detection.center.ui.theme.TextMuted
import com.accident.detection.center.ui.theme.TextPrimary
import com.accident.detection.center.ui.theme.TextSecondary
import com.accident.detection.center.ui.theme.WarningAmber
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToBeacon: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDiagnostics: () -> Unit = {}
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    val contactsCount by viewModel.emergencyContactsCount.collectAsState()
    val telemetry by viewModel.telemetryState.collectAsState()
    val isSimulating by viewModel.isSimulating.collectAsState()
    val simulationMessage by viewModel.simulationMessage.collectAsState()

    val waveformPoints = remember { mutableStateListOf<WaveformPoint>() }
    LaunchedEffect(telemetry) {
        if (waveformPoints.size >= 50) {
            waveformPoints.removeAt(0)
        }
        waveformPoints.add(
            WaveformPoint(
                ax = telemetry.accelX,
                ay = telemetry.accelY,
                az = telemetry.accelZ,
                svmG = telemetry.svmGForce
            )
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSimulationSheet by remember { mutableStateOf(false) }

    // Listen for confirmed crash events to trigger EmergencyAlarmActivity
    LaunchedEffect(Unit) {
        viewModel.triggerEmergencyAlarmEvent.collectLatest { payload ->
            val intent = Intent(context, EmergencyAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EmergencyAlarmActivity.EXTRA_LATITUDE, payload.latitude)
                putExtra(EmergencyAlarmActivity.EXTRA_LONGITUDE, payload.longitude)
                putExtra(EmergencyAlarmActivity.EXTRA_SPEED_KMH, payload.speedAtImpactKmh)
                putExtra(EmergencyAlarmActivity.EXTRA_PEAK_G, payload.peakGForce)
                putExtra(EmergencyAlarmActivity.EXTRA_CONFIDENCE, payload.crashConfidenceScore)
                putExtra(EmergencyAlarmActivity.EXTRA_VEHICLE_MODE, payload.vehicleMode.name)
                putExtra(EmergencyAlarmActivity.EXTRA_FORENSIC_SUMMARY, payload.forensicSummary)
                putExtra(EmergencyAlarmActivity.EXTRA_IS_SIMULATION, payload.isSimulation)
            }
            context.startActivity(intent)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // Header Bar
            DashboardHeader(
                vehicleMode = userProfile.selectedVehicleMode,
                userName = userProfile.fullName,
                onOpenSimulationLab = { showSimulationSheet = true },
                onNavigateToContacts = onNavigateToContacts,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToDiagnostics = onNavigateToDiagnostics
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Master Protection Shield Card
                item {
                    ProtectionShieldCard(
                        isActive = userProfile.isProtectionShieldActive,
                        stage = telemetry.currentStage,
                        onToggle = viewModel::toggleProtectionShield
                    )
                }

                // 2. Service Health Status Banner
                item {
                    ServiceHealthBanner(
                        isHardened = permissionState.isCompletelyHardened,
                        contactsCount = contactsCount,
                        isBatteryWhitelisted = permissionState.isBatteryOptimizationIgnored
                    )
                }

                // 3. 5-Stage Multi-Sensor Fusion Pipeline Stage Banner
                item {
                    FusionPipelineStatusCard(
                        stage = telemetry.currentStage,
                        confidenceScore = telemetry.crashConfidenceScore
                    )
                }

                // 4. Real-time Telemetry HUD Grid & IMU Oscilloscope Waveform
                item {
                    Text(
                        text = "LIVE SENSOR TELEMETRY HUD (5-SENSOR FUSION)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TelemetryHudGrid(telemetry = telemetry)

                    Spacer(modifier = Modifier.height(10.dp))
                    TelemetryWaveformGraph(
                        points = waveformPoints,
                        maxGScale = 6.0f,
                        showLegend = true
                    )
                }

                // 5. First Responder Quick Beacon Action
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToBeacon),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AlertRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = null,
                                    tint = AlertRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "First-Responder Medical Beacon",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Blood Group ${userProfile.bloodGroup.label} • Quick Paramedic Display",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sheet: Crash Simulation Lab
        if (showSimulationSheet) {
            CrashSimulatorSheet(
                sheetState = sheetState,
                isSimulating = isSimulating,
                simulationMessage = simulationMessage,
                onDismiss = { showSimulationSheet = false },
                onRunScenario = { scenario ->
                    viewModel.runSimulation(scenario)
                }
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    vehicleMode: VehicleMode,
    userName: String,
    onOpenSimulationLab: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Command Center",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = vehicleMode.icon,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${vehicleMode.displayName} • ${userName.ifBlank { "User" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onNavigateToDiagnostics,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkCard)
                    .size(38.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Hardware Diagnostics", tint = WarningAmber, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onNavigateToHistory,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkCard)
                    .size(38.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = "Incident Logs", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onOpenSimulationLab,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkCard)
                    .size(38.dp)
            ) {
                Icon(Icons.Default.Science, contentDescription = "Simulation Lab", tint = EmeraldGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onNavigateToContacts,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkCard)
                    .size(38.dp)
            ) {
                Icon(Icons.Default.People, contentDescription = "Emergency Contacts", tint = InfoCyan, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ProtectionShieldCard(
    isActive: Boolean,
    stage: CrashDetectionStage,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val shieldColor by animateColorAsState(
        targetValue = if (isActive) EmeraldGreen else WarningAmber,
        label = "shield_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, shieldColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .scale(if (isActive) pulseScale else 1.0f)
                    .clip(CircleShape)
                    .background(shieldColor.copy(alpha = 0.15f))
                    .border(2.dp, shieldColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Shield",
                    tint = shieldColor,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isActive) "PROTECTION SHIELD ACTIVE" else "PROTECTION PAUSED",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = shieldColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isActive) "5-Sensor Fusion Engine Guarding • Tap to Pause" else "Tap to Resume Monitoring Protection",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ServiceHealthBanner(
    isHardened: Boolean,
    contactsCount: Int,
    isBatteryWhitelisted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isHardened) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isHardened) EmeraldGreen else WarningAmber,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isHardened) "System Hardened" else "Hardening Incomplete",
                style = MaterialTheme.typography.labelSmall,
                color = if (isHardened) EmeraldGreen else WarningAmber
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = null,
                tint = if (contactsCount > 0) InfoCyan else AlertRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$contactsCount Contact${if (contactsCount != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = if (contactsCount > 0) TextPrimary else AlertRed
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = if (isBatteryWhitelisted) EmeraldGreen else WarningAmber,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isBatteryWhitelisted) "Doze Exempt" else "Optimized",
                style = MaterialTheme.typography.labelSmall,
                color = if (isBatteryWhitelisted) EmeraldGreen else WarningAmber
            )
        }
    }
}

@Composable
private fun FusionPipelineStatusCard(
    stage: CrashDetectionStage,
    confidenceScore: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "PIPELINE STAGE ${stage.stageNumber}/5",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                    Text(
                        text = stage.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = when (stage) {
                            CrashDetectionStage.CRASH_CONFIRMED -> AlertRed
                            CrashDetectionStage.IMPACT_DETECTED, CrashDetectionStage.VERIFYING_POST_IMPACT -> WarningAmber
                            CrashDetectionStage.FALSE_ALARM_ABORTED -> InfoCyan
                            else -> EmeraldGreen
                        }
                    )
                }

                // Crash Score Badge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CRASH SCORE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "${confidenceScore.toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (confidenceScore >= 80f) AlertRed else if (confidenceScore >= 40f) WarningAmber else EmeraldGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { confidenceScore / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (confidenceScore >= 80f) AlertRed else if (confidenceScore >= 40f) WarningAmber else EmeraldGreen,
                trackColor = DarkBorder
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stage.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun TelemetryHudGrid(telemetry: SensorTelemetryState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudMetricCard(
                modifier = Modifier.weight(1f),
                title = "G-Force (SVM)",
                value = String.format("%.2f G", telemetry.svmGForce),
                subtext = "X:${String.format("%.1f", telemetry.accelX)} Y:${String.format("%.1f", telemetry.accelY)} Z:${String.format("%.1f", telemetry.accelZ)}",
                icon = Icons.Default.ElectricBolt,
                tint = if (telemetry.svmGForce > 3.0f) AlertRed else EmeraldGreen
            )

            HudMetricCard(
                modifier = Modifier.weight(1f),
                title = "Gyro Angular Spin",
                value = String.format("%.2f rad/s", telemetry.rotationalRateRadS),
                subtext = "Roll & Yaw Dynamics",
                icon = Icons.Default.DirectionsCar,
                tint = if (telemetry.rotationalRateRadS > 4.0f) AlertRed else InfoCyan
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudMetricCard(
                modifier = Modifier.weight(1f),
                title = "GPS Velocity",
                value = "${telemetry.speedKmh.toInt()} km/h",
                subtext = "Gate: >20 km/h • ${telemetry.activityType}",
                icon = Icons.Default.Speed,
                tint = if (telemetry.speedKmh >= 20.0f) EmeraldGreen else TextSecondary
            )

            HudMetricCard(
                modifier = Modifier.weight(1f),
                title = "Acoustic Decibel",
                value = "${telemetry.soundLevelDb.toInt()} dB",
                subtext = "Airbag Pop Sensor",
                icon = Icons.Default.VolumeUp,
                tint = if (telemetry.soundLevelDb > 85f) AlertRed else WarningAmber
            )
        }
    }
}

@Composable
private fun HudMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    tint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = tint
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}
