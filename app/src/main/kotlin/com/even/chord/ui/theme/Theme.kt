package com.even.chord.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colors from Flutter design
val Primary = Color(0xFF4285F4)
val PrimaryDark = Color(0xFF3367D6)
val White = Color(0xFFFFFFFF)
val Background = Color(0xFFFFFFFF)
val Surface = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1E1E1E)
val Error = Color(0xFFB00020)
val OnPrimary = Color(0xFFFFFFFF)
val OnBackground = Color(0xFF1E1E1E)
val OnSurface = Color(0xFF1E1E1E)
val Grey200 = Color(0xFFE0E0E0)
val Grey500 = Color(0xFF9E9E9E)
val Grey600 = Color(0xFF757575)
val Grey800 = Color(0xFF424242)
val Grey900 = Color(0xFF212121)
val Green = Color(0xFF4CAF50)
val Orange = Color(0xFFFF9800)
val Blue50 = Color(0xFFE3F2FD)
val Blue200 = Color(0xFF90CAF9)
val Blue700 = Color(0xFF1976D2)
val Blue900 = Color(0xFF0D47A1)
val Orange50 = Color(0xFFFFF3E0)
val Orange200 = Color(0xFFFFCC80)
val Orange700 = Color(0xFFF57C00)

// DriveSync Theme Colors
val DriveSyncDarkBackground = Color(0xFF0D110F)
val DriveSyncGreenAccent = Color(0xFF9BD49E)
val DriveSyncButtonGrey = Color(0xFF262926)
val DriveSyncTextWhite = Color(0xFFE0E3E0)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Blue50,
    onPrimaryContainer = Blue900,
    secondary = Primary,
    onSecondary = OnPrimary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    error = Error,
    onError = White,
    outline = Grey200,
    outlineVariant = Grey200,
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = SurfaceDark,
    onBackground = White,
    surface = SurfaceDark,
    onSurface = White,
    error = Error,
    onError = White,
)

@Composable
fun ChordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
