package com.spela.player.presentation.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpDefaultDarkColors = darkColorScheme(
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

private val SpDefaultLightColors = lightColorScheme(
    primary = Color(0xFF0081B3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4EEFF),
    secondary = Color(0xFF4A6572),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E4EC),
    tertiary = Color(0xFF0081B3),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE9ECEF),
    onSurfaceVariant = Color(0xFF44474E),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFDEE2E6),
    outlineVariant = Color(0xFFCED4DA),
    scrim = Color(0x66000000),
)

private val SpNintendoColorfulColors = lightColorScheme(
    primary = Color(0xFFE52521),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFF43B047),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4F5D0),
    tertiary = Color(0xFFF5C722),
    background = Color(0xFFFAF8F4),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF0EDE8),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFE0DCD6),
    outlineVariant = Color(0xFFD0CBC5),
    scrim = Color(0x66000000),
)

private val SpOceanDarkColors = darkColorScheme(
    primary = Color(0xFF00C896),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF003829),
    secondary = Color(0xFF4DB8A4),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF0D3A30),
    tertiary = Color(0xFF00C896),
    background = Color(0xFF04111F),
    onBackground = Color(0xFFE0E8F0),
    surface = Color(0xFF0D2A3D),
    onSurface = Color(0xFFDDE4EC),
    surfaceVariant = Color(0xFF153A50),
    onSurfaceVariant = Color(0xFFA0B8C8),
    error = Color(0xFFFF5252),
    errorContainer = Color(0xFF3D1515),
    outline = Color(0xFF1E4A60),
    outlineVariant = Color(0xFF2A5A70),
    scrim = Color(0xCC000000),
)

private val SpSunsetWarmColors = lightColorScheme(
    primary = Color(0xFFE8622A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFD4902E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE0B2),
    tertiary = Color(0xFFE8622A),
    background = Color(0xFFFDF8F4),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFEEDDD0),
    onSurfaceVariant = Color(0xFF52443B),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFE0D0C2),
    outlineVariant = Color(0xFFD0C0B2),
    scrim = Color(0x66000000),
)

fun colorSchemeForTheme(theme: String): ColorScheme = when (theme) {
    "default-dark" -> SpDefaultDarkColors
    "default-light" -> SpDefaultLightColors
    "nintendo-colorful" -> SpNintendoColorfulColors
    "ocean-dark" -> SpOceanDarkColors
    "sunset-warm" -> SpSunsetWarmColors
    else -> SpDefaultDarkColors
}

@Composable
fun SpelaTheme(
    theme: String = "default-dark",
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorSchemeForTheme(theme),
        content = content,
    )
}
