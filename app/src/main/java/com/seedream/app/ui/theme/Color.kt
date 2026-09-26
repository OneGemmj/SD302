package com.seedream.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Palette modelled on the DeepSeek app: one saturated brand blue holding the
 * entire accent budget, and everything else measured in neutral greys so the
 * content — prompts, reference images, results — carries the hierarchy instead
 * of the chrome.
 *
 * Every surface slot is spelled out on purpose. Material 3 fills anything left
 * unspecified from its own baseline palette, which would leak purple-grey
 * containers into the top bar, navigation bar and dialogs.
 */

// Brand: DeepSeek blue. One colour, used for the active tab, primary actions
// and any selected state.
private val Blue = Color(0xFF4D6BFE)
private val BlueSoft = Color(0xFFEDF1FF)   // selected chip / user bubble on white
private val BlueSoftDark = Color(0xFF232A47) // the same role on near-black

// Light neutrals.
private val LBackground = Color(0xFFFFFFFF)
private val LSurface = Color(0xFFF5F6F8)
private val LSurfaceHigh = Color(0xFFFFFFFF)
private val LSurfaceLow = Color(0xFFFAFAFB)
private val LSurfaceVariant = Color(0xFFEEF0F3)
private val LOutline = Color(0xFFE5E7EB)
private val LOutlineVariant = Color(0xFFEDEFF2)
private val LOnSurface = Color(0xFF1A1A1A)
private val LOnSurfaceVariant = Color(0xFF8A8F98)

// Dark neutrals: DeepSeek's dark mode is near-black, not grey-slate.
private val DBackground = Color(0xFF191919)
private val DSurface = Color(0xFF242424)
private val DSurfaceHigh = Color(0xFF2E2E2E)
private val DSurfaceLow = Color(0xFF1F1F1F)
private val DSurfaceVariant = Color(0xFF2C2C2E)
private val DOutline = Color(0xFF33363B)
private val DOutlineVariant = Color(0xFF2A2C30)
private val DOnSurface = Color(0xFFEDEDED)
private val DOnSurfaceVariant = Color(0xFF9096A0)

private val Amber = Color(0xFFF59E0B)
private val LError = Color(0xFFE5484D)
private val DError = Color(0xFFFF6369)

val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueSoft,
    onPrimaryContainer = Color(0xFF1B2A6B),
    inversePrimary = Color(0xFF9DB0FF),

    secondary = Color(0xFF5B6472),
    onSecondary = Color.White,
    secondaryContainer = LSurfaceVariant,
    onSecondaryContainer = LOnSurface,

    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF3DD),
    onTertiaryContainer = Color(0xFF6B4708),

    background = LBackground,
    onBackground = LOnSurface,
    surface = LSurface,
    onSurface = LOnSurface,
    surfaceVariant = LSurfaceVariant,
    onSurfaceVariant = LOnSurfaceVariant,
    surfaceTint = Blue,

    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = Color(0xFFF5F5F5),

    error = LError,
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF7A1719),

    outline = LOutline,
    outlineVariant = LOutlineVariant,
    scrim = Color(0x99000000),

    surfaceBright = Color.White,
    surfaceDim = Color(0xFFEDEEF0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LSurfaceLow,
    surfaceContainer = LSurface,
    surfaceContainerHigh = LSurfaceHigh,
    surfaceContainerHighest = Color(0xFFF0F1F4)
)

val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueSoftDark,
    onPrimaryContainer = Color(0xFFC7D2FF),
    inversePrimary = Color(0xFF3A50C9),

    secondary = Color(0xFFA9B0BC),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = DSurfaceVariant,
    onSecondaryContainer = DOnSurface,

    tertiary = Color(0xFFF2B04C),
    onTertiary = Color(0xFF3E2E00),
    tertiaryContainer = Color(0xFF3D2E10),
    onTertiaryContainer = Color(0xFFFFDFA8),

    background = DBackground,
    onBackground = DOnSurface,
    surface = DSurface,
    onSurface = DOnSurface,
    surfaceVariant = DSurfaceVariant,
    onSurfaceVariant = DOnSurfaceVariant,
    surfaceTint = Blue,

    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF1A1A1A),

    error = DError,
    onError = Color(0xFF4A0B0D),
    errorContainer = Color(0xFF3A1416),
    onErrorContainer = Color(0xFFFFD2D3),

    outline = DOutline,
    outlineVariant = DOutlineVariant,
    scrim = Color(0xCC000000),

    surfaceBright = Color(0xFF333333),
    surfaceDim = Color(0xFF141414),
    surfaceContainerLowest = Color(0xFF121212),
    surfaceContainerLow = DSurfaceLow,
    surfaceContainer = DSurface,
    surfaceContainerHigh = DSurfaceHigh,
    surfaceContainerHighest = Color(0xFF383838)
)
