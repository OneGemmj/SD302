package com.seedream.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Unified corner radius scale. Material 3 maps every component to one of
 * these buckets, so to keep the whole UI consistent the buckets are aligned:
 *  - extraSmall (8dp):  thumbnails and small clipped media
 *  - small (12dp):      text fields, dropdown menus (M3 default for these)
 *  - medium (12dp):     cards
 *  - large (12dp):      filled/outlined buttons (M3 default) and status card
 *  - extraLarge (16dp): dialogs and sheets (M3 default)
 *
 * With small == medium == large == 12dp, every input box, dropdown, button and
 * card shares one radius, so model dropdowns, text fields, the prompt box and
 * the advanced-parameter fields all look consistent instead of mixing 4dp
 * text fields with 20dp pill buttons.
 */
val SeedreamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

/**
 * Material 3 theme with a manual light/dark override on top of "follow system":
 * themeMode "system" follows the system setting, "light" forces light,
 * "dark" forces dark. Android 12+ uses Material You dynamic color (KernelSU
 * style); older devices fall back to the brand green palettes.
 */
@Composable
fun SeedreamTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SeedreamTypography,
        shapes = SeedreamShapes,
        content = content
    )
}
