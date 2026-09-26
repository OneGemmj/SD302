package com.seedream.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Corner radius scale, following the DeepSeek app: generous radii on anything
 * that reads as a container (the compose box, cards, dialogs) and tighter ones
 * on small media, so the interface looks soft without turning every chip into
 * a pill.
 *
 *  - extraSmall (10dp): thumbnails and clipped media
 *  - small (12dp):      text fields, dropdown menus, list rows
 *  - medium (16dp):     cards and panels
 *  - large (20dp):      filled/outlined buttons and the status strip
 *  - extraLarge (26dp): dialogs, the prompt composer, sheets
 */
val SeedreamShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

/**
 * Material 3 theme with a manual light/dark override on top of "follow system":
 * themeMode "system" follows the system setting, "light" forces light,
 * "dark" forces dark.
 *
 * Dynamic colour is deliberately off. Material You would repaint the whole app
 * from the device wallpaper and throw away the DeepSeek blue that the rest of
 * the design is built around.
 */
@Composable
fun SeedreamTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SeedreamTypography,
        shapes = SeedreamShapes,
        content = content
    )
}
