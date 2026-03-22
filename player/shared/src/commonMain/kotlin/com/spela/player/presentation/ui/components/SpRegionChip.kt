package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    val flag = getRegionFlag(region)
    SpChip(
        text = if (flag != null) "$flag $region" else region,
        isSelected = true,
        modifier = modifier,
        onGradient = onGradient,
    )
}

private val regionFlags = mapOf(
    "USA" to "\uD83C\uDDFA\uD83C\uDDF8",
    "Japan" to "\uD83C\uDDEF\uD83C\uDDF5",
    "Europe" to "\uD83C\uDDEA\uD83C\uDDFA",
    "World" to "\uD83C\uDF0D",
    "Korea" to "\uD83C\uDDF0\uD83C\uDDF7",
    "Brazil" to "\uD83C\uDDE7\uD83C\uDDF7",
    "France" to "\uD83C\uDDEB\uD83C\uDDF7",
    "Germany" to "\uD83C\uDDE9\uD83C\uDDEA",
    "Spain" to "\uD83C\uDDEA\uD83C\uDDF8",
    "Italy" to "\uD83C\uDDEE\uD83C\uDDF9",
    "Australia" to "\uD83C\uDDE6\uD83C\uDDFA",
    "China" to "\uD83C\uDDE8\uD83C\uDDF3",
    "Canada" to "\uD83C\uDDE8\uD83C\uDDE6",
    "UK" to "\uD83C\uDDEC\uD83C\uDDE7",
    "Sweden" to "\uD83C\uDDF8\uD83C\uDDEA",
    "Netherlands" to "\uD83C\uDDF3\uD83C\uDDF1",
    "Russia" to "\uD83C\uDDF7\uD83C\uDDFA",
    "Taiwan" to "\uD83C\uDDF9\uD83C\uDDFC",
    "Asia" to "\uD83C\uDF0F",
)

private fun getRegionFlag(region: String): String? =
    regionFlags.entries.firstOrNull { region.contains(it.key, ignoreCase = true) }?.value
