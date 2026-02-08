package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.domain.model.Game
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSearchField
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameListViewModel

@Composable
fun ConsoleScreen(
    consoleId: String,
    viewModel: GameListViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    val consoleName = state.consoles
        .firstOrNull { it.id == consoleId }?.name ?: "Games"

    LaunchedEffect(consoleId) {
        viewModel.onIntent(GameListIntent.SelectConsole(consoleId))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(
            title = consoleName,
            showBack = true,
            onBack = onBack,
        )

        // Search bar
        SpSearchField(
            value = state.searchQuery,
            onValueChange = { viewModel.onIntent(GameListIntent.Search(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Small),
            placeholder = "Search $consoleName games...",
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SpLoadingIndicator(message = "Loading games...")
            }
        } else if (state.games.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No games found",
                        style = SpTypography.HeadlineMedium,
                        color = SpColor.OnBackgroundSecondary,
                    )
                    Spacer(Modifier.height(SpSpacing.Small))
                    Text(
                        text = "Games will appear here once the server scans your library",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }
        } else {
            val displayGames = if (state.searchQuery.length >= 2) {
                state.games
            } else {
                state.games
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = SpSpacing.ScreenHorizontal,
                    vertical = SpSpacing.Default,
                ),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
            ) {
                items(displayGames) { game ->
                    GameGridItem(
                        game = game,
                        onClick = { onGameSelected(game.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameGridItem(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(onClick = onClick) {
        Column {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = game.title,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier.padding(
                    horizontal = SpSpacing.Small,
                    vertical = SpSpacing.Small,
                ),
            ) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game.genre != null) {
                    Text(
                        text = game.genre,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
