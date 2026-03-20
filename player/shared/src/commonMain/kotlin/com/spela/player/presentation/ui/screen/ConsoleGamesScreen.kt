package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSearchField
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.library.GameGridItem
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.theme.spScreenBackground
import com.spela.player.presentation.viewmodel.GameListViewModel

private data class SortOption(val key: String, val label: String)
private val sortOptions = listOf(
    SortOption("title", "Title (A–Z)"),
    SortOption("rating", "Rating"),
    SortOption("releaseDate", "Release date"),
    SortOption("lastPlayed", "Recently played"),
)

/**
 * Full filterable game list for a single console.
 * Includes search, sort, and game grid with client-side filtering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleGamesScreen(
    consoleId: String,
    viewModel: GameListViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    val console = state.consoles.firstOrNull { it.id == consoleId }
    val consoleName = console?.name ?: "Games"

    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    PlatformBackHandler {
        if (isSearchVisible) {
            viewModel.onIntent(GameListIntent.Search(""))
            isSearchVisible = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(consoleId) {
        viewModel.onIntent(GameListIntent.SelectConsole(consoleId))
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) focusRequester.requestFocus()
    }

    // Client-side sort
    val sortedGames = remember(state.games, state.sortBy, state.sortOrder, state.searchQuery) {
        val filtered = if (state.searchQuery.length >= 2) {
            state.games.filter { it.title.contains(state.searchQuery, ignoreCase = true) }
        } else {
            state.games
        }
        val asc = state.sortOrder != "desc"
        when (state.sortBy) {
            "rating" -> if (asc) filtered.sortedBy { it.averageRating }
                        else filtered.sortedByDescending { it.averageRating }
            "releaseDate" -> if (asc) filtered.sortedBy { it.releaseDate ?: "" }
                             else filtered.sortedByDescending { it.releaseDate ?: "" }
            "lastPlayed" -> filtered.sortedByDescending { it.lastPlayedAt ?: "" }
            else -> if (asc) filtered.sortedBy { it.title.lowercase() }
                    else filtered.sortedByDescending { it.title.lowercase() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .spScreenBackground()
            .testTag("console_games_screen"),
    ) {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.onIntent(GameListIntent.SelectConsole(consoleId)) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SpSpacing.ScreenHorizontal,
                    end = SpSpacing.ScreenHorizontal,
                    top = SpSpacing.TopBarHeight + LocalTitleBarInset.current,
                    bottom = SpSpacing.Default,
                ),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Default),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
            ) {
                // Search field
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (isSearchVisible) {
                        SpSearchField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.onIntent(GameListIntent.Search(it)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = SpSpacing.Small)
                                .focusRequester(focusRequester),
                            placeholder = "Search $consoleName games...",
                            trailingIcon = {
                                SpIconButton(
                                    icon = Icons.Filled.Close,
                                    contentDescription = "Close search",
                                    onClick = {
                                        viewModel.onIntent(GameListIntent.Search(""))
                                        isSearchVisible = false
                                    },
                                )
                            },
                        )
                    }
                }

                // Games heading + sort
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = SpSpacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.searchQuery.length >= 2) "${sortedGames.size} results"
                                   else "${sortedGames.size} games",
                            style = SpTypography.HeadlineSmall,
                            color = SpColor.OnBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            SpIconButton(
                                icon = Icons.Filled.SwapVert,
                                contentDescription = "Sort games",
                                onClick = { showSortMenu = true },
                            )
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                sortOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            viewModel.onIntent(GameListIntent.SetSortBy(option.key))
                                            showSortMenu = false
                                        },
                                        leadingIcon = if (state.sortBy == option.key) {
                                            { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                                        } else null,
                                    )
                                }
                            }
                        }
                    }
                }

                // Loading / empty / game grid
                if (state.isLoading && state.games.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpLoadingIndicator(message = "Loading games...")
                        }
                    }
                } else if (sortedGames.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.searchQuery.length >= 2) {
                                SpEmptyStates.NoSearchResults(query = state.searchQuery)
                            } else {
                                SpEmptyStates.NoGamesInConsole(consoleName = consoleName)
                            }
                        }
                    }
                } else {
                    items(sortedGames, key = { it.id }) { game ->
                        GameGridItem(
                            game = game,
                            onClick = { onGameSelected(game.id) },
                            onRequestScrape = { viewModel.requestScrapeIfNeeded(it) },
                        )
                    }
                }
            }
        }

        // Top bar
        SpTopBar(
            title = "$consoleName Games",
            showBack = true,
            onBack = {
                if (isSearchVisible) {
                    viewModel.onIntent(GameListIntent.Search(""))
                    isSearchVisible = false
                } else {
                    onBack()
                }
            },
            actions = {
                SpIconButton(
                    icon = Icons.Filled.Search,
                    contentDescription = "Search games",
                    onClick = { isSearchVisible = !isSearchVisible },
                )
            },
        )

        // Error snackbar
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(GameListIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(GameListIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
