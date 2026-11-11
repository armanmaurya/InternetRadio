package com.armanmaurya.internetradio.ui.shared.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.armanmaurya.internetradio.R
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

enum class AppColor(
    @StringRes val titleRes: Int,
    val previewColor: Color
) {
    MONOCHROME(
        titleRes = R.string.settings_color_monochrome,
        previewColor = Color.Black
    ),
    PURPLE(
        titleRes = R.string.settings_color_purple,
        previewColor = Color(0xFF7B1FA2)
    ),
    BLUE(
        titleRes = R.string.settings_color_blue,
        previewColor = Color(0xFF0066CC)
    ),
    TEAL(
        titleRes = R.string.settings_color_teal,
        previewColor = Color(0xFF00796B)
    ),
    GREEN(
        titleRes = R.string.settings_color_green,
        previewColor = Color(0xFF2E7D32)
    ),
    YELLOW(
        titleRes = R.string.settings_color_yellow,
        previewColor = Color(0xFFB26A00)
    ),
    PINK(
        titleRes = R.string.settings_color_pink,
        previewColor = Color(0xFFE91E63)
    ),
    RED(
        titleRes = R.string.settings_color_red,
        previewColor = Color(0xFFC62828)
    ),
    CUSTOM(
        titleRes = R.string.settings_color_custom,
        previewColor = Color(0xFF00BCD4)
    );

    private val defaultLightScheme: ColorScheme by lazy {
        if (this == MONOCHROME) {
            monochromeLightScheme
        } else {
            dynamicColorScheme(
                seedColor = previewColor,
                isDark = false,
                style = PaletteStyle.Vibrant
            )
        }
    }

    private val defaultDarkScheme: ColorScheme by lazy {
        if (this == MONOCHROME) {
            monochromeDarkScheme
        } else {
            dynamicColorScheme(
                seedColor = previewColor,
                isDark = true,
                style = PaletteStyle.Vibrant
            )
        }
    }

    fun getLightColorScheme(): ColorScheme = defaultLightScheme

    fun getDarkColorScheme(): ColorScheme = defaultDarkScheme

    fun getColorScheme(darkTheme: Boolean, customColorArgb: Int? = null): ColorScheme {
        if (this == CUSTOM && customColorArgb != null) {
            // Greyscale seeds have no usable hue, so MaterialKolor invents one and tints the
            // palette (pink for black, cyan for white). Treat them as Monochrome instead.
            if (isGreyscale(Color(customColorArgb))) {
                return if (darkTheme) monochromeDarkScheme else monochromeLightScheme
            }
            return dynamicColorScheme(
                seedColor = Color(customColorArgb),
                isDark = darkTheme,
                style = PaletteStyle.Vibrant
            )
        }
        return if (darkTheme) defaultDarkScheme else defaultLightScheme
    }

    fun getPreviewColors(darkTheme: Boolean, customColorArgb: Int? = null): List<Color> {
        val scheme = getColorScheme(darkTheme, customColorArgb)
        return listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer)
    }
}

/** Max spread between RGB channels (0..1) for a colour to count as grey/black/white. */
private const val GREYSCALE_CHANNEL_TOLERANCE = 0.08f

internal fun isGreyscale(color: Color): Boolean {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    return max - min <= GREYSCALE_CHANNEL_TOLERANCE
}

/**
 * Black-and-white scheme: pure black background with white components in dark mode,
 * pure white background with black components in light mode. No accent hue.
 * Dark mode keeps page backgrounds pure black and lifts cards/list items to dark greys
 * so they stay visible.
 */
private val monochromeDarkScheme: ColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color.White,
    inversePrimary = Color.Black,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF333333),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFE7E9EA),
    surface = Color.Black,
    onSurface = Color(0xFFE7E9EA),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFF8B8F93),
    surfaceTint = Color.Black,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    outline = Color(0xFF5C5C5C),
    outlineVariant = Color(0xFF2F3336),
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF242424)
)

private val monochromeLightScheme: ColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E6),
    onPrimaryContainer = Color.Black,
    inversePrimary = Color.White,
    secondary = Color(0xFF424242),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF616161),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E6E6),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color(0xFF0F1419),
    surface = Color.White,
    onSurface = Color(0xFF0F1419),
    surfaceVariant = Color(0xFFEFF1F2),
    onSurfaceVariant = Color(0xFF536471),
    surfaceTint = Color.White,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    outline = Color(0xFF9E9E9E),
    outlineVariant = Color(0xFFE1E1E1),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE6E6E6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFEFEFEF),
    surfaceContainerHighest = Color(0xFFE8E8E8)
)

// Custom colours are hue-only: saturation and brightness are fixed so every custom seed
// produces a clearly coloured palette (black/white/grey are covered by MONOCHROME).
private const val CUSTOM_SATURATION = 0.75f
private const val CUSTOM_BRIGHTNESS = 0.8f

/** Hue used when no valid custom hue exists (cyan, matches [AppColor.CUSTOM]'s preview). */
const val DEFAULT_CUSTOM_HUE = 187f

/** The custom seed colour (ARGB) for [hue] in degrees. */
fun customColorFromHue(hue: Float): Int =
    Color.hsv(((hue % 360f) + 360f) % 360f, CUSTOM_SATURATION, CUSTOM_BRIGHTNESS).toArgb()

/**
 * Hue of [argb] in degrees (0..360), or null when the colour is greyscale
 * (red, green and blue nearly equal), i.e. it has no meaningful hue.
 */
fun hueOfColor(argb: Int): Float? {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta <= GREYSCALE_TOLERANCE) return null
    val d = delta.toFloat()
    val hue = when (max) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    return if (hue < 0f) hue + 360f else hue
}

private const val GREYSCALE_TOLERANCE = 20
