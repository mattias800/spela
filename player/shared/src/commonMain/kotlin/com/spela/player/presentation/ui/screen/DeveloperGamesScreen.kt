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
import com.spela.player.presentation.ui.components.SpLazyVerticalGrid
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
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpSearchField
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.library.GameGridItem
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.domain.model.Game
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
    val developerState by viewModel.developerDetailState.collectAsState()
    val publisherState by viewModel.publisherDetailState.collectAsState()

    // Unify the two side-specific states into a small view object for this
    // screen. The fields consumed here (detail?.let present, sortedFilteredGames,
    // gamesSearchQuery, gamesSortBy, isLoading) have identical shape on both
    // sides; only the detail type differs and this screen only needs a
    // non-null check.
    data class GamesView(
        val hasDetail: Boolean,
        val isLoading: Boolean,
        val sortedFilteredGames: List<Game>,
        val gamesSearchQuery: String,
        val gamesSortBy: String,
    )

    val state = if (isDeveloper) {
        GamesView(
            hasDetail = developerState.detail != null,
            isLoading = developerState.isLoading,
            sortedFilteredGames = developerState.sortedFilteredGames,
            gamesSearchQuery = developerState.gamesSearchQuery,
            gamesSortBy = developerState.gamesSortBy,
        )
    } else {
        GamesView(
            hasDetail = publisherState.detail != null,
            isLoading = publisherState.isLoading,
            sortedFilteredGames = publisherState.sortedFilteredGames,
            gamesSearchQuery = publisherState.gamesSearchQuery,
            gamesSortBy = publisherState.gamesSortBy,
        )
    }

    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val sortedGames = state.sortedFilteredGames

    // Both sides accept the same filter setters (they just write to the
    // appropriate state flow — see ExploreViewModel.setPublisher* helpers).
    val setSearch: (String) -> Unit = { query ->
        if (isDeveloper) viewModel.setDeveloperGamesSearch(query)
        else viewModel.setPublisherGamesSearchQuery(query)
    }
    val setSort: (String) -> Unit = { sortBy ->
        if (isDeveloper) viewModel.setDeveloperGamesSort(sortBy)
        else viewModel.setPublisherGamesSortBy(sortBy)
    }

    PlatformBackHandler {
        if (isSearchVisible) {
            setSearch("")
            isSearchVisible = false
        } else {
            onBack()
        }
    }

    // Load data if not already loaded
    LaunchedEffect(name, isDeveloper) {
        if (!state.hasDetail && !state.isLoading) {
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

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    SpScreen(modifier = Modifier.testTag("developer_games_screen")) {
        val focusMemory = rememberFocusMemoryState()
        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        SpLazyVerticalGrid(
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
                        onValueChange = { setSearch(it) },
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
                                    setSearch("")
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
                            modifier = Modifier.focusRestoreItem(
                                key = "developer_games_sort",
                                isDefault = true,
                            ),
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            sortOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        setSort(option.key)
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
            if (state.isLoading && !state.hasDetail) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ScreenLoadingIndicator(message = "Loading games...")
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
                        onPlatformSelected = onGameSelected,
                    )
                }
            }
        }

        // Top bar
        if (isGamepad) {
            SpScreenTopSpacer()
        } else {
            SpTopBar(
                title = name,
                showBack = true,
                onBack = {
                    if (isSearchVisible) {
                        setSearch("")
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
        } // CompositionLocalProvider
    }
}
