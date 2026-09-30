package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonMagenta,
    onPrimary = Color.White,
    primaryContainer = HolographicViolet,
    onPrimaryContainer = Color.White,
    secondary = ElectricCyan,
    onSecondary = Color.Black,
    secondaryContainer = CyberSurfaceVariant,
    onSecondaryContainer = ElectricCyan,
    tertiary = HolographicViolet,
    background = CyberDarkBg,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CyberCardBorder
  )

@Composable
fun MahiAiTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}

