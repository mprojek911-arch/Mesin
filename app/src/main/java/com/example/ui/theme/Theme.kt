package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00363F),
    onPrimaryContainer = Color(0xFFB5F4FF),
    secondary = NeonPurple,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF33144C),
    onSecondaryContainer = Color(0xFFEEDBFF),
    tertiary = NeonAmber,
    onTertiary = Color.Black,
    background = StudioDarkBg,
    onBackground = TextPrimary,
    surface = StudioCardBg,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioCardBorder,
    error = StatusError,
    onError = Color.White
)

private val LightColorScheme = darkColorScheme( // Keep studio dark feel for DJ app
    primary = NeonCyanSubtle,
    onPrimary = Color.Black,
    background = StudioDarkBg,
    surface = StudioCardBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // DJ Slow studio aesthetic is dark by default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
