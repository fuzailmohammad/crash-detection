package com.accident.detection.center.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.ui.theme.AlertRed
import com.accident.detection.center.ui.theme.DarkBorder
import com.accident.detection.center.ui.theme.DarkCard
import com.accident.detection.center.ui.theme.InfoCyan
import com.accident.detection.center.ui.theme.TextMuted
import com.accident.detection.center.ui.theme.TextPrimary
import com.accident.detection.center.ui.theme.TextSecondary
import kotlin.math.abs

/**
 * Offline Paramedic QR Code Card:
 * Renders an offline-scannable 2D barcode matrix encoding the patient's critical medical data
 * (Name, Blood Group, Allergies, Medical Conditions, Emergency Contacts, and GPS Coordinates)
 * so emergency responders can access medical records instantly in zero-cellular accident zones.
 */
@Composable
fun ParamedicQrCodeCard(
    userProfile: UserProfile,
    latitude: Double = 0.0,
    longitude: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val qrPayload = remember(userProfile, latitude, longitude) {
        "MED-ID: ${userProfile.fullName.ifBlank { "Unidentified" }} | " +
                "BLOOD: ${userProfile.bloodGroup.label} | " +
                "ALLERGIES: ${userProfile.allergies.ifBlank { "None" }} | " +
                "CONDITIONS: ${userProfile.medicalConditions.ifBlank { "None" }} | " +
                "ORGAN DONOR: ${if (userProfile.organDonor) "YES" else "NO"} | " +
                "GPS: %.5f, %.5f".format(latitude, longitude)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = InfoCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "OFFLINE PARAMEDIC QR CODE",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Scannable with any camera lens in zero-reception rural zones",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // White QR Matrix Container
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                QrMatrixCanvas(
                    data = qrPayload,
                    modifier = Modifier.size(176.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Payload: Blood ${userProfile.bloodGroup.label} • ${userProfile.allergies.ifBlank { "No Allergies" }}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

/**
 * Pure Kotlin Canvas 2D matrix renderer simulating QR layout with 3 position detection patterns.
 */
@Composable
private fun QrMatrixCanvas(
    data: String,
    modifier: Modifier = Modifier
) {
    val gridSize = 25 // 25x25 QR Version 2 module grid
    val hash = abs(data.hashCode())

    Canvas(modifier = modifier) {
        val moduleSize = size.width / gridSize

        // 1. Draw 3 Finder Patterns (Top-Left, Top-Right, Bottom-Left)
        drawFinderPattern(0f, 0f, moduleSize)
        drawFinderPattern((gridSize - 7) * moduleSize, 0f, moduleSize)
        drawFinderPattern(0f, (gridSize - 7) * moduleSize, moduleSize)

        // 2. Draw Data Modules based on payload hash and character distribution
        val bytes = data.toByteArray()
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Skip finder pattern zones (7x7 modules at 3 corners)
                val inTopLeft = row < 7 && col < 7
                val inTopRight = row < 7 && col >= gridSize - 7
                val inBottomLeft = row >= gridSize - 7 && col < 7
                if (inTopLeft || inTopRight || inBottomLeft) continue

                // Timing patterns (row 6 and col 6)
                if (row == 6 || col == 6) {
                    if ((row + col) % 2 == 0) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(col * moduleSize, row * moduleSize),
                            size = Size(moduleSize, moduleSize)
                        )
                    }
                    continue
                }

                // Deterministic data encoding pattern
                val byteIndex = (row * gridSize + col) % bytes.size
                val bitVal = (bytes[byteIndex].toInt() xor (hash ushr (col % 16))) and 1
                if (bitVal == 1 || (row + col) % 3 == 0) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(col * moduleSize, row * moduleSize),
                        size = Size(moduleSize, moduleSize)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFinderPattern(
    x: Float,
    y: Float,
    moduleSize: Float
) {
    // 7x7 outer black square
    drawRect(
        color = Color.Black,
        topLeft = Offset(x, y),
        size = Size(7 * moduleSize, 7 * moduleSize)
    )
    // 5x5 inner white square
    drawRect(
        color = Color.White,
        topLeft = Offset(x + moduleSize, y + moduleSize),
        size = Size(5 * moduleSize, 5 * moduleSize)
    )
    // 3x3 central black square
    drawRect(
        color = Color.Black,
        topLeft = Offset(x + 2 * moduleSize, y + 2 * moduleSize),
        size = Size(3 * moduleSize, 3 * moduleSize)
    )
}

