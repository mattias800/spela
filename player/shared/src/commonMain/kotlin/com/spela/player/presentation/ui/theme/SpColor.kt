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

    // Link color — bright white for readable text on dark/gradient backgrounds
    val Link = Color(0xFFDDDDDD)

    // Secondary palette - vivid coral/rose
    val Secondary = Color(0xFFFF6B81)
    val SecondaryLight = Color(0xFFFF8E9E)
    val SecondaryDark = Color(0xFFE84563)
    val SecondaryContainer = Color(0xFF3D1520)

    // Accent - electric cyan
    val Accent = Color(0xFF00D2FF)
    val AccentLight = Color(0xFF69E4FF)
    val AccentDark = Color(0xFF009FCC)

    // Background surfaces - true dark, neutral (no blue tint)
    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF121214)
    val SurfaceVariant = Color(0xFF1A1A1E)
    val SurfaceElevated = Color(0xFF222226)
    val SurfaceBright = Color(0xFF2A2A30)

    // Card surfaces
    val Card = Color(0xFF161618)
    val CardHovered = Color(0xFF1E1E22)

    // Text - greyscale only, no color tint
    val OnBackground = Color(0xFFF0F0F0)
    val OnBackgroundSecondary = Color(0xFFCCCCCC)
    val OnBackgroundTertiary = Color(0xFF888888)
    val OnPrimary = Color(0xFFFFFFFF)
    val OnSecondary = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFFE8E8E8)
    val OnSurfaceVariant = Color(0xFFA0A0A0)
    val OnCard = Color(0xFFE0E0E0)

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
    val Divider = Color(0xFF2A2A2E)
    val DividerLight = Color(0xFF3A3A3E)

    // Spela brand gradient — used for focused input borders, primary buttons, secondary button borders
    val GradientStart = Color(0xFF6B8DD6)   // blue
    val GradientMid = Color(0xFFa855f7)     // purple
    val GradientEnd = Color(0xFFE056A0)     // pink
    val AccentPurple = Color(0xFFA78BFA)    // label accent
    val AccentPurpleLight = Color(0xFFC4B5FD) // lighter accent for text

    // Default screen background gradient (dark, neutral)
    val ScreenGradientStart = Color(0xFF1A1A20)
    val ScreenGradientEnd = Color(0xFF141418)
}

/**
 * Returns the Spela brand gradient brush (blue → purple → pink at 135°).
 * Used for focused input borders, primary button fills, and secondary button borders.
 */
fun spelaBrandGradient(
    startOffset: Offset = Offset.Zero,
    endOffset: Offset = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
): Brush = Brush.linearGradient(
    colors = listOf(SpColor.GradientStart, SpColor.GradientMid, SpColor.GradientEnd),
    start = startOffset,
    end = endOffset,
)

/**
 * Applies the standard dark gradient background used on content screens.
 * Ensures cards with transparent backgrounds blend properly instead of
 * showing pure black.
 */
fun Modifier.spScreenBackground(
    from: Color = SpColor.ScreenGradientStart,
    to: Color = SpColor.ScreenGradientEnd,
): Modifier = drawBehind {
    if (size.width <= 0f || size.height <= 0f) return@drawBehind
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
