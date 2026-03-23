package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.ui.theme.SpColor

/**
 * CONTENT component — small chip showing rarity tier and percentage.
 *
 * Displays nothing for Common (> 50%) since it's not interesting.
 * For all other tiers shows: "Tier · X.X%"
 *
 * Uses [SpChip] with `isSelected = true` and a tier-appropriate color.
 */
@Composable
fun SpRarityChip(
    rarityPercent: Double,
    modifier: Modifier = Modifier,
) {
    val tier = RarityTier.from(rarityPercent)
    if (tier == RarityTier.Common) return

    val label = "${tier.label} · ${"%.1f".format(rarityPercent)}%"

    SpChip(
        text = label,
        color = tier.color,
        isSelected = true,
        modifier = modifier,
    )
}

private enum class RarityTier {
    Common,
    Uncommon,
    Rare,
    UltraRare,
    Legendary;

    val label: String
        get() = when (this) {
            Common -> "Common"
            Uncommon -> "Uncommon"
            Rare -> "Rare"
            UltraRare -> "Ultra Rare"
            Legendary -> "Legendary"
        }

    val color: Color
        get() = when (this) {
            Common -> Color.Transparent
            Uncommon -> Color(0xFFC0C0C0) // silver
            Rare -> Color(0xFFFFD700)     // gold
            UltraRare -> SpColor.Primary
            Legendary -> SpColor.Primary
        }

    companion object {
        fun from(rarityPercent: Double): RarityTier = when {
            rarityPercent > 50.0 -> Common
            rarityPercent > 20.0 -> Uncommon
            rarityPercent > 5.0 -> Rare
            rarityPercent > 1.0 -> UltraRare
            else -> Legendary
        }
    }
}
