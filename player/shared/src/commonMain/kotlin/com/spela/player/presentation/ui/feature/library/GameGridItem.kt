package com.spela.player.presentation.ui.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.SpGridGameCard

/**
 * ROLE component — a game card for use in grid layouts.
 *
 * Layer 3 in the component hierarchy (Design → Content → Role).
 * Delegates to [SpGridGameCard] which delegates to [SpGameCard].
 * Optionally triggers auto-scraping for games without cover art.
 */
@Composable
internal fun GameGridItem(
    game: Game,
    onClick: () -> Unit,
    onRequestScrape: ((Game) -> Unit)? = null,
) {
    // Trigger scrape for games with no cover art that haven't been scraped yet
    if (onRequestScrape != null && game.coverUrl == null && game.scrapeAttempts == 0) {
        LaunchedEffect(game.id) {
            onRequestScrape(game)
        }
    }

    SpGridGameCard(
        title = game.title,
        subtitle = game.consoleName,
        coverUrl = game.coverUrl,
        onClick = onClick,
        rating = game.averageRating,
        isFavorite = game.isFavorite,
        isInPlayLater = game.isInPlayLater,
        variantCount = game.variantCount,
        testTag = "game_grid_item_${game.id}",
    )
}
