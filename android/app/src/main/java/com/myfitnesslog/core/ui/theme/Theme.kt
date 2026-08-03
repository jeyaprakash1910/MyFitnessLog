package com.myfitnesslog.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = brand_primary_light,
    onPrimary = brand_onPrimary_light,
    primaryContainer = brand_primaryContainer_light,
    onPrimaryContainer = brand_onPrimaryContainer_light,
    secondary = brand_secondary_light,
    onSecondary = brand_onSecondary_light,
    secondaryContainer = brand_secondaryContainer_light,
    onSecondaryContainer = brand_onSecondaryContainer_light,
    tertiary = brand_tertiary_light,
    onTertiary = brand_onTertiary_light,
    background = brand_background_light,
    onBackground = brand_onBackground_light,
    surface = brand_surface_light,
    onSurface = brand_onSurface_light,
    surfaceVariant = brand_surfaceVariant_light,
    onSurfaceVariant = brand_onSurfaceVariant_light,
    surfaceContainerLowest = brand_surfaceContainerLowest_light,
    surfaceContainerLow = brand_surfaceContainerLow_light,
    surfaceContainer = brand_surfaceContainer_light,
    surfaceContainerHigh = brand_surfaceContainerHigh_light,
    surfaceContainerHighest = brand_surfaceContainerHighest_light,
    outline = brand_outline_light,
    outlineVariant = brand_outlineVariant_light,
    error = brand_error_light,
    onError = brand_onError_light,
)

private val DarkColors = darkColorScheme(
    primary = brand_primary_dark,
    onPrimary = brand_onPrimary_dark,
    primaryContainer = brand_primaryContainer_dark,
    onPrimaryContainer = brand_onPrimaryContainer_dark,
    secondary = brand_secondary_dark,
    onSecondary = brand_onSecondary_dark,
    secondaryContainer = brand_secondaryContainer_dark,
    onSecondaryContainer = brand_onSecondaryContainer_dark,
    tertiary = brand_tertiary_dark,
    onTertiary = brand_onTertiary_dark,
    background = brand_background_dark,
    onBackground = brand_onBackground_dark,
    surface = brand_surface_dark,
    onSurface = brand_onSurface_dark,
    surfaceVariant = brand_surfaceVariant_dark,
    onSurfaceVariant = brand_onSurfaceVariant_dark,
    surfaceContainerLowest = brand_surfaceContainerLowest_dark,
    surfaceContainerLow = brand_surfaceContainerLow_dark,
    surfaceContainer = brand_surfaceContainer_dark,
    surfaceContainerHigh = brand_surfaceContainerHigh_dark,
    surfaceContainerHighest = brand_surfaceContainerHighest_dark,
    outline = brand_outline_dark,
    outlineVariant = brand_outlineVariant_dark,
    error = brand_error_dark,
    onError = brand_onError_dark,
)

/**
 * Root Material 3 theme wrapper for the application.
 *
 * The app currently ships a single, brand-owned dark theme. Dynamic (Material
 * You wallpaper) color is intentionally disabled so the green/teal brand palette
 * renders consistently across every device. The light palette is defined and
 * ready for a future light-mode pass - flip [darkTheme] to honor the system
 * setting when that work happens.
 */
@Composable
fun MyFitnessLogTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MyFitnessLogTypography,
        content = content,
    )
}
