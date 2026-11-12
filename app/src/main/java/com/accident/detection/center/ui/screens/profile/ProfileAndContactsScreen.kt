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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import com.accident.detection.center.domain.model.BloodGroup
import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.ui.screens.contacts.EmergencyContactsViewModel
import com.accident.detection.center.ui.theme.AlertRed
import com.accident.detection.center.ui.theme.DarkBackground
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
 * Unified Safety Configuration Screen:
 * Tab 1: User Profile, Paramedic Medical ID & Vehicle Mode (DataStore)
 * Tab 2: Priority Emergency Contacts Management (Room DB)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAndContactsScreen(
    profileViewModel: ProfileViewModel,
    contactsViewModel: EmergencyContactsViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Safety Profile & Medical ID", "Emergency Contacts")

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Safety & Emergency Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurface,
                    contentColor = EmeraldGreen,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = EmeraldGreen
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (selectedTab == index) EmeraldGreen else TextSecondary
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> ProfileAndMedicalTab(viewModel = profileViewModel, onSaved = onBack)
                1 -> ContactsManagementTab(viewModel = contactsViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileAndMedicalTab(
    viewModel: ProfileViewModel,
    onSaved: () -> Unit
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Full Name
        item {
            OutlinedTextField(
                value = fullName,
                onValueChange = viewModel::onFullNameChanged,
                label = { Text("Driver / User Full Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
        }

        // Blood Group Selector
        item {
            ExposedDropdownMenuBox(
                expanded = bloodGroupExpanded,
                onExpandedChange = { bloodGroupExpanded = !bloodGroupExpanded }
            ) {
                OutlinedTextField(
                    value = bloodGroup.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Blood Group (Paramedic Critical)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodGroupExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = textFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = bloodGroupExpanded,
                    onDismissRequest = { bloodGroupExpanded = false },
                    modifier = Modifier.background(DarkCard)
                ) {
                    BloodGroup.values().forEach { bg ->
                        DropdownMenuItem(
                            text = { Text(bg.label, color = TextPrimary) },
                            onClick = {
                                viewModel.onBloodGroupChanged(bg)
                                bloodGroupExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Allergies
        item {
            OutlinedTextField(
                value = allergies,
                onValueChange = viewModel::onAllergiesChanged,
                label = { Text("Allergies (e.g. Penicillin, Peanuts, Latex)") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
        }

        // Medical Conditions
        item {
            OutlinedTextField(
                value = medicalConditions,
                onValueChange = viewModel::onMedicalConditionsChanged,
                label = { Text("Medical Conditions (e.g. Asthma, Diabetes)") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
        }

        // Medications
        item {
            OutlinedTextField(
                value = medications,
                onValueChange = viewModel::onMedicationsChanged,
                label = { Text("Current Medications") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
        }

        // Organ Donor Switch
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Registered Organ Donor", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Surfaced on Paramedic Medical ID Beacon", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = organDonor,
                        onCheckedChange = viewModel::onOrganDonorChanged,
                        colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen, checkedTrackColor = EmeraldGreen.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // Vehicle Mode Selector
        item {
            Text(
                text = "PRIMARY VEHICLE MODE (CALIBRATES G-FORCE THRESHOLDS)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VehicleMode.values().forEach { mode ->
                    val isSelected = mode == selectedVehicleMode
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.onVehicleModeChanged(mode) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) EmeraldGreen.copy(alpha = 0.15f) else DarkCard
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) EmeraldGreen else DarkBorder
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = null,
                                tint = if (isSelected) EmeraldGreen else TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) EmeraldGreen else TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Save Button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.saveProfile(onSuccess = onSaved)
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Profile & Thresholds", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.Black)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ContactsManagementTab(
    viewModel: EmergencyContactsViewModel
) {
    val contacts by viewModel.contacts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EMERGENCY CONTACTS (${contacts.size}/5)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted
                )
                if (contacts.size < 5) {
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Contact", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                    }
                }
            }
        }

        if (contacts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Emergency Contacts Added",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AlertRed
                        )
                        Text(
                            text = "At least 1 emergency contact is required to receive automated SOS SMS and phone calls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        items(contacts) { contact ->
            EmergencyContactCard(
                contact = contact,
                onDelete = { viewModel.deleteContact(contact) }
            )
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            nextPriority = contacts.size + 1,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone, email, isSms, isCall, isEmail ->
                viewModel.addContact(
                    name = name,
                    phone = phone,
                    email = email,
                    relationship = "Emergency Contact",
                    isCallEnabled = isCall,
                    isSmsEnabled = isSms,
                    isEmailEnabled = isEmail
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun EmergencyContactCard(
    contact: EmergencyContactEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(EmeraldGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "#${contact.priority}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = contact.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Text(text = contact.phoneNumber, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        if (contact.email.isNotBlank()) {
                            Text(text = contact.email, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Channel Badges
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (contact.isSmsEnabled) ChannelActiveChip("SMS", EmeraldGreen)
                if (contact.isCallEnabled) ChannelActiveChip("CALL (Priority ${contact.priority})", InfoCyan)
                if (contact.isEmailEnabled) ChannelActiveChip("EMAIL", WarningAmber)
            }
        }
    }
}

@Composable
private fun ChannelActiveChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
private fun AddContactDialog(
    nextPriority: Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, email: String, isSms: Boolean, isCall: Boolean, isEmail: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isSms by remember { mutableStateOf(true) }
    var isCall by remember { mutableStateOf(true) }
    var isEmail by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Emergency Contact", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional for Crash Report)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSms, onCheckedChange = { isSms = it }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
                    Text("Enable Silent SMS Dispatch", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isCall, onCheckedChange = { isCall = it }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
                    Text("Enable Emergency Calling", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isEmail, onCheckedChange = { isEmail = it }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
                    Text("Enable Crash Report Email", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, phone, email, isSms, isCall, isEmail) },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkCard
    )
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EmeraldGreen,
    unfocusedBorderColor = DarkBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = EmeraldGreen,
    unfocusedLabelColor = TextSecondary,
    cursorColor = EmeraldGreen
)

