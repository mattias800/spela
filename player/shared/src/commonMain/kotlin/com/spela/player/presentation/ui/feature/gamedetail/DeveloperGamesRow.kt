package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.runtime.Composable
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpGameCard
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
internal fun DeveloperGamesSection(
    games: List<DeveloperGame>,
    developerName: String?,
    onGameSelected: (String) -> Unit,
) {
    if (games.isEmpty()) return

    val title = if (developerName != null) "More from $developerName" else "More from Developer"

    SpTitledSection(
        title = title,
        icon = Icons.Outlined.Business,
        edgeToEdgeContent = true,
    ) {
        SpCarousel(
            contentPadding = PaddingValues(horizontal = SpSpacing.XLarge),
        ) {
            items(games, key = { it.id }) { game ->
                DeveloperGameCard(
                    game = game,
                    onClick = { onGameSelected(game.id) },
                )
            }
        }
    }
}

/** ROLE component — a game card in Developer detail. Delegates to [SpGameCard]. */
@Composable
private fun DeveloperGameCard(
    game: DeveloperGame,
    onClick: () -> Unit,
) {
    SpGameCard(
        title = game.title,
        subtitle = game.consoleName,
        coverUrl = game.coverUrl,
        onClick = onClick,
    )
}
