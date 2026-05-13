package com.spela.player.presentation.ui.feature.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.spela.player.presentation.ui.theme.SpColor

/**
 * Brightness floor used when darkening a console gradient — keeps
 * very-dark brands (Genesis #171717, PSP #000000, ColecoVision
 * #000000, etc.) from collapsing to pure black after the second-
 * order darken in ConsoleScreen / GameDetailScreen, where the
 * background would otherwise swallow card borders and any other
 * semi-transparent overlay. Equivalent of ~#1A1A1A — a visible dark
 * grey rather than the surface itself.
 *
 * The floor only kicks in when the darkened result is *below* this
 * brightness; brighter consoles (NES red, Game Boy olive) keep their
 * tinted darken result unchanged.
 */
private const val DARKEN_MIN_BRIGHTNESS = 0.10f

/**
 * Darkens a color by mixing it towards black. [amount] 0f = unchanged,
 * 1f = pure black. Clamps the result so the average channel brightness
 * doesn't fall below [DARKEN_MIN_BRIGHTNESS] — see the constant for
 * why. The lift preserves hue: we add the same delta to every channel
 * rather than per-channel max, so darkening a saturated colour
 * doesn't grey it out unless the saturated colour was itself very
 * dark to begin with.
 */
fun Color.darken(amount: Float): Color {
    val newR = red * (1f - amount)
    val newG = green * (1f - amount)
    val newB = blue * (1f - amount)
    val brightness = (newR + newG + newB) / 3f
    if (brightness >= DARKEN_MIN_BRIGHTNESS) {
        return copy(red = newR, green = newG, blue = newB)
    }
    val lift = DARKEN_MIN_BRIGHTNESS - brightness
    return copy(
        red = (newR + lift).coerceAtMost(1f),
        green = (newG + lift).coerceAtMost(1f),
        blue = (newB + lift).coerceAtMost(1f),
    )
}

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
    // Dark stop targets a near-black grey (#1A1A1A) instead of pure
    // black so very-dark brands (Genesis #171717, PSP #000000) still
    // have a visible-against-the-surface bottom of the gradient. For
    // brighter brands the visible difference is negligible.
    val darkStop = lerp(brand, Color(0xFF1A1A1A), 0.6f)
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
