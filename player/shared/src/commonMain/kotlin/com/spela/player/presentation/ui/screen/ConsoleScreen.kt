package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.feature.explore.ConsoleEssentials
import com.spela.player.presentation.ui.feature.explore.ConsoleHiddenGems
import com.spela.player.presentation.ui.feature.explore.ConsoleTopDevelopers
import com.spela.player.presentation.ui.feature.home.ContinuePlayingRow
import com.spela.player.presentation.ui.feature.home.TopRatedRow
import com.spela.player.presentation.ui.feature.library.BiosWarningBanner
import com.spela.player.presentation.ui.feature.library.ConsoleHeroBanner
import com.spela.player.presentation.ui.feature.library.darken
import com.spela.player.presentation.ui.feature.library.getConsoleGradient
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.presentation.viewmodel.GameListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    consoleId: String,
    viewModel: GameListViewModel,
    exploreViewModel: ExploreViewModel? = null,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
    onDeveloperSelected: (String) -> Unit = {},
    onNavigateToConsoleSettings: () -> Unit = {},
    onBrowseAllGames: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    val console = state.consoles.firstOrNull { it.id == consoleId }
    val consoleName = console?.name ?: "Games"

    PlatformBackHandler { onBack() }

    LaunchedEffect(consoleId) {
        viewModel.onIntent(GameListIntent.SelectConsole(consoleId))
    }

    LaunchedEffect(consoleId) {
        exploreViewModel?.loadConsoleShowcase(consoleId)
    }

    val continuePlayingGames = remember(state.games) {
        state.games
            .filter { it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
            .take(5)
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

                    // Continue Playing section (most relevant — always first after hero)
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

                    // Browse All Games button (quick access near top)
                    if (state.games.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpButton(
                                text = "Browse All ${state.games.size} Games",
                                onClick = onBrowseAllGames,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Top Rated section
                    if (state.topRatedGames.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpTitledSection(
                                title = "Top Rated",
                                icon = Icons.Filled.Star,
                                edgeToEdgeContent = true,
                            ) {
                                TopRatedRow(
                                    games = state.topRatedGames,
                                    onGameSelected = onGameSelected,
                                    contentPadding = PaddingValues(horizontal = SpSpacing.Default),
                                )
                            }
                        }
                    }

                    // BIOS warning banner
                    if (consoleId in state.consolesWithMissingBios) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            BiosWarningBanner(
                                consoleName = consoleName,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Showcase sections (Essentials, Hidden Gems, Top Developers)
                    if (exploreViewModel != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ConsoleEssentials(exploreViewModel, onGameSelected)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ConsoleHiddenGems(exploreViewModel, onGameSelected)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ConsoleTopDevelopers(exploreViewModel, onDeveloperSelected)
                        }
                    }

                    // Loading state (no games loaded yet)
                    if (state.games.isEmpty() && state.isLoading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SpLoadingIndicator(message = "Loading games...")
                            }
                        }
                    } else if (state.games.isEmpty() && !state.isLoading) {
                        // Empty state (no games in console)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SpEmptyStates.NoGamesInConsole(consoleName = consoleName)
                            }
                        }
                    }
                }
            }

            // Fixed top bar overlaid on top of scrollable content
            SpTopBar(
                title = consoleName,
                showBack = true,
                onGradient = true,
                onBack = onBack,
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
