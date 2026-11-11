package com.accident.detection.center.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.accident.detection.center.ui.screens.contacts.EmergencyContactsScreen
import com.accident.detection.center.ui.screens.contacts.EmergencyContactsViewModel
import com.accident.detection.center.ui.screens.dashboard.DashboardScreen
import com.accident.detection.center.ui.screens.dashboard.DashboardViewModel
import com.accident.detection.center.ui.screens.history.CrashHistoryScreen
import com.accident.detection.center.ui.screens.history.CrashHistoryViewModel
import com.accident.detection.center.ui.screens.onboarding.PermissionViewModel
import com.accident.detection.center.ui.screens.onboarding.PermissionWizardScreen
import com.accident.detection.center.ui.screens.profile.MedicalBeaconScreen
import com.accident.detection.center.ui.screens.profile.ProfileAndContactsScreen
import com.accident.detection.center.ui.screens.profile.ProfileSetupScreen
import com.accident.detection.center.ui.screens.profile.ProfileViewModel
import com.accident.detection.center.ui.screens.diagnostics.SensorDiagnosticsScreen
import com.accident.detection.center.ui.screens.diagnostics.SensorDiagnosticsViewModel
import com.accident.detection.center.ui.theme.DarkBackground
import com.accident.detection.center.ui.theme.EmeraldGreen

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.SplashGuard.route,
        enterTransition = { fadeIn(animationSpec = tween(250)) },
        exitTransition = { fadeOut(animationSpec = tween(250)) }
    ) {
        // Startup Guard route that evaluates live system state before routing
        composable(Screen.SplashGuard.route) {
            val permissionViewModel: PermissionViewModel = hiltViewModel()
            val profileViewModel: ProfileViewModel = hiltViewModel()
            val contactsViewModel: EmergencyContactsViewModel = hiltViewModel()

            val permissionState by permissionViewModel.permissionState.collectAsState()
            val userProfile by profileViewModel.savedProfile.collectAsState()
            val contactsCount by contactsViewModel.contactCount.collectAsState()

            LaunchedEffect(permissionState, userProfile, contactsCount) {
                // If core permissions are missing, force hardening wizard
                if (!permissionState.areAllCorePermissionsGranted) {
                    navController.navigate(Screen.PermissionWizard.route) {
                        popUpTo(Screen.SplashGuard.route) { inclusive = true }
                    }
                } else if (!userProfile.isProfileCompleted) {
                    // Force medical profile setup
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.SplashGuard.route) { inclusive = true }
                    }
                } else if (contactsCount < 1) {
                    // Force at least 1 emergency contact
                    navController.navigate(Screen.EmergencyContacts.route) {
                        popUpTo(Screen.SplashGuard.route) { inclusive = true }
                    }
                } else {
                    // System fully prepared -> Launch Command Center
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.SplashGuard.route) { inclusive = true }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EmeraldGreen)
            }
        }

        // Screen 1: System Hardening Onboarding Wizard
        composable(Screen.PermissionWizard.route) {
            val permissionViewModel: PermissionViewModel = hiltViewModel()
            PermissionWizardScreen(
                viewModel = permissionViewModel,
                onAllPermissionsGranted = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.PermissionWizard.route) { inclusive = true }
                    }
                }
            )
        }

        // Screen 2: Mandatory Safety Profile
        composable(Screen.ProfileSetup.route) {
            val profileViewModel: ProfileViewModel = hiltViewModel()
            ProfileSetupScreen(
                viewModel = profileViewModel,
                onNavigateToContacts = {
                    navController.navigate(Screen.EmergencyContacts.route)
                }
            )
        }

        // Screen 3: Emergency Contacts Gate
        composable(Screen.EmergencyContacts.route) {
            val contactsViewModel: EmergencyContactsViewModel = hiltViewModel()
            EmergencyContactsScreen(
                viewModel = contactsViewModel,
                onCompleteOnboarding = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.PermissionWizard.route) { inclusive = true }
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                        popUpTo(Screen.EmergencyContacts.route) { inclusive = true }
                    }
                }
            )
        }

        // Screen 4: Main Command Center Dashboard
        composable(Screen.Dashboard.route) {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToProfile = {
                    navController.navigate(Screen.ProfileAndContacts.route)
                },
                onNavigateToContacts = {
                    navController.navigate(Screen.EmergencyContacts.route)
                },
                onNavigateToBeacon = {
                    navController.navigate(Screen.MedicalBeacon.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.CrashHistory.route)
                },
                onNavigateToDiagnostics = {
                    navController.navigate(Screen.SensorDiagnostics.route)
                }
            )
        }

        // Screen 5: Standalone First-Responder Medical Beacon
        composable(Screen.MedicalBeacon.route) {
            val profileViewModel: ProfileViewModel = hiltViewModel()
            val contactsViewModel: EmergencyContactsViewModel = hiltViewModel()
            MedicalBeaconScreen(
                profileViewModel = profileViewModel,
                contactsViewModel = contactsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // Screen 6: Crash & Simulation Incident Audit Logs
        composable(Screen.CrashHistory.route) {
            val historyViewModel: CrashHistoryViewModel = hiltViewModel()
            CrashHistoryScreen(
                viewModel = historyViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // Screen 7: IMU Hardware Oscilloscope & Sensor Calibration Diagnostics
        composable(Screen.SensorDiagnostics.route) {
            val diagnosticsViewModel: SensorDiagnosticsViewModel = hiltViewModel()
            SensorDiagnosticsScreen(
                viewModel = diagnosticsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // Screen 8: Unified Profile & Contacts Management
        composable(Screen.ProfileAndContacts.route) {
            val profileViewModel: ProfileViewModel = hiltViewModel()
            val contactsViewModel: EmergencyContactsViewModel = hiltViewModel()
            ProfileAndContactsScreen(
                profileViewModel = profileViewModel,
                contactsViewModel = contactsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
