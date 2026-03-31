package com.spela.player.presentation.ui.feature.library

import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.ui.theme.SpColor

/** Darkens a color by mixing it towards black. [amount] 0f = unchanged, 1f = pure black. */
fun Color.darken(amount: Float): Color = copy(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
)

/**
 * Returns the hero banner gradient (from, to) for a console.
 * Colors match the web UI's console-metadata.ts Tailwind gradient pairs exactly.
 * Falls back to deriving a gradient from the console's colorTheme hex.
 */
fun getConsoleGradient(abbreviation: String, colorTheme: String?): Pair<Color, Color> {
    // Curated gradient pairs matching the web UI (Tailwind color values)
    val gradient = when (abbreviation.lowercase()) {
        "nes" -> Color(0xFFdc2626) to Color(0xFF7f1d1d)         // red-600 → red-900
        "snes" -> Color(0xFF9333ea) to Color(0xFF312e81)        // purple-600 → indigo-900
        "gb" -> Color(0xFF16a34a) to Color(0xFF14532d)          // green-600 → green-900
        "gbc" -> Color(0xFF14b8a6) to Color(0xFF166534)         // teal-500 → green-800
        "gba" -> Color(0xFF6366f1) to Color(0xFF6b21a8)         // indigo-500 → purple-800
        "n64" -> Color(0xFF22c55e) to Color(0xFF1d4ed8)         // green-500 → blue-700
        "nds" -> Color(0xFF9ca3af) to Color(0xFF374151)         // gray-400 → gray-700
        "sms" -> Color(0xFF3b82f6) to Color(0xFF1e40af)         // blue-500 → blue-800
        "gen" -> Color(0xFF1d4ed8) to Color(0xFF000000)         // blue-700 → black
        "sat" -> Color(0xFF4b5563) to Color(0xFF111827)         // gray-600 → gray-900
        "psx" -> Color(0xFF6b7280) to Color(0xFF1e3a8a)         // gray-500 → blue-900
        "psp" -> Color(0xFF374151) to Color(0xFF000000)         // gray-700 → black
        "neogeo" -> Color(0xFFeab308) to Color(0xFFb91c1c)      // yellow-500 → red-700
        "neocd" -> Color(0xFFeab308) to Color(0xFFb91c1c)       // yellow-500 → red-700
        "pce" -> Color(0xFFf97316) to Color(0xFF991b1b)         // orange-500 → red-800
        "pcecd" -> Color(0xFFf97316) to Color(0xFF991b1b)       // orange-500 → red-800
        "a26" -> Color(0xFF1e293b) to Color(0xFF0f172a)         // slate-800 → slate-900 (complements red Atari logo)
        "gg" -> Color(0xFF2563eb) to Color(0xFF1e3a8a)          // blue-600 → blue-900
        "scd" -> Color(0xFF374151) to Color(0xFF1e3a8a)         // gray-700 → blue-900
        "32x" -> Color(0xFF1f2937) to Color(0xFF000000)         // gray-800 → black
        "dc" -> Color(0xFFf97316) to Color(0xFF1d4ed8)          // orange-500 → blue-700
        "vb" -> Color(0xFF991b1b) to Color(0xFF450a0a)          // red-800 → red-950
        "3ds" -> Color(0xFF1e3a5f) to Color(0xFF0c1a2e)         // dark teal-blue (complements red 3DS logo)
        "gc" -> Color(0xFF6366f1) to Color(0xFF6b21a8)           // indigo-500 → purple-800
        "a52" -> Color(0xFFb45309) to Color(0xFF451a03)         // amber-700 → amber-950
        "a78" -> Color(0xFF1e293b) to Color(0xFF0f172a)         // slate-800 → slate-900 (complements red logo)
        "lynx" -> Color(0xFFca8a04) to Color(0xFF713f12)        // yellow-600 → yellow-900
        "jag" -> Color(0xFFb91c1c) to Color(0xFF111827)         // red-700 → gray-900
        "ngp" -> Color(0xFF6b7280) to Color(0xFF1f2937)         // gray-500 → gray-800
        "ws" -> Color(0xFF4f46e5) to Color(0xFF312e81)          // indigo-600 → indigo-900
        "pcfx" -> Color(0xFF0d9488) to Color(0xFF134e4a)        // teal-600 → teal-900
        "cv" -> Color(0xFF0284c7) to Color(0xFF0c4a6e)          // sky-600 → sky-900
        "pkmn" -> Color(0xFFfacc15) to Color(0xFFa16207)        // yellow-400 → yellow-700
        "ps2" -> Color(0xFF1e40af) to Color(0xFF172554)         // blue-800 → blue-950
        "c64" -> Color(0xFF3b82f6) to Color(0xFF6b21a8)         // blue-500 → purple-800
        "dos" -> Color(0xFF15803d) to Color(0xFF052e16)         // green-700 → green-950
        "amiga" -> Color(0xFFdc2626) to Color(0xFF1e40af)       // red-600 → blue-800
        "3do" -> Color(0xFF9ca3af) to Color(0xFF374151)          // gray-400 → gray-700
        "c128" -> Color(0xFF3b82f6) to Color(0xFF6b21a8)         // blue-500 → purple-800
        "pet" -> Color(0xFF60a5fa) to Color(0xFF7e22ce)          // blue-400 → purple-700
        "plus4" -> Color(0xFF60a5fa) to Color(0xFF7e22ce)        // blue-400 → purple-700
        "vic20" -> Color(0xFF3b82f6) to Color(0xFF6b21a8)        // blue-500 → purple-800
        "cdi" -> Color(0xFF15803d) to Color(0xFF052e16)          // green-700 → green-950
        "msx1" -> Color(0xFF3b82f6) to Color(0xFF1e40af)         // blue-500 → blue-800
        "msx2" -> Color(0xFF3b82f6) to Color(0xFF1e40af)         // blue-500 → blue-800
        else -> null
    }
    if (gradient != null) return gradient

    // Fallback: use colorTheme hex and darken for the end color
    val baseColor = getConsoleColor(colorTheme)
    return baseColor to baseColor.darken(0.55f)
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
