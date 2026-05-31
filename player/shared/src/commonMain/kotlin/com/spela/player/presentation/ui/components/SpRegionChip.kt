package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.util.currentPlatform

/**
 * ROLE component — a chip that displays a game region with its flag emoji.
 *
 * Layer 3 in the component hierarchy (Design → Content → Role).
 * Thin wrapper around [SpChip] — maps region text to chip with flag prefix.
 * All region badges across the app must use this, never raw [SpChip].
 */
@Composable
fun SpRegionChip(
    region: String,
    modifier: Modifier = Modifier,
    onGradient: Boolean = false,
) {
    SpChip(
        text = regionChipText(region, currentPlatform()),
        isSelected = true,
        modifier = modifier,
        onGradient = onGradient,
    )
}

private val regionFlags = mapOf(
    "USA" to "🇺🇸",
    "Japan" to "🇯🇵",
    "Europe" to "🇪🇺",
    "World" to "🌍",
    "Korea" to "🇰🇷",
    "Brazil" to "🇧🇷",
    "France" to "🇫🇷",
    "Germany" to "🇩🇪",
    "Spain" to "🇪🇸",
    "Italy" to "🇮🇹",
    "Australia" to "🇦🇺",
    "China" to "🇨🇳",
    "Canada" to "🇨🇦",
    "UK" to "🇬🇧",
    "Sweden" to "🇸🇪",
    "Netherlands" to "🇳🇱",
    "Russia" to "🇷🇺",
    "Taiwan" to "🇹🇼",
    "Asia" to "🌏",
)

private fun getRegionFlag(region: String): String? =
    regionFlags.entries.firstOrNull { region.contains(it.key, ignoreCase = true) }?.value

/**
 * Builds the chip label, prefixing the flag emoji only when it will
 * actually render on [platform].
 *
 * Country flags are regional-indicator pairs (e.g. 🇺🇸 = U+1F1FA U+1F1F8).
 * The default Windows/Linux desktop fonts ship no flag glyphs, so those
 * render as the bare indicator letters ("US"). Drop the flag there and
 * show the region name alone. Globe emoji (🌍/🌏 for World/Asia) are a
 * single codepoint that *does* render everywhere, so they're kept. macOS
 * and Android render flag emoji natively. (#1253)
 */
internal fun regionChipText(region: String, platform: String): String {
    val flag = getRegionFlag(region)
    return if (flag != null && flagRendersOn(flag, platform)) "$flag $region" else region
}

internal fun flagRendersOn(flag: String, platform: String): Boolean {
    if (!isRegionalIndicatorFlag(flag)) return true // globes etc. render everywhere
    return platform != "windows" && platform != "linux"
}

/**
 * True when [flag] is a regional-indicator flag emoji (a surrogate pair
 * whose low surrogate is in the regional-indicator block U+1F1E6..U+1F1FF).
 * Globe emoji like 🌍 share the same high surrogate (0xD83C) but a
 * different low surrogate, so the low surrogate is what distinguishes them.
 */
private fun isRegionalIndicatorFlag(flag: String): Boolean =
    flag.length >= 2 && flag[0].code == 0xD83C && flag[1].code in 0xDDE6..0xDDFF
