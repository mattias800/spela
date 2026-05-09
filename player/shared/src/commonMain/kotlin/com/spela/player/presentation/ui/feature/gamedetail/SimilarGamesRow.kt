package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.SimilarGame
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.focusRequester
import com.spela.player.presentation.ui.components.SpAvailabilityGameCard
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
internal fun SimilarGamesSection(
    games: List<SimilarGame>,
    onGameSelected: (String) -> Unit,
) {
    if (games.isEmpty()) return

    SpTitledSection(
        title = "Similar Games",
        icon = Icons.Outlined.Explore,
        edgeToEdgeContent = true,
    ) {
        SpCarousel(
            itemCount = games.size,
            memoryKey = "game_detail_similar",
            itemKey = { games[it].localGameId ?: games[it].name },
        ) { index, focusRequester ->
            val game = games[index]
            Box(modifier = Modifier.focusRequester(focusRequester)) {
                SimilarGameCard(
                    game = game,
                    onClick = if (game.localGameId != null) {
                        { onGameSelected(game.localGameId) }
                    } else null,
                )
            }
        }
    }
}

/** ROLE component — a similar game card with library availability. Delegates to [SpAvailabilityGameCard]. */
@Composable
private fun SimilarGameCard(
    game: SimilarGame,
    onClick: (() -> Unit)?,
) {
    SpAvailabilityGameCard(
        title = game.name,
        subtitle = "",
        coverUrl = game.coverUrl,
        onClick = onClick ?: {},
        available = game.localGameId != null,
        coverHeight = SpSpacing.CoverMediumHeight,
        rating = game.rating,
    )
}
