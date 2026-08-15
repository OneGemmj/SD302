package com.seedream.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand green (KernelSU-inspired Material accent).
private val Green40 = Color(0xFF2E7D32)
private val Green80 = Color(0xFF81C784)
private val GreenGrey40 = Color(0xFF54704A)
private val GreenGrey80 = Color(0xFFB5CCAA)

// Light surface neutrals.
private val LightBackground = Color(0xFFFBFDF8)
private val LightSurface = Color(0xFFF6F9F1)
private val LightSurfaceVariant = Color(0xFFE9EFE2)
private val LightOnBackground = Color(0xFF1A1C19)
private val LightOnSurface = Color(0xFF1A1C19)
private val LightOnSurfaceVariant = Color(0xFF44483F)
private val LightPrimaryContainer = Color(0xFFC4ECC4)
private val LightOnPrimaryContainer = Color(0xFF0A1E0A)
private val LightSecondary = Color(0xFF607D8B)
private val LightTertiary = Color(0xFFF59E0B)
private val LightError = Color(0xFFBA1A1A)

// Dark surface neutrals.
private val DarkBackground = Color(0xFF101410)
private val DarkSurface = Color(0xFF141814)
private val DarkSurfaceVariant = Color(0xFF232823)
private val DarkOnBackground = Color(0xFFE2E4DE)
private val DarkOnSurface = Color(0xFFE2E4DE)
private val DarkOnSurfaceVariant = Color(0xFFC2C8BA)
private val DarkPrimaryContainer = Color(0xFF1F3A20)
private val DarkOnPrimaryContainer = Color(0xFFC4ECC4)
private val DarkSecondary = Color(0xFF9FB8C7)
private val DarkTertiary = Color(0xFFF2B04C)
private val DarkError = Color(0xFFFFB4AB)

val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = Color.White,
    tertiary = LightTertiary,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = LightError,
    onError = Color.White
)

val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Color(0xFF0A1E0A),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = Color(0xFF1A1C19),
    tertiary = DarkTertiary,
    onTertiary = Color(0xFF3E2E00),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    onError = Color(0xFF690005)
)
