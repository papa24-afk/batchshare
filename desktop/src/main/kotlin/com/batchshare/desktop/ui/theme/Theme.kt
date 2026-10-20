package com.batchshare.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Slate950 = Color(0xFF020617)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate400 = Color(0xFF94A3B8)
val Slate200 = Color(0xFFE2E8F0)
val Slate50 = Color(0xFFF8FAFC)

val PrimaryBlue = Color(0xFF2563EB)
val PrimaryBlueLight = Color(0xFF3B82F6)
val AccentCyan = Color(0xFF06B6D4)
val DangerRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF10B981)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlue,
    onPrimaryContainer = Color.White,
    surface = Slate900,
    onSurface = Slate50,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    background = Slate950,
    onBackground = Slate50,
    outline = Slate700,
    outlineVariant = Slate800,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun BatchShareDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
