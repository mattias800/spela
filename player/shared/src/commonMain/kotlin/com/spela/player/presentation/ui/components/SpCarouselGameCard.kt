package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * ROLE component — a game card for horizontal carousels.
 *
 * Layer 3 in the component hierarchy (Design → Content → Role).
 * Fixed cover height, dynamic width determined by the actual image.
 * All cards in the carousel align at the same height while widths
 * vary naturally to show full uncropped box art.
 *
 * Use in [LazyRow] with [Arrangement.spacedBy].
 */
@Composable
fun SpCarouselGameCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit,
    coverHeight: Dp = SpSpacing.CoverMediumHeight,
    rating: Double = 0.0,
    isFavorite: Boolean = false,
    isInPlayLater: Boolean = false,
    variantCount: Int = 0,
    testTag: String? = null,
) {
    SpGameCard(
        title = title,
        subtitle = subtitle,
        coverUrl = coverUrl,
        onClick = onClick,
        coverAspectRatio = null,
        coverHeight = coverHeight,
        rating = rating,
        isFavorite = isFavorite,
        isInPlayLater = isInPlayLater,
        variantCount = variantCount,
        testTag = testTag,
    )
}
