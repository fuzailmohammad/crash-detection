package com.accident.detection.center.domain.model

/**
 * Metadata descriptor for each required system permission or hardware capability.
 */
data class PermissionItem(
    val id: String,
    val title: String,
    val rationale: String,
    val isGranted: Boolean,
    val isSpecialAccess: Boolean = false,
    val androidLevelRequirement: String = "All"
)

/**
 * Health monitor for system permissions, overlay capabilities, and power policies.
 */
data class PermissionHealthState(
    val isFineLocationGranted: Boolean = false,
    val isBackgroundLocationGranted: Boolean = false,
    val isActivityRecognitionGranted: Boolean = false,
    val isRecordAudioGranted: Boolean = false,
    val isSendSmsGranted: Boolean = false,
    val isCallPhoneGranted: Boolean = false,
    val isPostNotificationsGranted: Boolean = false,
    val isSystemAlertWindowGranted: Boolean = false,
    val isFullScreenIntentGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isExactAlarmPermitted: Boolean = true
) {
    val areAllCorePermissionsGranted: Boolean
        get() = isFineLocationGranted &&
                isBackgroundLocationGranted &&
                isActivityRecognitionGranted &&
                isRecordAudioGranted &&
                isSendSmsGranted &&
                isCallPhoneGranted &&
                isPostNotificationsGranted

    val areSpecialSettingsGranted: Boolean
        get() = isSystemAlertWindowGranted &&
                isFullScreenIntentGranted &&
                isBatteryOptimizationIgnored &&
                isExactAlarmPermitted

    val isCompletelyHardened: Boolean
        get() = areAllCorePermissionsGranted && areSpecialSettingsGranted

    val permissionItems: List<PermissionItem>
        get() = listOf(
            PermissionItem(
                id = "LOCATION",
                title = "Precise & Background Location",
                rationale = "Required for speed gating (>20 km/h) and generating live Google Maps coordinates for rescue teams.",
                isGranted = isFineLocationGranted && isBackgroundLocationGranted,
                isSpecialAccess = false,
                androidLevelRequirement = "All ('Allow all the time')"
            ),
            PermissionItem(
                id = "ACTIVITY",
                title = "Physical Activity Recognition",
                rationale = "Detects when you are driving, cycling, or stationary to prevent high-frequency battery drain when parked.",
                isGranted = isActivityRecognitionGranted,
                isSpecialAccess = false,
                androidLevelRequirement = "Android 10+"
            ),
            PermissionItem(
                id = "AUDIO",
                title = "Acoustic Airbag & Crash Decibel Sensor",
                rationale = "Measures peak acoustic decibels (dB) during impact to correlate structural metal deformation and airbag pops.",
                isGranted = isRecordAudioGranted,
                isSpecialAccess = false,
                androidLevelRequirement = "All"
            ),
            PermissionItem(
                id = "SMS",
                title = "Automated Silent SOS SMS",
                rationale = "Sends immediate SMS with live GPS Google Maps links directly through device telephony if unresponsive.",
                isGranted = isSendSmsGranted,
                isSpecialAccess = false,
                androidLevelRequirement = "All"
            ),
            PermissionItem(
                id = "CALL",
                title = "Emergency Speakerphone Calling",
                rationale = "Places direct hands-free emergency speakerphone calls to your primary contact upon crash confirmation.",
                isGranted = isCallPhoneGranted,
                isSpecialAccess = false,
                androidLevelRequirement = "All"
            ),
            PermissionItem(
                id = "NOTIFICATIONS",
                title = "Critical Alerts & Persistent Foreground Service",
                rationale = "Maintains persistent system priority and displays real-time monitoring HUD status.",
                isGranted = isPostNotificationsGranted,
                isSpecialAccess = false,
                androidLevelRequirement = "Android 13+"
            ),
            PermissionItem(
                id = "SYSTEM_ALERT_WINDOW",
                title = "Draw Over Other Apps (Overlay)",
                rationale = "Allows the high-contrast countdown alarm to launch immediately over any active navigation or music app.",
                isGranted = isSystemAlertWindowGranted,
                isSpecialAccess = true,
                androidLevelRequirement = "Special App Access"
            ),
            PermissionItem(
                id = "FULL_SCREEN_INTENT",
                title = "Full-Screen Lockscreen Intercept",
                rationale = "Wakes the screen and overrides the lockscreen when an accident is detected while phone is in pocket or mount.",
                isGranted = isFullScreenIntentGranted,
                isSpecialAccess = true,
                androidLevelRequirement = "Android 14+"
            ),
            PermissionItem(
                id = "BATTERY_OPTIMIZATION",
                title = "Unrestricted Battery Background Execution",
                rationale = "Exempts the sensor fusion engine from OEM battery killers and Android Doze termination during highway driving.",
                isGranted = isBatteryOptimizationIgnored,
                isSpecialAccess = true,
                androidLevelRequirement = "Battery Whitelist"
            ),
            PermissionItem(
                id = "EXACT_ALARM",
                title = "Exact Alarm Watchdog Wake-Up",
                rationale = "Allows instant self-healing revival within 1 second if the app is swiped away from the Recents tray.",
                isGranted = isExactAlarmPermitted,
                isSpecialAccess = true,
                androidLevelRequirement = "Android 12+"
            )
        )

    val missingPermissionsCount: Int
        get() = permissionItems.count { !it.isGranted }
}
