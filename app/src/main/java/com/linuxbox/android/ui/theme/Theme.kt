package com.linuxbox.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object LinuxBoxColors {
    val Background = Color(0xFF0B0F14)
    val Surface = Color(0xFF111821)
    val SurfaceElevated = Color(0xFF17212C)
    val Border = Color(0xFF243242)
    val Primary = Color(0xFF22C55E)
    val PrimaryDim = Color(0xFF14532D)
    val Secondary = Color(0xFF38BDF8)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFEF4444)
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B99A8)
}

private val LinuxBoxDarkScheme = darkColorScheme(
    primary = LinuxBoxColors.Primary,
    onPrimary = Color(0xFF041309),
    primaryContainer = LinuxBoxColors.PrimaryDim,
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = LinuxBoxColors.Secondary,
    onSecondary = Color(0xFF041520),
    background = LinuxBoxColors.Background,
    onBackground = LinuxBoxColors.TextPrimary,
    surface = LinuxBoxColors.Surface,
    onSurface = LinuxBoxColors.TextPrimary,
    surfaceVariant = LinuxBoxColors.SurfaceElevated,
    onSurfaceVariant = LinuxBoxColors.TextSecondary,
    outline = LinuxBoxColors.Border,
    error = LinuxBoxColors.Danger,
    onError = Color.White
)

@Composable
fun LinuxBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LinuxBoxDarkScheme,
        content = content
    )
}
