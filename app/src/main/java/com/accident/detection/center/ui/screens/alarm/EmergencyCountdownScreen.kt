package com.accident.detection.center.ui.screens.alarm

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.accident.detection.center.domain.model.ChannelDispatchStatus
import com.accident.detection.center.domain.model.ContactDispatchProgress
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.ui.components.SlideToCancelButton
import com.accident.detection.center.ui.theme.AlertRed
import com.accident.detection.center.ui.theme.AlertRedDark
import com.accident.detection.center.ui.theme.DarkBackground
import com.accident.detection.center.ui.theme.DarkBorder
import com.accident.detection.center.ui.theme.DarkCard
import com.accident.detection.center.ui.theme.EmeraldGreen
import com.accident.detection.center.ui.theme.InfoCyan
import com.accident.detection.center.ui.theme.TextMuted
import com.accident.detection.center.ui.theme.TextPrimary
import com.accident.detection.center.ui.theme.TextSecondary
import com.accident.detection.center.ui.theme.WarningAmber
import kotlinx.coroutines.delay

@Composable
fun EmergencyCountdownScreen(
    viewModel: EmergencyAlarmViewModel,
    onDismissActivity: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    // High-contrast emergency red/black pulsing background
    val pulseTransition = rememberInfiniteTransition(label = "bg_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val backgroundColor = when (uiState.phase) {
        EmergencyCountdownPhase.COUNTING_DOWN -> Color(0xFF380000)
        EmergencyCountdownPhase.CANCELLED_SAFE -> DarkBackground
        EmergencyCountdownPhase.DISPATCHING_ALERTS,
        EmergencyCountdownPhase.RESPONDER_BEACON_ACTIVE -> Color(0xFF140808)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState.phase) {
                EmergencyCountdownPhase.COUNTING_DOWN -> {
                    CountdownActiveView(
                        uiState = uiState,
                        pulseAlpha = pulseAlpha,
                        onSlideToCancel = viewModel::onSlideToCancelSuccess,
                        onSendSosNow = viewModel::onTriggerSosImmediately
                    )
                }

                EmergencyCountdownPhase.DISPATCHING_ALERTS,
                EmergencyCountdownPhase.RESPONDER_BEACON_ACTIVE -> {
                    ResponderBeaconView(
                        uiState = uiState,
                        userProfile = userProfile,
                        emergencyContacts = contacts,
                        hardwareController = viewModel.hardwareController,
                        onClose = onDismissActivity
                    )
                }

                EmergencyCountdownPhase.CANCELLED_SAFE -> {
                    CancelledSafeView(onDismiss = onDismissActivity)
                }
            }

            // 3-Second Optional Survey Dialog upon Cancellation
            if (uiState.isShowingCancellationSurvey) {
                CancellationSurveyDialog(
                    onReasonSelected = { reason ->
                        viewModel.onCancelConfirmed(reason)
                    },
                    onTimeoutOrDismiss = {
                        viewModel.onCancelConfirmed(null)
                    }
                )
            }
        }
    }
}

/**
 * 30-Second Countdown Screen View with Giant Timer Ring, Telemetry Pill, and Anti-Pocket Slider.
 */
@Composable
private fun CountdownActiveView(
    uiState: EmergencyCountdownUiState,
    pulseAlpha: Float,
    onSlideToCancel: () -> Unit,
    onSendSosNow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. TOP: Immediate SOS Dispatch Button
        Button(
            onClick = onSendSosNow,
            colors = ButtonDefaults.buttonColors(
                containerColor = AlertRed,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "I AM INJURED — SEND SOS NOW",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 1.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. CENTER: Giant Circular Animated Countdown Ring & Alert Label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = "CRASH DETECTED",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = Color.White.copy(alpha = pulseAlpha)
            )

            Text(
                text = "Emergency cascade starting in",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Giant Circular Progress Ring
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .scale(pulseAlpha),
                contentAlignment = Alignment.Center
            ) {
                // Background Track
                CircularProgressIndicator(
                    progress = { 1.0f },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White.copy(alpha = 0.15f),
                    strokeWidth = 16.dp,
                    strokeCap = StrokeCap.Round
                )

                // Animated Active Ring (from 1.0f to 0.0f)
                CircularProgressIndicator(
                    progress = { uiState.progressFraction },
                    modifier = Modifier.fillMaxSize(),
                    color = if (uiState.remainingSeconds <= 10) AlertRed else WarningAmber,
                    strokeWidth = 16.dp,
                    strokeCap = StrokeCap.Round
                )

                // 72.sp Bold Countdown Number
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val formattedSec = String.format("%02d", uiState.remainingSeconds)
                    Text(
                        text = formattedSec,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "SECONDS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Telemetry Summary Pill
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.telemetrySummaryText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. BOTTOM: Anti-Pocket-Tap Slide-to-Cancel Gesture Button
        SlideToCancelButton(
            text = "SLIDE TO CANCEL (I AM SAFE)",
            onCancelled = onSlideToCancel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 3-Second Optional Cancellation Reason Survey Dialog.
 */
@Composable
private fun CancellationSurveyDialog(
    onReasonSelected: (String) -> Unit,
    onTimeoutOrDismiss: () -> Unit
) {
    var secondsRemaining by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining -= 1
        }
        onTimeoutOrDismiss()
    }

    Dialog(
        onDismissRequest = onTimeoutOrDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, EmeraldGreen)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Glad you're safe!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )

                Text(
                    text = "What triggered this alert?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Survey Options
                val options = listOf(
                    "📱 Dropped Phone" to "Dropped Phone",
                    "🛣️ Rough Road / Pothole" to "Rough Road / Pothole",
                    "🛑 Hard Braking" to "Hard Braking",
                    "🧪 Testing / Simulation" to "Testing"
                )

                options.forEach { (label, value) ->
                    OutlinedButton(
                        onClick = { onReasonSelected(value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Countdown bar before auto-close
                LinearProgressIndicator(
                    progress = { secondsRemaining / 3f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = EmeraldGreen,
                    trackColor = DarkBorder
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Closing in $secondsRemaining seconds...",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

/**
 * Screen displayed when alarm is cancelled and confirmed safe.
 */
@Composable
private fun CancelledSafeView(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(EmeraldGreen.copy(alpha = 0.15f))
                .border(2.dp, EmeraldGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldGreen,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Alarm Cancelled",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )

        Text(
            text = "No emergency contacts were alerted. You have been marked safe.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "Back to Safe State",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black
            )
        }
    }
}

/**
 * Multi-channel dispatch progress and first-responder medical beacon view.
 */
@Composable
private fun AutomatedDispatchAndBeaconView(
    uiState: EmergencyCountdownUiState,
    userProfile: UserProfile,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Dispatch Status Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AlertRedDark),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (uiState.phase == EmergencyCountdownPhase.DISPATCHING_ALERTS)
                                "EMERGENCY CASCADE IN PROGRESS"
                            else "SOS DISPATCH COMPLETED",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (uiState.phase == EmergencyCountdownPhase.DISPATCHING_ALERTS)
                            "Sending silent SMS with GPS coordinates, dispatching crash reports, and initiating speakerphone call."
                        else "All priority emergency contacts have been alerted. Paramedic Beacon is broadcasting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 2. Contact Dispatch Progress Cards
        item {
            Text(
                text = "DISPATCH AUDIT TRAIL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = TextSecondary
            )
        }

        items(uiState.contactProgressList) { contact ->
            ContactDispatchCard(contact)
        }

        // 3. Paramedic Medical ID Beacon
        item {
            Spacer(modifier = Modifier.height(8.dp))
            ParamedicMedicalBeaconCard(userProfile)
        }

        // 4. Quick Emergency Calling Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        .height(56.dp),
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
                        .height(56.dp),
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

@Composable
private fun ContactDispatchCard(contact: ContactDispatchProgress) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contact.contactName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChannelStatusBadge("SMS", contact.smsStatus, Icons.Default.Sms)
                ChannelStatusBadge("EMAIL", contact.emailStatus, Icons.Default.Email)
                ChannelStatusBadge("CALL", contact.callStatus, Icons.Default.Phone)
            }
        }
    }
}

@Composable
private fun ChannelStatusBadge(label: String, status: ChannelDispatchStatus, icon: ImageVector) {
    val (statusColor, statusText) = when (status) {
        ChannelDispatchStatus.SUCCESS -> EmeraldGreen to "Sent"
        ChannelDispatchStatus.IN_PROGRESS -> WarningAmber to "Sending"
        ChannelDispatchStatus.FAILED -> AlertRed to "Failed"
        ChannelDispatchStatus.QUEUED -> InfoCyan to "Queued"
        ChannelDispatchStatus.SKIPPED -> TextMuted to "Skipped"
        ChannelDispatchStatus.IDLE -> TextMuted to "Idle"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: $statusText",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = statusColor
        )
    }
}

@Composable
private fun ParamedicMedicalBeaconCard(profile: UserProfile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228)),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, InfoCyan)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalHospital,
                    contentDescription = null,
                    tint = InfoCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PARAMEDIC MEDICAL ID BEACON",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = InfoCyan
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            MedicalInfoRow("Patient Name", profile.fullName.ifBlank { "Not Specified" }, Icons.Default.Person)
            MedicalInfoRow("Blood Group", profile.bloodGroup.label, Icons.Default.Bloodtype)
            MedicalInfoRow("Allergies", profile.allergies.ifBlank { "No Known Allergies" }, Icons.Default.Warning)
            MedicalInfoRow("Medical Conditions", profile.medicalConditions.ifBlank { "None Reported" }, Icons.Default.Medication)
        }
    }
}

@Composable
private fun MedicalInfoRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$label: ", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
    }
}

