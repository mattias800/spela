package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.feature.home.ContinuePlayingRow
import com.spela.player.presentation.ui.feature.library.BiosWarningBanner
import com.spela.player.presentation.ui.feature.library.ConsoleHeroBanner
import com.spela.player.presentation.ui.feature.library.GameGridItem
import com.spela.player.presentation.ui.feature.library.darken
import com.spela.player.presentation.ui.feature.library.getConsoleGradient
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameListViewModel

private data class SortOption(val key: String, val label: String)
private val sortOptions = listOf(
    SortOption("title",       "Title (A–Z)"),
    SortOption("rating",      "Rating"),
    SortOption("releaseDate", "Release date"),
    SortOption("lastPlayed",  "Recently played"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    consoleId: String,
    viewModel: GameListViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToConsoleSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    val console = state.consoles.firstOrNull { it.id == consoleId }
    val consoleName = console?.name ?: "Games"

    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Back handler: close search first, then navigate back
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

    // Auto-focus search field when it becomes visible
    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            focusRequester.requestFocus()
        }
    }

    val continuePlayingGames = remember(state.games) {
        state.games
            .filter { it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
            .take(5)
    }

    // Client-side sort applied on top of whatever order the server returns.
    val sortedGames = remember(state.games, state.sortBy, state.sortOrder) {
        val asc = state.sortOrder != "desc"
        when (state.sortBy) {
            "rating"      -> if (asc) state.games.sortedBy { it.averageRating }
                             else state.games.sortedByDescending { it.averageRating }
            "releaseDate" -> if (asc) state.games.sortedBy { it.releaseDate ?: "" }
                             else state.games.sortedByDescending { it.releaseDate ?: "" }
            "lastPlayed"  -> state.games.sortedByDescending { it.lastPlayedAt ?: "" }
            else          -> if (asc) state.games.sortedBy { it.title.lowercase() }
                             else state.games.sortedByDescending { it.title.lowercase() }
        }
    }

    // Darkened version of the console's brand gradient for the full-screen background
    val screenGradientColors = if (console != null) {
        val (from, to) = getConsoleGradient(console.abbreviation, console.colorTheme)
        listOf(from.darken(0.65f), to.darken(0.65f))
    } else {
        listOf(SpColor.Background, SpColor.Background)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val d = (size.width + size.height) * 0.25f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = screenGradientColors,
                            start = Offset(cx - d, cy - d),
                            end = Offset(cx + d, cy + d),
                        ),
                    )
                },
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
                    // Console hero banner (scrolls with content, includes info section)
                    if (console != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ConsoleHeroBanner(
                                console = console,
                                modifier = Modifier.padding(top = SpSpacing.Small),
                            )
                        }
                    }

                    // Continue Playing section
                    if (continuePlayingGames.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpTitledSection(
                                title = "Continue Playing",
                                icon = Icons.Filled.PlayArrow,
                                edgeToEdgeContent = true,
                            ) {
                                ContinuePlayingRow(
                                    games = continuePlayingGames,
                                    onGameSelected = onGameSelected,
                                    contentPadding = PaddingValues(horizontal = SpSpacing.Default),
                                )
                            }
                        }
                    }

                    // Search field
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            AnimatedVisibility(
                                visible = isSearchVisible,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
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
                    }

                    // BIOS warning banner
                    if (consoleId in state.consolesWithMissingBios) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            BiosWarningBanner(
                                consoleName = consoleName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = SpSpacing.Small),
                            )
                        }
                    }

                    // Loading state
                    if (state.isLoading && state.games.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(300.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SpLoadingIndicator(message = "Loading games...")
                            }
                        }
                    } else if (state.games.isEmpty()) {
                        // Empty state
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
                        // "Games" heading + sort dropdown (#2)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = SpSpacing.Default, bottom = SpSpacing.Small),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${sortedGames.size} games",
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

                        // Game grid items (sorted)
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

            // Fixed top bar overlaid on top of scrollable content
            SpTopBar(
                title = consoleName,
                showBack = true,
                onGradient = true,
                onBack = {
                    if (isSearchVisible) {
                        viewModel.onIntent(GameListIntent.Search(""))
                        isSearchVisible = false
                    } else {
                        onBack()
                    }
                },
                titleLeadingContent = if (console?.iconUrl?.isNotEmpty() == true) {
                    {
                        AsyncImage(
                            model = console.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                } else null,
                actions = {
                    SpIconButton(
                        icon = Icons.Filled.Search,
                        contentDescription = "Search games",
                        onGradient = true,
                        onClick = { isSearchVisible = !isSearchVisible },
                        badge = if (!isSearchVisible && state.searchQuery.isNotEmpty()) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .background(SpColor.Primary, shape = CircleShape),
                                )
                            }
                        } else null,
                    )
                    SpIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Console settings",
                        onGradient = true,
                        onClick = onNavigateToConsoleSettings,
                    )
                },
            )
        }

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
    } // outer Box
}
