package com.aetheris.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AetherisPrimary,
    onPrimary = Color.White,
    primaryContainer = AetherisPrimaryDark,
    onPrimaryContainer = AetherisPrimaryLight,
    secondary = AetherisSecondary,
    onSecondary = Color.White,
    secondaryContainer = AetherisSecondaryDark,
    onSecondaryContainer = AetherisSecondaryLight,
    tertiary = AetherisTertiary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFF2A2A3A)
)

private val LightColorScheme = lightColorScheme(
    primary = AetherisPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = AetherisPrimaryDark,
    secondary = AetherisSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = AetherisSecondaryDark,
    tertiary = AetherisTertiary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFFD1D5DB)
)

@Composable
fun AetherisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AetherisTypography,
        content = content
    )
}
