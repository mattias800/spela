package com.spela.player.presentation.ui.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.TopRatedGame
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.focusRequester
import com.spela.player.presentation.ui.components.SpAvailabilityGameCard
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
internal fun TopRatedRow(
    games: List<TopRatedGame>,
    onGameSelected: (String) -> Unit,
) {
    SpCarousel(itemCount = games.size) { index, focusRequester ->
        val game = games[index]
        Box(modifier = Modifier.focusRequester(focusRequester)) {
            TopRatedCard(
                game = game,
                onClick = if (game.localGameId != null) {
                    { onGameSelected(game.localGameId) }
                } else null,
            )
        }
    }
}

/** ROLE component — a top-rated game card with library availability. Delegates to [SpAvailabilityGameCard]. */
@Composable
private fun TopRatedCard(
    game: TopRatedGame,
    onClick: (() -> Unit)?,
) {
    SpAvailabilityGameCard(
        title = game.name,
        subtitle = game.consoleName,
        coverUrl = game.coverUrl,
        onClick = onClick ?: {},
        available = game.localGameId != null,
        coverHeight = SpSpacing.CoverMediumHeight,
        rating = game.rating,
    )
}
