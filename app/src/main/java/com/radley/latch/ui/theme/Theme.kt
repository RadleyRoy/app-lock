package com.radley.latch.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Latch is dark-only on purpose: the source palette is built around a near-black ground, and
 * a synthesised light scheme would invert the one relationship the design depends on. So
 * [isSystemInDarkTheme] is deliberately not consulted.
 */
private val LatchColorScheme = darkColorScheme(
    primary = Clay,
    onPrimary = Bone,
    primaryContainer = Cocoa,
    onPrimaryContainer = Bone,
    secondary = Taupe,
    onSecondary = Ink,
    tertiary = Taupe,
    onTertiary = Ink,
    background = Ink,
    onBackground = Bone,
    surface = Ink,
    onSurface = Bone,
    surfaceVariant = Surface1,
    onSurfaceVariant = Ash,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Cocoa,
    outline = Slate,
    outlineVariant = Surface1,
    error = Ember,
    onError = Bone,
)

@Composable
fun LatchTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = LatchColorScheme,
        typography = LatchTypography,
        content = content,
    )
}
