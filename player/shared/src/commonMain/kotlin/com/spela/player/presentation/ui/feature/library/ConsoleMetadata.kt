package com.spela.player.presentation.ui.feature.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.spela.player.presentation.ui.theme.SpColor

/** Darkens a color by mixing it towards black. [amount] 0f = unchanged, 1f = pure black. */
fun Color.darken(amount: Float): Color = copy(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
)

/**
 * Returns the `(from, to)` gradient pair for a console-coloured surface.
 *
 * Both stops are derived from [Console.colorTheme] (the server-seeded
 * brand hex) — the same source the Explore "Browse by Console" strip
 * uses, and the web Consoles list / hero now use after #1167. The
 * dark stop is the brand hex mixed 60% with black, which gives every
 * theme — even dark ones like Genesis (#171717) or Atari 2600
 * (#1e293b) — a visible two-shade sweep. This mirrors CSS's
 * `color-mix(in srgb, brand, black 60%)` used on web.
 *
 * Previously this returned a per-console table of two hard-coded
 * Tailwind colours (e.g. NES `red-600 → red-900`). That drifted from
 * the server's `colorTheme` — admins editing the seed couldn't tell
 * what would change, and the same console rendered with different
 * palettes on different screens. The single-source-of-truth fix
 * landed in #1167; this function is the player-side equivalent.
 */
fun getConsoleGradient(colorTheme: String?): Pair<Color, Color> {
    val brand = getConsoleColor(colorTheme)
    val darkStop = lerp(brand, Color.Black, 0.6f)
    return brand to darkStop
}

internal fun getConsoleColor(colorTheme: String?): Color {
    if (colorTheme == null) return SpColor.Primary
    // Try parsing as hex color first (backend sends "#e53e3e" format)
    if (colorTheme.startsWith("#")) {
        return try {
            val hex = colorTheme.removePrefix("#")
            val colorLong = when (hex.length) {
                6 -> (0xFF000000 or hex.toLong(16))
                8 -> hex.toLong(16)
                else -> null
            }
            if (colorLong != null) Color(colorLong.toInt()) else SpColor.Primary
        } catch (_: NumberFormatException) {
            SpColor.Primary
        }
    }
    // Fallback to name matching for backwards compatibility
    return when (colorTheme.lowercase()) {
        "nes" -> SpColor.ConsoleNes
        "snes" -> SpColor.ConsoleSnes
        "gameboy", "gb", "gbc" -> SpColor.ConsoleGameBoy
        "gba" -> SpColor.ConsoleGba
        "n64" -> SpColor.ConsoleN64
        "nds" -> SpColor.ConsoleNds
        "sega", "genesis", "megadrive" -> SpColor.ConsoleSega
        "psx", "playstation" -> SpColor.ConsolePsx
        "psp" -> SpColor.ConsolePsp
        "arcade", "mame" -> SpColor.ConsoleArcade
        else -> SpColor.Primary
    }
}
