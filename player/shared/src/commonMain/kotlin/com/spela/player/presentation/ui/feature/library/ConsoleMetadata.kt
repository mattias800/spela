package com.spela.player.presentation.ui.feature.library

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
