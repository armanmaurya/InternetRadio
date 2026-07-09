package com.armanmaurya.internetradio.ui.shared.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.armanmaurya.internetradio.data.model.AppPreferences

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black
)

private val DarkColorSchemeStandard = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun InternetRadioTheme(
    appPreferences: AppPreferences = AppPreferences(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (appPreferences.themeMode) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val dynamicColor = appPreferences.useDynamicColor

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val baseScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && appPreferences.pureBlack) {
                baseScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainer = Color.Black,
                    surfaceContainerLow = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerHigh = Color.Black,
                    surfaceContainerHighest = Color.Black
                )
            } else {
                baseScheme
            }
        }

        darkTheme -> if (appPreferences.pureBlack) DarkColorScheme else DarkColorSchemeStandard
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}