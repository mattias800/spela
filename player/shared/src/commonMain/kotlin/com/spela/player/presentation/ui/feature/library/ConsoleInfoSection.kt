package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Console
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Static per-console metadata: manufacturer, release year, hardware generation,
 * media type, and (where notable) units sold.
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
)

/**
 * Returns static [ConsoleInfo] for the given console abbreviation, or null if unknown.
 * Data is sourced from Wikipedia / manufacturer press releases.
 */
fun getConsoleInfo(abbreviation: String): ConsoleInfo? = when (abbreviation.lowercase()) {
    "nes"    -> ConsoleInfo("Nintendo",   1983, "Cartridge",    "3rd gen",       "61.9M units")
    "snes"   -> ConsoleInfo("Nintendo",   1990, "Cartridge",    "4th gen",       "49.1M units")
    "gb"     -> ConsoleInfo("Nintendo",   1989, "Cartridge",    "4th gen",       "118.7M units")
    "gbc"    -> ConsoleInfo("Nintendo",   1998, "Cartridge",    "5th gen")
    "gba"    -> ConsoleInfo("Nintendo",   2001, "Cartridge",    "6th gen",       "81.5M units")
    "n64"    -> ConsoleInfo("Nintendo",   1996, "Cartridge",    "5th gen",       "32.9M units")
    "nds"    -> ConsoleInfo("Nintendo",   2004, "Cartridge",    "7th gen",       "154M units")
    "sms"    -> ConsoleInfo("Sega",       1985, "Cartridge",    "3rd gen",       "13M units")
    "gen"    -> ConsoleInfo("Sega",       1988, "Cartridge",    "4th gen",       "30.7M units")
    "sat"    -> ConsoleInfo("Sega",       1994, "CD-ROM",       "5th gen",       "9.3M units")
    "scd"    -> ConsoleInfo("Sega",       1991, "CD-ROM",       "4th gen")
    "32x"    -> ConsoleInfo("Sega",       1994, "Cartridge",    "5th gen")
    "gg"     -> ConsoleInfo("Sega",       1990, "Cartridge",    "4th gen")
    "dc"     -> ConsoleInfo("Sega",       1998, "GD-ROM",       "6th gen",       "9.1M units")
    "psx"    -> ConsoleInfo("Sony",       1994, "CD-ROM",       "5th gen",       "102.5M units")
    "ps2"    -> ConsoleInfo("Sony",       2000, "DVD",          "6th gen",       "155M units")
    "psp"    -> ConsoleInfo("Sony",       2004, "UMD",          "7th gen",       "80.8M units")
    "neogeo" -> ConsoleInfo("SNK",        1990, "Cartridge",    "4th gen")
    "pce"    -> ConsoleInfo("NEC",        1987, "HuCard/CD",    "4th gen")
    "a26"    -> ConsoleInfo("Atari",      1977, "Cartridge",    "2nd gen",       "30M units")
    "a52"    -> ConsoleInfo("Atari",      1982, "Cartridge",    "2nd gen")
    "a78"    -> ConsoleInfo("Atari",      1986, "Cartridge",    "3rd gen")
    "lynx"   -> ConsoleInfo("Atari",      1989, "Cartridge",    "4th gen")
    "jag"    -> ConsoleInfo("Atari",      1993, "Cartridge",    "5th gen")
    "vb"     -> ConsoleInfo("Nintendo",   1995, "Cartridge",    "5th gen")
    "3ds"    -> ConsoleInfo("Nintendo",   2011, "Cartridge",    "8th gen",       "75.9M units")
    "ngp"    -> ConsoleInfo("SNK",        1998, "Cartridge",    "5th gen")
    "ws"     -> ConsoleInfo("Bandai",     1999, "Cartridge",    "5th gen")
    "cv"     -> ConsoleInfo("Coleco",     1982, "Cartridge",    "2nd gen")
    "c64"    -> ConsoleInfo("Commodore",  1982, "Tape / Floppy","Home computer", "17M units")
    "amiga"  -> ConsoleInfo("Commodore",  1985, "Floppy",       "Home computer")
    "dos"    -> ConsoleInfo("IBM / PC",   1981, "Floppy / HDD", "Home computer")
    "arcade" -> ConsoleInfo("Various",    1971, "Arcade board", "Arcade")
    else -> null
}

/**
 * A compact vertical list of console stats shown in the [ConsoleHeroBanner].
 * Each row shows an icon and value for: release year, manufacturer,
 * hardware generation, media type, and units sold.
 */
@Composable
internal fun ConsoleInfoSection(
    console: Console,
    modifier: Modifier = Modifier,
) {
    val info = getConsoleInfo(console.abbreviation) ?: return

    data class Stat(val icon: ImageVector, val value: String)

    val stats = buildList {
        add(Stat(Icons.Filled.CalendarToday, info.releaseYear.toString()))
        add(Stat(Icons.Filled.Business,      info.manufacturer))
        if (info.generation.isNotEmpty()) {
            add(Stat(Icons.Filled.Layers, info.generation))
        }
        add(Stat(Icons.Filled.SdCard, info.mediaType))
        if (info.unitsSold.isNotEmpty()) {
            add(Stat(Icons.Filled.Group, info.unitsSold))
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
    ) {
        stats.forEach { stat ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stat.value,
                    style = SpTypography.BodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}
