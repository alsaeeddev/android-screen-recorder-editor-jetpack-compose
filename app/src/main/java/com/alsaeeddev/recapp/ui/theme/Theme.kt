package com.alsaeeddev.recapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BentoPrimary,
    onPrimary = BentoOnPrimary,
    primaryContainer = BentoPrimaryContainer,
    onPrimaryContainer = BentoOnPrimaryContainer,
    secondaryContainer = BentoAccentTile,
    surface = BentoCardSurface,
    surfaceVariant = BentoCardSurfaceVariant,
    background = BentoBackgroundLight,
    onBackground = BentoTextPrimary,
    onSurface = BentoTextPrimary,
    onSurfaceVariant = BentoTextSecondary,
    outline = BentoCardBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = BentoPrimaryDark,
    onPrimary = BentoOnPrimaryDark,
    primaryContainer = BentoPrimaryContainerDark,
    onPrimaryContainer = BentoOnPrimaryContainerDark,
    secondaryContainer = BentoAccentTileDark,
    surface = BentoCardSurfaceDark,
    surfaceVariant = BentoCardSurfaceVariantDark,
    background = BentoBackgroundDark,
    onBackground = BentoTextPrimaryDark,
    onSurface = BentoTextPrimaryDark,
    onSurfaceVariant = BentoTextSecondaryDark,
    outline = BentoCardBorderDark
)

@Composable
fun ScreenRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    ScreenRecorderTheme(darkTheme = darkTheme, content = content)
}
