package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Displays an achievement badge image with a rarity-tier colored border.
 *
 * Rarity tiers (by unlock percentage):
 * - Common    (> 50%):  subtle divider border
 * - Uncommon  (20–50%): silver border
 * - Rare      ( 5–20%): gold border
 * - Ultra Rare( 1– 5%): primary (indigo) border, thicker
 * - Legendary (<  1%):  primary (indigo) border, thicker
 *
 * When [isUnlocked] is false the entire badge is dimmed to 40% opacity.
 *
 * @param badgeUrl       RA badge image URL (may be null while loading or unavailable)
 * @param rarityPercent  Unlock percentage in the range 0–100
 * @param modifier       Modifier applied to the root Box
 * @param size           Diameter of the square badge
 * @param isUnlocked     Whether the achievement has been unlocked by the current user
 */
@Composable
fun SpAchievementBadge(
    badgeUrl: String?,
    rarityPercent: Double,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isUnlocked: Boolean = true,
) {
    val rarity = AchievementRarity.from(rarityPercent)
    val borderColor = rarity.borderColor
    val borderWidth = rarity.borderWidth
    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)

    Box(
        modifier = modifier
            .size(size)
            .alpha(if (isUnlocked) 1f else 0.4f)
            .clip(shape)
            .border(width = borderWidth, color = borderColor, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        if (badgeUrl != null) {
            SubcomposeAsyncImage(
                model = badgeUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    BadgeFallback(rarityColor = borderColor)
                },
                error = {
                    BadgeFallback(rarityColor = borderColor)
                },
            )
        } else {
            BadgeFallback(rarityColor = borderColor)
        }
    }
}

@Composable
private fun BadgeFallback(rarityColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "?",
            style = SpTypography.TitleMedium,
            color = rarityColor,
        )
    }
}

private enum class AchievementRarity {
    Common,
    Uncommon,
    Rare,
    UltraRare,
    Legendary;

    val borderColor: Color
        get() = when (this) {
            Common -> SpColor.Divider
            Uncommon -> Color(0xFFC0C0C0)
            Rare -> Color(0xFFFFD700)
            UltraRare -> SpColor.Primary
            Legendary -> SpColor.Primary
        }

    val borderWidth: Dp
        get() = when (this) {
            Common, Uncommon -> 2.dp
            Rare, UltraRare, Legendary -> 3.dp
        }

    companion object {
        fun from(rarityPercent: Double): AchievementRarity = when {
            rarityPercent > 50.0 -> Common
            rarityPercent > 20.0 -> Uncommon
            rarityPercent > 5.0 -> Rare
            rarityPercent > 1.0 -> UltraRare
            else -> Legendary
        }
    }
}
