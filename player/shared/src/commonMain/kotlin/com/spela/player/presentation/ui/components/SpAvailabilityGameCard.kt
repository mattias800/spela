package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * CONTENT component — a game card with library availability awareness.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Wraps [SpGameCard] and adds:
 * - "Not in library" badge over cover art when not available locally
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
    coverHeight: Dp? = null,
    rating: Double = 0.0,
    isFavorite: Boolean = false,
    isInPlayLater: Boolean = false,
    width: Dp = SpSpacing.CoverMediumWidth,
    testTag: String? = null,
) {
    SpGameCard(
        title = title,
        subtitle = subtitle,
        coverUrl = coverUrl,
        onClick = if (available) onClick else ({}),
        coverAspectRatio = if (coverHeight != null) null else 0.75f,
        coverHeight = coverHeight,
        rating = rating,
        isFavorite = isFavorite,
        isInPlayLater = isInPlayLater,
        width = width,
        testTag = testTag,
        coverBadge = if (!available) {
            { SpCoverBadge(text = "Not in library") }
        } else null,
    )
}
