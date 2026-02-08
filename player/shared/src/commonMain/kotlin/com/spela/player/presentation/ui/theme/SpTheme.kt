package com.spela.player.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SpDarkColorScheme = darkColorScheme(
    primary = SpColor.Primary,
    onPrimary = SpColor.OnPrimary,
    primaryContainer = SpColor.PrimaryContainer,
    secondary = SpColor.Secondary,
    onSecondary = SpColor.OnSecondary,
    secondaryContainer = SpColor.SecondaryContainer,
    tertiary = SpColor.Accent,
    background = SpColor.Background,
    onBackground = SpColor.OnBackground,
    surface = SpColor.Surface,
    onSurface = SpColor.OnSurface,
    surfaceVariant = SpColor.SurfaceVariant,
    onSurfaceVariant = SpColor.OnSurfaceVariant,
    error = SpColor.Error,
    errorContainer = SpColor.ErrorContainer,
    outline = SpColor.Divider,
    outlineVariant = SpColor.DividerLight,
    scrim = SpColor.Scrim,
)

@Composable
fun SpelaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpDarkColorScheme,
        content = content,
    )
}
