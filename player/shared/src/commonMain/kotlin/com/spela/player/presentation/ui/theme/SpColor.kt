package com.spela.player.presentation.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object SpColor {
    // Primary palette - deep electric indigo
    val Primary = Color(0xFF6C5CE7)
    val PrimaryLight = Color(0xFF9B8FEF)
    val PrimaryDark = Color(0xFF4834D4)
    val PrimaryContainer = Color(0xFF1E1640)

    // Link color — brighter than Primary for readable text on dark backgrounds
    val Link = Color(0xFF9B8FEF)

    // Secondary palette - vivid coral/rose
    val Secondary = Color(0xFFFF6B81)
    val SecondaryLight = Color(0xFFFF8E9E)
    val SecondaryDark = Color(0xFFE84563)
    val SecondaryContainer = Color(0xFF3D1520)

    // Accent - electric cyan
    val Accent = Color(0xFF00D2FF)
    val AccentLight = Color(0xFF69E4FF)
    val AccentDark = Color(0xFF009FCC)

    // Background surfaces - true dark with subtle blue tint
    val Background = Color(0xFF0A0A10)
    val Surface = Color(0xFF12121C)
    val SurfaceVariant = Color(0xFF1A1A28)
    val SurfaceElevated = Color(0xFF222236)
    val SurfaceBright = Color(0xFF2A2A42)

    // Card surfaces
    val Card = Color(0xFF16162A)
    val CardHovered = Color(0xFF1E1E38)

    // Text
    val OnBackground = Color(0xFFF0F0F8)
    val OnBackgroundSecondary = Color(0xFFB0B0C8)
    val OnBackgroundTertiary = Color(0xFF707090)
    val OnPrimary = Color(0xFFFFFFFF)
    val OnSecondary = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFFE8E8F0)
    val OnSurfaceVariant = Color(0xFFA0A0B8)
    val OnCard = Color(0xFFE0E0F0)

    // Semantic interaction colors
    val Favorite = Color(0xFFFF4757)  // heart / like
    val Rating = Color(0xFFFACC15)    // star rating

    // Status colors
    val Success = Color(0xFF00C853)
    val SuccessContainer = Color(0xFF0D2818)
    val Warning = Color(0xFFFFD600)
    val WarningContainer = Color(0xFF2D2800)
    val Error = Color(0xFFFF5252)
    val ErrorContainer = Color(0xFF3D1515)

    // Medal colors (leaderboard)
    val Gold = Color(0xFFFFD700)
    val Silver = Color(0xFFC0C0C0)
    val Bronze = Color(0xFFCD7F32)

    // Download states
    val DownloadIdle = OnBackgroundTertiary
    val DownloadQueued = Warning
    val DownloadActive = Accent
    val DownloadComplete = Success
    val DownloadFailed = Error

    // Console brand colors
    val ConsoleNes = Color(0xFFE60012)
    val ConsoleSnes = Color(0xFF7B7BB0)
    val ConsoleGameBoy = Color(0xFF9BBC0F)
    val ConsoleGba = Color(0xFF5A1F9E)
    val ConsoleN64 = Color(0xFF009E42)
    val ConsoleNds = Color(0xFF939393)
    val ConsoleSega = Color(0xFF0060A8)
    val ConsolePsx = Color(0xFF003087)
    val ConsolePsp = Color(0xFF1A1A1A)
    val ConsoleArcade = Color(0xFFFF6F00)

    // Scrim / overlay
    val Scrim = Color(0xCC000000)
    val ScrimLight = Color(0x80000000)

    // Divider
    val Divider = Color(0xFF2A2A3A)
    val DividerLight = Color(0xFF3A3A4A)

    // Default screen background gradient (dark, non-black)
    val ScreenGradientStart = Color(0xFF1E1E3A)
    val ScreenGradientEnd = Color(0xFF162030)
}

/**
 * Applies the standard dark gradient background used on content screens.
 * Ensures cards with transparent backgrounds blend properly instead of
 * showing pure black.
 */
fun Modifier.spScreenBackground(
    from: Color = SpColor.ScreenGradientStart,
    to: Color = SpColor.ScreenGradientEnd,
): Modifier = drawBehind {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val d = (size.width + size.height) * 0.25f
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(from, to),
            start = Offset(cx - d, cy - d),
            end = Offset(cx + d, cy + d),
        ),
    )
}
