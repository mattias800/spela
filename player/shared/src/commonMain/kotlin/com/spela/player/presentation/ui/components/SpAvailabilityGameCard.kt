package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * CONTENT component — a game card with library availability awareness.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Wraps [SpGameCard] and adds:
 * - Dimmed appearance (50% opacity) when the game is not available locally
 * - Disabled click when the game is not available
 *
 * Use this instead of [SpGameCard] when showing games that may or may
 * not exist in the user's library (e.g. Top Rated lists from IGDB,
 * Similar Games suggestions, Series/Franchise listings).
 *
 * Role components (TopRatedCard, SimilarGameCard) should delegate to
 * this — never implement availability logic themselves.
 */
@Composable
fun SpAvailabilityGameCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit,
    available: Boolean,
    coverAspectRatio: Float = 0.75f,
    rating: Double = 0.0,
    isFavorite: Boolean = false,
    isInPlayLater: Boolean = false,
    width: Dp = SpSpacing.CoverMediumWidth,
    testTag: String? = null,
) {
    Box(modifier = if (!available) Modifier.alpha(0.5f) else Modifier) {
        SpGameCard(
            title = title,
            subtitle = subtitle,
            coverUrl = coverUrl,
            onClick = if (available) onClick else ({}),
            coverAspectRatio = coverAspectRatio,
            rating = rating,
            isFavorite = isFavorite,
            isInPlayLater = isInPlayLater,
            width = width,
            testTag = testTag,
        )
    }
}
