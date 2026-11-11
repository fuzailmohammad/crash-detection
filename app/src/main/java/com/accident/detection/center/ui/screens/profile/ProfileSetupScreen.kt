package com.accident.detection.center.ui.screens.profile

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
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accident.detection.center.domain.model.BloodGroup
import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.ui.theme.AlertRed
import com.accident.detection.center.ui.theme.DarkBackground
import com.accident.detection.center.ui.theme.DarkBorder
import com.accident.detection.center.ui.theme.DarkCard
import com.accident.detection.center.ui.theme.EmeraldGreen
import com.accident.detection.center.ui.theme.TextMuted
import com.accident.detection.center.ui.theme.TextPrimary
import com.accident.detection.center.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    viewModel: ProfileViewModel,
    onNavigateToContacts: () -> Unit
) {
    val fullName by viewModel.fullName.collectAsState()
    val bloodGroup by viewModel.bloodGroup.collectAsState()
    val allergies by viewModel.allergies.collectAsState()
    val medicalConditions by viewModel.medicalConditions.collectAsState()
    val medications by viewModel.medications.collectAsState()
    val organDonor by viewModel.organDonor.collectAsState()
    val selectedVehicleMode by viewModel.selectedVehicleMode.collectAsState()
    val isFormValid by viewModel.isFormValid.collectAsState()

    var bloodGroupExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Safety & Medical Profile",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Displayed on screen for first responders if you are found incapacitated, and sent in emergency crash telemetry reports.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Full Name
                item {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = viewModel::onFullNameChanged,
                        label = { Text("Full Legal Name *") },
                        placeholder = { Text("e.g. Jane Doe") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = EmeraldGreen)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkCard,
                            unfocusedContainerColor = DarkCard
                        ),
                        singleLine = true
                    )
                }

                // Blood Group Dropdown
                item {
                    ExposedDropdownMenuBox(
                        expanded = bloodGroupExpanded,
                        onExpandedChange = { bloodGroupExpanded = !bloodGroupExpanded }
                    ) {
                        OutlinedTextField(
                            value = bloodGroup.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Blood Group *") },
                            leadingIcon = {
                                Icon(Icons.Default.Bloodtype, contentDescription = null, tint = AlertRed)
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodGroupExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = DarkCard,
                                unfocusedContainerColor = DarkCard
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = bloodGroupExpanded,
                            onDismissRequest = { bloodGroupExpanded = false },
                            modifier = Modifier.background(DarkCard)
                        ) {
                            BloodGroup.entries.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.label, color = TextPrimary) },
                                    onClick = {
                                        viewModel.onBloodGroupChanged(group)
                                        bloodGroupExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Allergies & Chronic Conditions
                item {
                    OutlinedTextField(
                        value = allergies,
                        onValueChange = viewModel::onAllergiesChanged,
                        label = { Text("Known Allergies") },
                        placeholder = { Text("e.g. Penicillin, Latex, Peanuts (or None)") },
                        leadingIcon = {
                            Icon(Icons.Default.Medication, contentDescription = null, tint = TextMuted)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkCard,
                            unfocusedContainerColor = DarkCard
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = medicalConditions,
                        onValueChange = viewModel::onMedicalConditionsChanged,
                        label = { Text("Medical Conditions / Implants") },
                        placeholder = { Text("e.g. Asthma, Diabetes, Pacemaker") },
                        leadingIcon = {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = TextMuted)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkCard,
                            unfocusedContainerColor = DarkCard
                        )
                    )
                }

                // Organ Donor Switch
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCard)
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Registered Organ Donor",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Flags donor consent clearly on the Paramedic Beacon screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = organDonor,
                            onCheckedChange = viewModel::onOrganDonorChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldGreen,
                                checkedTrackColor = EmeraldGreen.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                // Vehicle Mode Header
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "VEHICLE DETECTION PROFILE",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldGreen
                    )
                    Text(
                        text = "Configures custom G-force impact thresholds & acoustic gating",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Vehicle Mode Selection Cards
                items(VehicleMode.entries.size) { index ->
                    val mode = VehicleMode.entries[index]
                    val isSelected = mode == selectedVehicleMode

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onVehicleModeChanged(mode) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) EmeraldGreen else DarkBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) EmeraldGreen.copy(alpha = 0.2f) else DarkBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = mode.icon,
                                    contentDescription = mode.displayName,
                                    tint = if (isSelected) EmeraldGreen else TextSecondary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Impact Threshold: ${mode.impactGThreshold}G | Speed Gate: >${mode.minSpeedGateKmh.toInt()} km/h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldGreen
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Button(
                onClick = {
                    viewModel.saveProfile {
                        onNavigateToContacts()
                    }
                },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "Continue to Emergency Contacts",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

