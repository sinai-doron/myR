package com.neonsheep.myreceipts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Light only
private val LightColorScheme = lightColorScheme(
    primary = Green,
    onPrimary = White,
    primaryContainer = GreenSoft,
    onPrimaryContainer = GreenDark,
    secondary = Green,
    onSecondary = White,
    background = Ground,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    onSurfaceVariant = Faint,
    surfaceVariant = Soft,
    surfaceContainerHigh = White,
    outline = Line,
    outlineVariant = Line,
    error = Red,
)

@Composable
fun MyReceiptsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
