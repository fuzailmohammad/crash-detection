package com.accident.detection.center.ui.navigation

sealed class Screen(val route: String) {
    data object SplashGuard : Screen("splash_guard")
    data object PermissionWizard : Screen("permission_wizard")
    data object ProfileSetup : Screen("profile_setup")
    data object EmergencyContacts : Screen("emergency_contacts")
    data object Dashboard : Screen("dashboard")
    data object MedicalBeacon : Screen("medical_beacon")
    data object CrashHistory : Screen("crash_history")
    data object SensorDiagnostics : Screen("sensor_diagnostics")
    data object ProfileAndContacts : Screen("profile_and_contacts")
}

