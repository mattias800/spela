package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.state.ViewMode
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSearchField
import com.spela.player.presentation.ui.feature.library.GameGridItem
import com.spela.player.presentation.ui.feature.library.GameLibraryControls
import com.spela.player.presentation.ui.feature.library.GameListRowItem
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.GameListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllGamesScreen(
    viewModel: GameListViewModel,
    onGameSelected: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (state.consoles.isEmpty()) {
            viewModel.onIntent(GameListIntent.LoadConsoles)
        }
        viewModel.onIntent(GameListIntent.Search(""))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SpSearchField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                viewModel.onIntent(GameListIntent.Search(query))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Small),
            placeholder = "Search all games...",
        )

        GameLibraryControls(
            consoles = state.consoles,
            selectedConsoleFilter = state.selectedConsoleFilter,
            sortBy = state.sortBy,
            sortOrder = state.sortOrder,
            viewMode = state.viewMode,
            onFilterByConsole = { viewModel.onIntent(GameListIntent.FilterByConsole(it)) },
            onSortByChanged = { viewModel.onIntent(GameListIntent.SetSortBy(it)) },
            onSortOrderChanged = { viewModel.onIntent(GameListIntent.SetSortOrder(it)) },
            onToggleViewMode = { viewModel.onIntent(GameListIntent.ToggleViewMode) },
            modifier = Modifier.padding(vertical = SpSpacing.Small),
        )

        Spacer(Modifier.height(SpSpacing.Small))

        if (state.isLoading && state.games.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SpLoadingIndicator(message = "Loading games...")
            }
        } else {
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.onIntent(GameListIntent.Search(searchQuery)) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.games.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (searchQuery.length >= 2) {
                            SpEmptyStates.NoSearchResults(query = searchQuery)
                        } else {
                            SpEmptyStates.EmptyLibrary()
                        }
                    }
                } else if (state.viewMode == ViewMode.GRID) {
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
                        items(state.games, key = { it.id }) { game ->
                            GameGridItem(
                                game = game,
                                onClick = { onGameSelected(game.id) },
                                onRequestScrape = { viewModel.requestScrapeIfNeeded(it) },
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = SpSpacing.ScreenHorizontal,
                            vertical = SpSpacing.Default,
                        ),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                    ) {
                        items(state.games, key = { it.id }) { game ->
                            GameListRowItem(
                                game = game,
                                onClick = { onGameSelected(game.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
