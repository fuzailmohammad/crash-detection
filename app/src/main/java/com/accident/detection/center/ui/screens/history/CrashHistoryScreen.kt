package com.accident.detection.center.ui.screens.history

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accident.detection.center.data.local.entity.CrashEventLogEntity
import com.accident.detection.center.domain.model.CrashDetectionStage
import com.accident.detection.center.ui.theme.AlertRed
import com.accident.detection.center.ui.theme.DarkBackground
import com.accident.detection.center.ui.theme.DarkBorder
import com.accident.detection.center.ui.theme.DarkCard
import com.accident.detection.center.ui.theme.EmeraldGreen
import com.accident.detection.center.ui.theme.InfoCyan
import com.accident.detection.center.ui.theme.TextMuted
import com.accident.detection.center.ui.theme.TextPrimary
import com.accident.detection.center.ui.theme.TextSecondary
import com.accident.detection.center.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CrashHistoryScreen(
    viewModel: CrashHistoryViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val logs by viewModel.crashLogs.collectAsState()

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Incident Audit Logs",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }

                if (logs.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearAllLogs) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All", tint = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Incident Logs Found",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Verified collisions, simulation runs, and filtered false alarms will be recorded here for forensic audit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        CrashLogItemCard(
                            log = log,
                            onDelete = { viewModel.deleteLog(log.id) },
                            onOpenMap = {
                                val mapIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://maps.google.com/?q=${log.latitude},${log.longitude}")
                                )
                                context.startActivity(mapIntent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashLogItemCard(
    log: CrashEventLogEntity,
    onDelete: () -> Unit,
    onOpenMap: () -> Unit
) {
    val isConfirmed = log.finalDetectionStage == CrashDetectionStage.CRASH_CONFIRMED
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(log.timestampEpochMs))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConfirmed) AlertRed.copy(alpha = 0.6f) else DarkBorder
        )
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConfirmed) AlertRed.copy(alpha = 0.2f)
                                else if (log.isSimulation) EmeraldGreen.copy(alpha = 0.2f)
                                else InfoCyan.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isConfirmed) Icons.Default.CarCrash
                            else if (log.isSimulation) Icons.Default.Science
                            else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isConfirmed) AlertRed else if (log.isSimulation) EmeraldGreen else InfoCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = if (isConfirmed) "COLLISION CONFIRMED"
                            else if (log.isSimulation) "SIMULATION TEST"
                            else "FALSE ALARM FILTERED",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isConfirmed) AlertRed else if (log.isSimulation) EmeraldGreen else InfoCyan
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(label = "Peak Impact", value = "${String.format("%.1f", log.peakGForce)} G")
                MetricColumn(label = "Impact Speed", value = "${log.speedAtImpactKmh.toInt()} km/h")
                MetricColumn(label = "Acoustics", value = "${log.ambientNoiseDb.toInt()} dB")
                MetricColumn(label = "Score", value = "${log.crashConfidenceScore.toInt()}%")
            }

            if (!log.falseAlarmReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Filter: ${log.falseAlarmReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber
                )
            }

            if (log.dispatchSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dispatch: ${log.dispatchSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldGreen
                )
            }

            if (log.latitude != 0.0 || log.longitude != 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenMap,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Location on Map", color = EmeraldGreen, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    }
}

