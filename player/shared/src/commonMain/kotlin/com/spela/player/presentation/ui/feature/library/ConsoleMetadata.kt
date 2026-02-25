package com.spela.player.presentation.ui.feature.library

import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.ui.theme.SpColor

/**
 * Static per-console metadata: manufacturer, release year, hardware generation,
 * media type, units sold, and a short prose summary.
 *
 * Source strategy:
 * - Short-term: hardcoded here, exactly like [getConsoleGradient].
 * - Long-term: fetch from IGDB `/platforms` + `PlatformVersion` endpoints and store
 *   in the Console DB model. The IGDB Platform entity provides `summary`, `generation`,
 *   and linked `PlatformVersion` objects with CPU, RAM, media, graphics specs.
 *   Import timing: admin-triggered scrape alongside the existing IGDB game scraper.
 */
data class ConsoleInfo(
    val manufacturer: String,
    val releaseYear: Int,
    val mediaType: String,
    val generation: String = "",
    val unitsSold: String = "",
    val summary: String = "",
)

/**
 * Returns static [ConsoleInfo] for the given console abbreviation, or null if unknown.
 * Data is sourced from Wikipedia / manufacturer press releases.
 */
fun getConsoleInfo(abbreviation: String): ConsoleInfo? = when (abbreviation.lowercase()) {
    "nes" -> ConsoleInfo(
        manufacturer = "Nintendo",
        releaseYear = 1983,
        mediaType = "Cartridge",
        generation = "3rd gen",
        unitsSold = "61.9M units",
        summary = "Nintendo's landmark 8-bit console that revived the video game industry and launched iconic franchises like Mario, Zelda, and Metroid.",
    )
    "snes" -> ConsoleInfo(
        manufacturer = "Nintendo",
        releaseYear = 1990,
        mediaType = "Cartridge",
        generation = "4th gen",
        unitsSold = "49.1M units",
        summary = "Nintendo's 16-bit powerhouse defined the golden age of gaming with Mode 7 effects, a legendary soundtrack chip, and a library full of timeless classics.",
    )
    "gb" -> ConsoleInfo(
        manufacturer = "Nintendo",
        releaseYear = 1989,
        mediaType = "Cartridge",
        generation = "4th gen",
        unitsSold = "118.7M units",
        summary = "Nintendo's portable juggernaut dominated the handheld market for a decade. Tetris as the pack-in title and the Pokémon phenomenon made it a cultural touchstone.",
    )
    "gbc" -> ConsoleInfo(
        manufacturer = "Nintendo",
        releaseYear = 1998,
        mediaType = "Cartridge",
        generation = "5th gen",
        summary = "A color-enhanced Game Boy with backwards compatibility. Home to Pokémon Gold and Silver, which set the blueprint for the entire series.",
    )
    "gba" -> ConsoleInfo(
        manufacturer = "Nintendo",
        releaseYear = 2001,
        mediaType = "Cartridge",
        generation = "6th gen",
        unitsSold = "81.5M units",
        summary = "Nintendo's 32-bit handheld brought near-SNES quality gaming to your pocket and hosted beloved originals alongside definitive remakes of classic titles.",
    )
    "n64" -> ConsoleInfo(
        manufacturer = "Nintendo",
        releaseYear = 1996,
        mediaType = "Cartridge",
        generation = "5th gen",
        unitsSold = "32.9M units",
        summary = "Nintendo's 64-bit system introduced groundbreaking 3D gameplay. Super Mario 64 and Ocarina of Time set templates for 3D game design that still hold today.",
    )
    "nds" -> ConsoleInfo(
        manufacturer = "Nintendo",
        releaseYear = 2004,
        mediaType = "Cartridge",
        generation = "7th gen",
        unitsSold = "154M units",
        summary = "The dual-screen handheld became the second best-selling console ever. Its touchscreen opened up entirely new gameplay styles across an enormous, diverse library.",
    )
    "sms" -> ConsoleInfo(
        manufacturer = "Sega",
        releaseYear = 1985,
        mediaType = "Cartridge",
        generation = "3rd gen",
        unitsSold = "13M units",
        summary = "Sega's 8-bit challenger to the NES had superior hardware but struggled against Nintendo's third-party exclusivity in North America. Found greater success in Europe and Brazil.",
    )
    "gen" -> ConsoleInfo(
        manufacturer = "Sega",
        releaseYear = 1988,
        mediaType = "Cartridge",
        generation = "4th gen",
        unitsSold = "30.7M units",
        summary = "Sega's aggressive 16-bit rival to the SNES, defined by Sonic the Hedgehog, a darker tone, and a wealth of quality arcade ports.",
    )
    "sat" -> ConsoleInfo(
        manufacturer = "Sega",
        releaseYear = 1994,
        mediaType = "CD-ROM",
        generation = "5th gen",
        unitsSold = "9.3M units",
        summary = "Sega's 32-bit CD console had a surprise early launch that backfired, and its complex dual-CPU architecture made development challenging compared to the PlayStation.",
    )
    "scd" -> ConsoleInfo(
        manufacturer = "Sega",
        releaseYear = 1991,
        mediaType = "CD-ROM",
        generation = "4th gen",
        summary = "A CD-ROM add-on for the Genesis/Mega Drive that introduced FMV games and enhanced audio, though its high price limited adoption.",
    )
    "32x" -> ConsoleInfo(
        manufacturer = "Sega",
        releaseYear = 1994,
        mediaType = "Cartridge",
        generation = "5th gen",
        summary = "A stopgap add-on for the Genesis intended to compete with 32-bit systems. Short-lived due to the imminent Saturn launch and a small library.",
    )
    "gg" -> ConsoleInfo(
        manufacturer = "Sega",
        releaseYear = 1990,
        mediaType = "Cartridge",
        generation = "4th gen",
        summary = "Sega's compact color handheld offered superior hardware to the Game Boy but suffered from shorter battery life and a smaller library.",
    )
    "dc" -> ConsoleInfo(
        manufacturer = "Sega",
        releaseYear = 1998,
        mediaType = "GD-ROM",
        generation = "6th gen",
        unitsSold = "9.1M units",
        summary = "Sega's final and beloved console was ahead of its time with built-in internet, online multiplayer, and a memorable library — discontinued in 2001 under PS2 pressure.",
    )
    "psx" -> ConsoleInfo(
        manufacturer = "Sony",
        releaseYear = 1994,
        mediaType = "CD-ROM",
        generation = "5th gen",
        unitsSold = "102.5M units",
        summary = "Sony's debut console was the first to sell 100 million units. CD-ROM enabled cinematic storytelling and a library of landmark RPGs and action games.",
    )
    "ps2" -> ConsoleInfo(
        manufacturer = "Sony",
        releaseYear = 2000,
        mediaType = "DVD",
        generation = "6th gen",
        unitsSold = "155M units",
        summary = "The best-selling console of all time. DVD playback, a massive launch line-up, and an endless stream of quality titles made it a decade-defining household staple.",
    )
    "psp" -> ConsoleInfo(
        manufacturer = "Sony",
        releaseYear = 2004,
        mediaType = "UMD",
        generation = "7th gen",
        unitsSold = "80.8M units",
        summary = "Sony's first handheld brought home-console quality graphics and multimedia on the go. Its connectivity features and UMD format were ambitious for their time.",
    )
    "neogeo" -> ConsoleInfo(
        manufacturer = "SNK",
        releaseYear = 1990,
        mediaType = "Cartridge",
        generation = "4th gen",
        summary = "SNK's premium arcade-at-home console offered uncompromised arcade experiences. High price made it a luxury item beloved by fighting game enthusiasts.",
    )
    "pce" -> ConsoleInfo(
        manufacturer = "NEC",
        releaseYear = 1987,
        mediaType = "HuCard / CD",
        generation = "4th gen",
        summary = "NEC's hybrid 8/16-bit console was the first to market with a CD-ROM add-on. Popular in Japan with a strong shoot-em-up and RPG library.",
    )
    "a26" -> ConsoleInfo(
        manufacturer = "Atari",
        releaseYear = 1977,
        mediaType = "Cartridge",
        generation = "2nd gen",
        unitsSold = "30M units",
        summary = "Atari's iconic 2600 defined home gaming in the late 70s and early 80s. Space Invaders' runaway success on the platform ignited the home gaming revolution.",
    )
    "a52" -> ConsoleInfo(
        manufacturer = "Atari",
        releaseYear = 1982,
        mediaType = "Cartridge",
        generation = "2nd gen",
        summary = "Atari's follow-up to the 2600 offered significantly improved graphics and sound, though it arrived as the North American video game crash loomed.",
    )
    "a78" -> ConsoleInfo(
        manufacturer = "Atari",
        releaseYear = 1986,
        mediaType = "Cartridge",
        generation = "3rd gen",
        summary = "Atari's 7800 launched late and struggled against the NES, though its backward compatibility with 2600 cartridges gave it an instant library.",
    )
    "lynx" -> ConsoleInfo(
        manufacturer = "Atari",
        releaseYear = 1989,
        mediaType = "Cartridge",
        generation = "4th gen",
        summary = "The world's first color handheld console, with impressive hardware for the time, but short battery life and a limited library held it back against the Game Boy.",
    )
    "jag" -> ConsoleInfo(
        manufacturer = "Atari",
        releaseYear = 1993,
        mediaType = "Cartridge",
        generation = "5th gen",
        summary = "Atari's self-proclaimed 64-bit system had ambitions that outpaced its reality. A small library with a few standout titles preceded Atari's exit from hardware.",
    )
    "vb" -> ConsoleInfo(
        manufacturer = "Nintendo",
        releaseYear = 1995,
        mediaType = "Cartridge",
        generation = "5th gen",
        summary = "Nintendo's ill-fated head-mounted system used a monochrome red display to create 3D depth effects. Discontinued after less than a year on the market.",
    )
    "3ds" -> ConsoleInfo(
        manufacturer = "Nintendo",
        releaseYear = 2011,
        mediaType = "Cartridge",
        generation = "8th gen",
        unitsSold = "75.9M units",
        summary = "Nintendo's glasses-free 3D handheld recovered from a rocky start to build one of the richest portable libraries, with acclaimed entries in Zelda, Pokémon, and Fire Emblem.",
    )
    "ngp" -> ConsoleInfo(
        manufacturer = "SNK",
        releaseYear = 1998,
        mediaType = "Cartridge",
        generation = "5th gen",
        summary = "SNK's compact monochrome handheld (upgraded to color in 1999) offered a polished SNK game library in a remarkably small package.",
    )
    "ws" -> ConsoleInfo(
        manufacturer = "Bandai",
        releaseYear = 1999,
        mediaType = "Cartridge",
        generation = "5th gen",
        summary = "Bandai's budget handheld with a unique rotatable screen offered an impressive library for its price point, particularly strong in Japan.",
    )
    "cv" -> ConsoleInfo(
        manufacturer = "Coleco",
        releaseYear = 1982,
        mediaType = "Cartridge",
        generation = "2nd gen",
        summary = "ColecoVision's near-arcade-quality graphics made it one of the most capable 2nd-generation consoles, with strong Donkey Kong and Zaxxon ports.",
    )
    "c64" -> ConsoleInfo(
        manufacturer = "Commodore",
        releaseYear = 1982,
        mediaType = "Tape / Floppy",
        generation = "Home computer",
        unitsSold = "17M units",
        summary = "The best-selling single computer model of all time. Its SID sound chip produced groundbreaking music and it dominated European gaming through the 80s.",
    )
    "amiga" -> ConsoleInfo(
        manufacturer = "Commodore",
        releaseYear = 1985,
        mediaType = "Floppy",
        generation = "Home computer",
        summary = "The Amiga's advanced graphics and audio capabilities made it the creative professional's and gamer's machine of choice in Europe throughout the late 80s and early 90s.",
    )
    "dos" -> ConsoleInfo(
        manufacturer = "IBM / PC",
        releaseYear = 1981,
        mediaType = "Floppy / HDD",
        generation = "Home computer",
        summary = "The PC-compatible DOS platform became the dominant home computing and gaming platform of the 1980s and early 90s, pioneering genres from adventure games to flight simulators.",
    )
    "arcade" -> ConsoleInfo(
        manufacturer = "Various",
        releaseYear = 1971,
        mediaType = "Arcade board",
        generation = "Arcade",
        summary = "Coin-operated arcade games defined gaming culture from the 1970s through the 90s, driving hardware innovation and spawning most of the genres and franchises we know today.",
    )
    else -> null
}

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
        "pce" -> Color(0xFFf97316) to Color(0xFF991b1b)         // orange-500 → red-800
        "a26" -> Color(0xFFd97706) to Color(0xFF78350f)         // amber-600 → amber-900
        "gg" -> Color(0xFF2563eb) to Color(0xFF1e3a8a)          // blue-600 → blue-900
        "scd" -> Color(0xFF374151) to Color(0xFF1e3a8a)         // gray-700 → blue-900
        "32x" -> Color(0xFF1f2937) to Color(0xFF000000)         // gray-800 → black
        "dc" -> Color(0xFFf97316) to Color(0xFF1d4ed8)          // orange-500 → blue-700
        "vb" -> Color(0xFF991b1b) to Color(0xFF450a0a)          // red-800 → red-950
        "3ds" -> Color(0xFFef4444) to Color(0xFF991b1b)         // red-500 → red-800
        "a52" -> Color(0xFFb45309) to Color(0xFF451a03)         // amber-700 → amber-950
        "a78" -> Color(0xFFd97706) to Color(0xFF78350f)         // amber-600 → amber-900
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
