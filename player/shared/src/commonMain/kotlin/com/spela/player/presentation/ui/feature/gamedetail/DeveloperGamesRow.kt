package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpGameCard
import com.spela.player.presentation.ui.components.SpTitledSection

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
            itemCount = games.size,
            memoryKey = "game_detail_developer",
            itemKey = { games[it].id },
        ) { index, focusRequester ->
            Box(modifier = Modifier.focusRequester(focusRequester)) {
                DeveloperGameCard(
                    game = games[index],
                    onClick = { onGameSelected(games[index].id) },
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
