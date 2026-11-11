package com.accident.detection.center.ui.screens.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import com.accident.detection.center.domain.model.ChannelDispatchStatus
import com.accident.detection.center.domain.model.ContactDispatchProgress
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.hardware.EmergencyHardwareController
import com.accident.detection.center.ui.theme.AlertRed
import com.accident.detection.center.ui.theme.AlertRedDark
import com.accident.detection.center.ui.theme.DarkBorder
import com.accident.detection.center.ui.theme.DarkCard
import com.accident.detection.center.ui.theme.EmeraldGreen
import com.accident.detection.center.ui.theme.InfoCyan
import com.accident.detection.center.ui.theme.TextMuted
import com.accident.detection.center.ui.theme.TextPrimary
import com.accident.detection.center.ui.theme.TextSecondary
import com.accident.detection.center.ui.theme.WarningAmber

/**
 * Permanent, high-contrast First Responder & Paramedic Medical Beacon View:
 * 1. Live checklist showing multi-channel dispatch audit trail.
 * 2. Giant Paramedic Medical ID Card with Blood Group Badge & 1-tap contact calls.
 * 3. Siren Mute Toggle allowing first responders to silence the alarm while keeping
 *    strobe lights and screen active.
 */
@Composable
fun ResponderBeaconView(
    uiState: EmergencyCountdownUiState,
    userProfile: UserProfile,
    emergencyContacts: List<EmergencyContactEntity>,
    hardwareController: EmergencyHardwareController? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isSirenMuted by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF140606)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. High-Urgency Beacon Header
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AlertRedDark),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AlertRed)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalHospital,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "FIRST RESPONDER & PARAMEDIC BEACON",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Automated emergency dispatch completed. Telemetry and live coordinates broadcasted to emergency contacts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 2. Paramedic Siren Mute / Strobe Control Button
            item {
                Button(
                    onClick = {
                        isSirenMuted = !isSirenMuted
                        if (isSirenMuted) {
                            hardwareController?.stopSiren()
                        } else {
                            hardwareController?.startSiren()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSirenMuted) Color(0xFF2C3238) else WarningAmber
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (isSirenMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = if (isSirenMuted) Color.White else Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSirenMuted)
                            "SIREN MUTED (SCREEN & STROBE ACTIVE)"
                        else "MUTE AUDIBLE SIREN (FOR PARAMEDICS)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isSirenMuted) Color.White else Color.Black
                    )
                }
            }

            // 3. Giant Medical ID Card
            item {
                GiantMedicalIdCard(userProfile)
            }

            // 4. Live Multi-Channel Dispatch Audit Trail
            item {
                Text(
                    text = "DISPATCH AUDIT TRAIL (LIVE VERIFICATION)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = TextSecondary
                )
            }

            items(uiState.contactProgressList) { contactProgress ->
                val contactEntity = emergencyContacts.firstOrNull { it.id == contactProgress.contactId }
                ContactDispatchRow(
                    contactProgress = contactProgress,
                    onDirectCall = {
                        val callUri = Uri.parse("tel:${contactProgress.phoneNumber}")
                        val callIntent = Intent(Intent.ACTION_CALL, callUri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(callIntent)
                        } catch (e: Exception) {
                            val dialIntent = Intent(Intent.ACTION_DIAL, callUri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(dialIntent)
                        }
                    }
                )
            }

            // 5. Emergency Calling Buttons & Exit Beacon
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:911")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Call 911 / 112",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Text(
                            text = "Close Beacon",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GiantMedicalIdCard(userProfile: UserProfile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, AlertRed)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row: Paramedic Badge & Giant Blood Group
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PARAMEDIC MEDICAL ID",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = AlertRed
                    )
                    Text(
                        text = userProfile.fullName.ifBlank { "Unidentified Patient" },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.Black
                    )
                }

                // Giant Blood Group Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AlertRed)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userProfile.bloodGroup.label,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        ),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Medical Details
            MedicalDetailRow("Allergies", userProfile.allergies.ifBlank { "No Known Allergies" }, AlertRed)
            MedicalDetailRow("Medical Conditions", userProfile.medicalConditions.ifBlank { "None Reported" }, Color(0xFF333333))
            MedicalDetailRow("Current Medications", userProfile.medications.ifBlank { "None Reported" }, Color(0xFF333333))
            MedicalDetailRow("Organ Donor", if (userProfile.organDonor) "YES" else "NO", Color(0xFF1B5E20))
        }
    }
}

@Composable
private fun MedicalDetailRow(label: String, value: String, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF666666)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black),
            color = accentColor
        )
    }
}

@Composable
private fun ContactDispatchRow(
    contactProgress: ContactDispatchProgress,
    onDirectCall: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = contactProgress.contactName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = contactProgress.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // 1-Tap Quick Call Button
                IconButtonClickable(
                    onClick = onDirectCall,
                    icon = Icons.Default.Call,
                    tint = EmeraldGreen,
                    backgroundColor = EmeraldGreen.copy(alpha = 0.15f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Channel Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChannelBadge("SMS", contactProgress.smsStatus, Icons.Default.Sms)
                ChannelBadge("EMAIL", contactProgress.emailStatus, Icons.Default.Email)
                ChannelBadge("CALL", contactProgress.callStatus, Icons.Default.Phone)
            }
        }
    }
}

@Composable
private fun ChannelBadge(label: String, status: ChannelDispatchStatus, icon: ImageVector) {
    val (statusColor, statusText) = when (status) {
        ChannelDispatchStatus.SUCCESS -> EmeraldGreen to "Sent"
        ChannelDispatchStatus.IN_PROGRESS -> WarningAmber to "In Progress"
        ChannelDispatchStatus.FAILED -> AlertRed to "Failed"
        ChannelDispatchStatus.QUEUED -> InfoCyan to "Queued"
        ChannelDispatchStatus.SKIPPED -> TextMuted to "Skipped"
        ChannelDispatchStatus.IDLE -> TextMuted to "Idle"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: $statusText",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = statusColor
        )
    }
}

@Composable
private fun IconButtonClickable(
    onClick: () -> Unit,
    icon: ImageVector,
    tint: Color,
    backgroundColor: Color
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

