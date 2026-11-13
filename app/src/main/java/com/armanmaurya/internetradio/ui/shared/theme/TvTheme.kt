package com.armanmaurya.internetradio.ui.shared.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.tv.material3.ColorScheme as TvColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import com.armanmaurya.internetradio.domain.model.AppPreferences
import androidx.compose.material3.ColorScheme as M3ColorScheme
import androidx.compose.material3.MaterialTheme as M3MaterialTheme

/**
 * TV theme. Always dark and always built from the app palette the user picked; wallpaper-based
 * dynamic colour is ignored because TVs don't provide a meaningful system palette.
 *
 * The same scheme is also provided to regular Material 3, so the few phone components used on
 * TV (progress indicators, dialogs, text fields) match.
 */
@Composable
fun TvInternetRadioTheme(
    appPreferences: AppPreferences = AppPreferences(),
    content: @Composable () -> Unit
) {
    val m3Scheme = remember(appPreferences.appColor, appPreferences.customColorArgb) {
        appPreferences.appColor.getColorScheme(
            darkTheme = true,
            customColorArgb = appPreferences.customColorArgb
        )
    }
    val tvScheme = remember(m3Scheme) { m3Scheme.toTvColorScheme() }

    M3MaterialTheme(colorScheme = m3Scheme, typography = Typography) {
        TvMaterialTheme(colorScheme = tvScheme, content = content)
    }
}

private fun M3ColorScheme.toTvColorScheme(): TvColorScheme = tvDarkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = surfaceTint,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    border = outline,
    borderVariant = outlineVariant,
    scrim = scrim
)
