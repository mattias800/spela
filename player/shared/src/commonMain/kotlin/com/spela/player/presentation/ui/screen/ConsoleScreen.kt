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
import com.spela.player.presentation.ui.components.SpSectionList
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.SpGameGrid
import com.spela.player.presentation.ui.components.SpGridGameCard
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
                SpSectionList(
                    modifier = Modifier.fillMaxSize(),
                    topPadding = SpSpacing.TopBarHeight + LocalTitleBarInset.current,
                ) {
                    // Console hero banner
                    if (console != null) {
                        item {
                            ConsoleHeroBanner(
                                console = console,
                                onBrowseGames = if (state.games.size > 15) onBrowseAllGames else null,
                            )
                        }
                    }

                    // Continue Playing (most relevant — always first)
                    if (continuePlayingGames.isNotEmpty()) {
                        item {
                            SpTitledSection(
                                title = "Continue Playing",
                                icon = Icons.Filled.PlayArrow,
                                edgeToEdgeContent = true,
                            ) {
                                ContinuePlayingRow(
                                    games = continuePlayingGames,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // BIOS warning
                    if (consoleId in state.consolesWithMissingBios) {
                        item {
                            BiosWarningBanner(
                                consoleName = consoleName,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Curated content
                    if (exploreViewModel != null) {
                        item { ConsoleEssentials(exploreViewModel, onGameSelected) }
                        item { ConsoleHiddenGems(exploreViewModel, onGameSelected) }
                    }

                    // Top Rated
                    if (state.topRatedGames.isNotEmpty()) {
                        item {
                            SpTitledSection(
                                title = "Top Rated",
                                icon = Icons.Filled.Star,
                                edgeToEdgeContent = true,
                            ) {
                                TopRatedRow(
                                    games = state.topRatedGames,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Top Developers
                    if (exploreViewModel != null) {
                        item { ConsoleTopDevelopers(exploreViewModel, onDeveloperSelected) }
                    }

                    // All Games — inline grid at bottom for small libraries (≤15 games)
                    // For larger libraries, the "Browse games" button is in the hero banner
                    if (state.games.isNotEmpty() && state.games.size <= 15) {
                        item {
                            SpTitledSection(title = "All Games") {
                                SpGameGrid(
                                    items = state.games.map { game ->
                                        {
                                            SpGridGameCard(
                                                title = game.title,
                                                subtitle = game.consoleName,
                                                coverUrl = game.coverUrl,
                                                onClick = { onGameSelected(game.id) },
                                                rating = game.rating,
                                                isFavorite = game.isFavorite,
                                                isInPlayLater = game.isInPlayLater,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

                    // Loading / Empty
                    if (state.games.isEmpty() && state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SpLoadingIndicator(message = "Loading games...")
                            }
                        }
                    } else if (state.games.isEmpty() && !state.isLoading) {
                        item {
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
