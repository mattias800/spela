package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * ROLE component — a game card for grid layouts.
 *
 * Layer 3 in the component hierarchy (Design → Content → Role).
 * Fills the parent grid cell width. Cover art height is dynamic,
 * showing the full box art without cropping.
 *
 * Use in [LazyVerticalGrid] with [GridCells.Adaptive].
 */
@Composable
fun SpGridGameCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit,
    rating: Double = 0.0,
    isFavorite: Boolean = false,
    isInPlayLater: Boolean = false,
    variantCount: Int = 0,
    testTag: String? = null,
    modifier: Modifier = Modifier,
) {
    SpGameCard(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        coverUrl = coverUrl,
        onClick = onClick,
        coverAspectRatio = if (coverUrl == null) 0.75f else null,
        rating = rating,
        isFavorite = isFavorite,
        isInPlayLater = isInPlayLater,
        fillWidth = true,
        variantCount = variantCount,
        testTag = testTag,
    )
}
