package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import com.spela.player.presentation.ui.components.SpLazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.rememberLoadingFlashDebounce
import com.spela.player.presentation.ui.feature.library.GameGridItem
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.GameListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinuePlayingScreen(
    viewModel: GameListViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(GameListIntent.LoadDashboard)
    }

    val focusMemory = rememberFocusMemoryState()
    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        SpScreen {
            Column(modifier = Modifier.fillMaxSize()) {
                // Standard screen header: gamepad mode clears the floating section
                // pill; touch mode gets a titled top bar with back. Matches the
                // rest of the app's screens (#1525).
                if (isGamepad) {
                    SpScreenTopSpacer()
                } else {
                    SpTopBar(title = "Continue Playing", showBack = true, onBack = onBack)
                }

                if (state.isLoading && state.recentGames.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ScreenLoadingIndicator(message = "Loading...")
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = rememberLoadingFlashDebounce(state.isLoading),
                        onRefresh = { viewModel.onIntent(GameListIntent.LoadDashboard) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.recentGames.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                SpEmptyStates.NoRecentGames()
                            }
                        } else {
                            SpLazyVerticalGrid(
                                columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    horizontal = SpSpacing.ScreenHorizontal,
                                    vertical = SpSpacing.Default,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                                verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                            ) {
                                itemsIndexed(state.recentGames, key = { _, g -> g.id }) { index, game ->
                                    GameGridItem(
                                        game = game,
                                        onClick = { onGameSelected(game.id) },
                                        onRequestScrape = { viewModel.requestScrapeIfNeeded(it) },
                                        modifier = Modifier.focusRestoreItem(
                                            key = "continue_playing_screen_${game.id}",
                                            isDefault = index == 0,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
