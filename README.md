# Accident Detection Center (100% Native Android)

A production-grade, life-safety Android application engineered with **Jetpack Compose (Material 3)**, **Kotlin Coroutines & Flow**, **Dagger Hilt**, **Room Database**, and **Jetpack DataStore**, targeting **Min SDK 26 (Android 8.0) through Target SDK 35 (Android 15)**.

## Core Architectural Pillars
1. **100% Native Kotlin Stack**: High-frequency sensor hardware sampling without cross-platform overhead or JNI bridge delays.
2. **Zero False Alarms**: 5-Stage Multi-Sensor Fusion Pipeline (GPS Velocity / Activity Gate -> Accelerometer G-Force Impact -> Gyroscope Rotational Spike -> Acoustic Decibel / Airbag Barometer Spike -> 5-Second Post-Impact Deceleration & Pedometer Stillness Verification).
3. **Three-State Survival**: Seamless background execution across Normal (Foreground), Background (Minimized), and Terminated (Swiped away or device rebooted) states with instant watchdog self-healing (`START_STICKY`, `onTaskRemoved`, `BOOT_COMPLETED`).
4. **30-Second Lockscreen Intercept**: Dual-Activity architecture (`MainActivity` & `EmergencyAlarmActivity`) that wakes the screen over the lockscreen, plays 100% volume `USAGE_ALARM` siren overriding DND, strobes camera torch, speaks TTS warnings, and presents an anti-pocket-tap "Slide to Cancel" slider alongside a "Send SOS Now" button.
5. **Automated Emergency Cascade**: Dispatches local system notifications, silent SMS with Google Maps coordinates via `SmsManager`, forensic crash report emails, and direct hands-free speakerphone phone calls (`ACTION_CALL`).

## Project Package Architecture
```
app/src/main/java/com/accident/detection/center/
├── CrashDetectionApp.kt               # @HiltAndroidApp with high-priority notification channels
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt             # Room Database v1
│   │   ├── Converters.kt              # Room type converters
│   │   ├── dao/
│   │   │   ├── EmergencyContactDao.kt # DAO for priority contacts
│   │   │   └── CrashEventLogDao.kt    # DAO for collision forensic telemetry
│   │   └── entity/
│   │       ├── EmergencyContactEntity.kt
│   │       └── CrashEventLogEntity.kt
│   ├── preferences/
│   │   └── UserPreferencesRepository.kt # DataStore repository for UserProfile & Shield
│   └── repository/
│       ├── CrashLogRepositoryImpl.kt
│       ├── EmergencyContactRepositoryImpl.kt
│       └── PermissionChecker.kt       # Live evaluator of 10 permissions & special access
├── di/
│   ├── DatabaseModule.kt              # Hilt module for Room DB & DAOs
│   └── RepositoryModule.kt            # Hilt bindings for repositories
├── domain/
│   ├── model/
│   │   ├── BloodGroup.kt              # Medical blood group enum
│   │   ├── CrashDetectionStage.kt     # 5-stage pipeline enum
│   │   ├── DispatchStatusState.kt     # Real-time cascade dispatch models
│   │   ├── PermissionHealthState.kt   # System hardening health model
│   │   ├── SensorTelemetryState.kt    # 100Hz IMU, GPS, Acoustic, and Crash Score
│   │   ├── UserProfile.kt             # Patient data & vehicle configuration
│   │   └── VehicleMode.kt             # Tailored thresholds (CAR, TWO_WHEELER, BICYCLE)
│   └── repository/
│       ├── CrashLogRepository.kt
│       └── EmergencyContactRepository.kt
├── service/
│   ├── AccidentDetectionService.kt    # High-priority Foreground Service (location|specialUse)
│   ├── BootCompletedReceiver.kt       # Device boot & app update auto-revive receiver
│   └── ServiceRestarterReceiver.kt    # onTaskRemoved watchdog restarter
└── ui/
    ├── MainActivity.kt                # Primary app entry point
    ├── components/
    │   └── SlideToCancelSlider.kt     # Anti-pocket-tap swipe gesture
    ├── navigation/
    │   ├── AppNavHost.kt              # Compose NavHost with live Startup Guard
    │   └── Screen.kt                  # Sealed routes
    ├── screens/
    │   ├── alarm/
    │   │   ├── EmergencyAlarmActivity.kt # Lockscreen override, siren, torch, and TTS
    │   │   ├── EmergencyAlarmScreen.kt   # 30s countdown, dispatch status & paramedic beacon
    │   │   └── EmergencyAlarmViewModel.kt
    │   ├── contacts/
    │   │   ├── EmergencyContactsScreen.kt
    │   │   └── EmergencyContactsViewModel.kt
    │   ├── dashboard/
    │   │   ├── CrashSimulationLabSheet.kt# Interactive false-alarm vs crash simulation
    │   │   ├── DashboardScreen.kt        # Master Shield, HUD, and Service Health
    │   │   └── DashboardViewModel.kt
    │   ├── onboarding/
    │   │   ├── PermissionViewModel.kt
    │   │   └── PermissionWizardScreen.kt # Multi-step hardening wizard with resume check
    │   └── profile/
    │       ├── MedicalBeaconScreen.kt    # Standalone Paramedic Medical ID card
    │       ├── ProfileSetupScreen.kt     # Medical and Vehicle setup
    │       └── ProfileViewModel.kt
    └── theme/
        ├── Color.kt                   # Emerald green, Alert red, high-contrast dark theme
        ├── Theme.kt                   # Material 3 dark color scheme & alarm scheme
        └── Type.kt                    # M3 Typography
```

