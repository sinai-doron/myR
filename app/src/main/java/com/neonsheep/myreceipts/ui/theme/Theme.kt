package com.neonsheep.myreceipts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Light only, like gym-tracker
private val LightColorScheme = lightColorScheme(
    primary = Purple,
    onPrimary = TextOnPurple,
    primaryContainer = PurpleLight,
    onPrimaryContainer = PurpleDark,
    secondary = SuccessGreen,
    onSecondary = White,
    background = LavenderBackground,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    surfaceVariant = LightSurface,
    outline = Outline,
    outlineVariant = Divider,
    error = DangerRed,
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
