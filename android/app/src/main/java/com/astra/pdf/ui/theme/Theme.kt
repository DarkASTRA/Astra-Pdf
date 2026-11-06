package com.astra.pdf.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AstraVioletPrimary,
    onPrimary = AstraWhite,
    secondary = AstraIndigo,
    onSecondary = AstraWhite,
    tertiary = AstraLilacAccent,
    background = AstraNavyDark,
    onBackground = AstraWhite,
    surface = AstraNavySurface,
    onSurface = AstraWhite,
    surfaceVariant = AstraNavySurfaceVariant,
    onSurfaceVariant = AstraLilacAccent,
    outline = AstraBorder
)

private val LightColorScheme = lightColorScheme(
    primary = AstraLightPrimary,
    onPrimary = AstraWhite,
    secondary = AstraIndigo,
    onSecondary = AstraWhite,
    tertiary = AstraVioletPrimary,
    background = AstraLightBg,
    onBackground = AstraLightText,
    surface = AstraLightSurface,
    onSurface = AstraLightText,
    surfaceVariant = AstraLightSurfaceVariant,
    onSurfaceVariant = AstraLightText,
    outline = AstraBorder
)

@Composable
fun AstraPDFTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
