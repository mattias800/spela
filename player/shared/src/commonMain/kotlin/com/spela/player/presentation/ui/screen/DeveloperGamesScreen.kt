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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSearchField
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.library.GameGridItem
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.theme.spScreenBackground
import com.spela.player.presentation.viewmodel.ExploreViewModel

private data class DeveloperGamesSortOption(val key: String, val label: String)
private val sortOptions = listOf(
    DeveloperGamesSortOption("title", "Title (A\u2013Z)"),
    DeveloperGamesSortOption("rating", "Rating"),
    DeveloperGamesSortOption("releaseDate", "Release date"),
)

/**
 * Full filterable game list for a developer or publisher.
 * Includes search, sort, and game grid with client-side filtering.
 */
@Composable
fun DeveloperGamesScreen(
    name: String,
    isDeveloper: Boolean = true,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.developerDetailState.collectAsState()

    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val sortedGames = state.sortedFilteredGames

    PlatformBackHandler {
        if (isSearchVisible) {
            viewModel.setDeveloperGamesSearch("")
            isSearchVisible = false
        } else {
            onBack()
        }
    }

    // Load data if not already loaded
    LaunchedEffect(name, isDeveloper) {
        if (state.detail == null && !state.isLoading) {
            if (isDeveloper) {
                viewModel.loadDeveloperDetail(name)
            } else {
                viewModel.loadPublisherDetail(name)
            }
        }
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .spScreenBackground()
            .testTag("developer_games_screen"),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
            modifier = Modifier.fillMaxSize().testTag("developer_games_grid"),
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
                        value = state.gamesSearchQuery,
                        onValueChange = { viewModel.setDeveloperGamesSearch(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SpSpacing.Small)
                            .focusRequester(focusRequester)
                            .testTag("developer_games_search"),
                        placeholder = "Search $name games...",
                        trailingIcon = {
                            SpIconButton(
                                icon = Icons.Filled.Close,
                                contentDescription = "Close search",
                                onClick = {
                                    viewModel.setDeveloperGamesSearch("")
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
                        text = if (state.gamesSearchQuery.length >= 2) "${sortedGames.size} results"
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
                                        viewModel.setDeveloperGamesSort(option.key)
                                        showSortMenu = false
                                    },
                                    leadingIcon = if (state.gamesSortBy == option.key) {
                                        { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }

            // Loading / empty / game grid
            if (state.isLoading && state.detail == null) {
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
                        if (state.gamesSearchQuery.length >= 2) {
                            SpEmptyStates.NoSearchResults(query = state.gamesSearchQuery)
                        } else {
                            SpEmptyStates.NoGamesInConsole(consoleName = name)
                        }
                    }
                }
            } else {
                items(sortedGames, key = { it.id }) { game ->
                    GameGridItem(
                        game = game,
                        onClick = { onGameSelected(game.id) },
                    )
                }
            }
        }

        // Top bar
        SpTopBar(
            title = name,
            showBack = true,
            onBack = {
                if (isSearchVisible) {
                    viewModel.setDeveloperGamesSearch("")
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
    }
}
