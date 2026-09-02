package com.roadtwin.ai.core.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = RoadTwinPrimary,
    onPrimary = BackgroundWhite,
    primaryContainer = RoadTwinPrimaryContainer,
    onPrimaryContainer = RoadTwinOnPrimaryContainer,
    secondary = RoadTwinSecondary,
    onSecondary = BackgroundWhite,
    background = BackgroundWhite,
    onBackground = TextDarkCharcoal,
    surface = BackgroundWhite,
    onSurface = TextDarkCharcoal,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextMediumGray,
    outline = SurfaceVariantLight
)

@Composable
fun RoadTwinTheme(
    darkTheme: Boolean = false, // Default to light theme for the redesign
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
