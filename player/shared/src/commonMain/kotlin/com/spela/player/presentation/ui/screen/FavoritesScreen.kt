package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.GameListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: GameListViewModel,
    onGameSelected: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(GameListIntent.LoadDashboard)
    }

    if (state.isLoading && state.favoriteGames.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            SpLoadingIndicator(message = "Loading favorites...")
        }
    } else {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.onIntent(GameListIntent.LoadDashboard) },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.favoriteGames.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpEmptyStates.NoFavorites()
                }
            } else {
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
                    items(state.favoriteGames, key = { it.id }) { game ->
                        GameGridItem(
                            game = game,
                            onClick = { onGameSelected(game.id) },
                            onRequestScrape = { viewModel.requestScrapeIfNeeded(it) },
                        )
                    }
                }
            }
        }
    }
}
