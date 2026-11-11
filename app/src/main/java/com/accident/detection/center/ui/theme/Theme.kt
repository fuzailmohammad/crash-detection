package com.accident.detection.center.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    onPrimary = DarkBackground,
    primaryContainer = EmeraldGreenDark,
    onPrimaryContainer = TextPrimary,
    secondary = InfoCyan,
    onSecondary = DarkBackground,
    error = AlertRed,
    onError = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder
)

private val EmergencyAlarmColorScheme = darkColorScheme(
    primary = AlertRed,
    onPrimary = TextPrimary,
    background = AlertRedDark,
    surface = AlertRedDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun AccidentDetectionCenterTheme(
    darkTheme: Boolean = true, // Default to high-contrast tactical dark theme for maximum legibility
    isEmergencyMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isEmergencyMode -> EmergencyAlarmColorScheme
        else -> DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

