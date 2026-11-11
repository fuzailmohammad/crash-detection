package com.accident.detection.center.ui.screens.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accident.detection.center.calibration.HardwareSensorSpec
import com.accident.detection.center.service.PowerPolicy
import com.accident.detection.center.ui.components.TelemetryWaveformGraph
import com.accident.detection.center.ui.theme.DarkBackground
import com.accident.detection.center.ui.theme.DarkBorder
import com.accident.detection.center.ui.theme.DarkCard
import com.accident.detection.center.ui.theme.EmeraldGreen
import com.accident.detection.center.ui.theme.InfoCyan
import com.accident.detection.center.ui.theme.TextMuted
import com.accident.detection.center.ui.theme.TextPrimary
import com.accident.detection.center.ui.theme.TextSecondary
import com.accident.detection.center.ui.theme.WarningAmber

@Composable
fun SensorDiagnosticsScreen(
    viewModel: SensorDiagnosticsViewModel,
    onBack: () -> Unit
) {
    val waveformPoints by viewModel.waveformPoints.collectAsState()
    val calibrationOffsets by viewModel.calibrationOffsets.collectAsState()
    val calibrationProgress by viewModel.calibrationProgress.collectAsState()
    val powerPolicy by viewModel.powerPolicy.collectAsState()
    val battery by viewModel.batteryDiagnostics.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Hardware Diagnostics & Lab",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Real-time telemetry oscilloscope & sensor bias calibration",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Real-time Waveform Oscilloscope Graph
                item {
                    TelemetryWaveformGraph(
                        points = waveformPoints,
                        maxGScale = 5.0f,
                        showLegend = true
                    )
                }

                // 2. Interactive Sensor Calibration Card
                item {
                    CalibrationCard(
                        offsets = calibrationOffsets,
                        progress = calibrationProgress,
                        onStartCalibration = viewModel::startCalibration
                    )
                }

                // 3. Power Governor Policy & Battery Health
                item {
                    PowerGovernorCard(
                        policy = powerPolicy,
                        batteryPct = battery.percentage,
                        tempCelsius = battery.temperatureCelsius,
                        isCharging = battery.isCharging,
                        powerSource = battery.powerSource
                    )
                }

                // 4. Onboard Sensor Hardware Specs
                item {
                    Text(
                        text = "ONBOARD SENSOR HARDWARE SPECIFICATIONS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SensorSpecCard(
                        title = "Tri-Axial Accelerometer",
                        spec = viewModel.accelSpec,
                        icon = Icons.Default.Speed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SensorSpecCard(
                        title = "Tri-Axial Gyroscope",
                        spec = viewModel.gyroSpec,
                        icon = Icons.Default.Sensors
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SensorSpecCard(
                        title = "Barometric Cabin Pressure",
                        spec = viewModel.barometerSpec,
                        icon = Icons.Default.Memory
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (viewModel.hasStepDetector) EmeraldGreen.copy(alpha = 0.2f) else DarkBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = if (viewModel.hasStepDetector) EmeraldGreen else TextSecondary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Hardware Step Detector", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    if (viewModel.hasStepDetector) "Hardware Pedometer Present (Instant False Alarm Filtering)" else "Simulated Software Fallback",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (viewModel.hasStepDetector) EmeraldGreen else WarningAmber
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationCard(
    offsets: com.accident.detection.center.calibration.CalibrationOffsets,
    progress: Int?,
    onStartCalibration: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (offsets.isCalibrated) EmeraldGreen.copy(alpha = 0.5f) else DarkBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Sensor Zero-Bias Calibration", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            if (offsets.isCalibrated) "Calibrated • Noise Floor σ = ${String.format("%.3f", offsets.noiseStdDev)}G" else "Uncalibrated Baseline",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (offsets.isCalibrated) EmeraldGreen else WarningAmber
                        )
                    }
                }

                if (offsets.isCalibrated) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Calibrated", tint = EmeraldGreen, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Place your device flat on a stable surface (table or phone mount) and tap Calibrate to eliminate factory sensor drift offsets.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (progress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sampling sensor baseline...", style = MaterialTheme.typography.labelSmall, color = EmeraldGreen)
                        Text("$progress%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = EmeraldGreen)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = EmeraldGreen,
                        trackColor = DarkBorder
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartCalibration,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Calibrate Sensor Baseline", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PowerGovernorCard(
    policy: PowerPolicy,
    batteryPct: Int,
    tempCelsius: Float,
    isCharging: Boolean,
    powerSource: String
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Adaptive Power Governor", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(InfoCyan.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(policy.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = InfoCyan)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(policy.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$batteryPct% ($powerSource)", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Thermostat, contentDescription = null, tint = if (tempCelsius > 40f) WarningAmber else TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${String.format("%.1f", tempCelsius)}°C", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun SensorSpecCard(
    title: String,
    spec: HardwareSensorSpec,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(modifier = Modifier.weight(1f))
                Text(if (spec.isPresent) "Active" else "Missing", color = if (spec.isPresent) EmeraldGreen else WarningAmber, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Model: ${spec.sensorName} (${spec.vendor})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Max Range: ${spec.maxRange} • Power: ${spec.powerMa} mA", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextMuted)
        }
    }
}

