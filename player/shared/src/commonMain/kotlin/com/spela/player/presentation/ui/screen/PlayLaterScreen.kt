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
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.sectionPillClearance
import com.spela.player.presentation.ui.components.rememberLoadingFlashDebounce
import com.spela.player.presentation.ui.feature.library.GameGridItem
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.GameListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayLaterScreen(
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
                // Touch mode gets a titled top bar with back. Gamepad mode has no
                // bar — the pill clearance lives in the grid's contentPadding below
                // so cards scroll under the floating pill.
                if (!isGamepad) {
                    SpTopBar(title = "Play Later", showBack = true, onBack = onBack)
                }

                if (state.isLoading && state.playLaterGames.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ScreenLoadingIndicator(message = "Loading play later...")
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = rememberLoadingFlashDebounce(state.isLoading),
                        onRefresh = { viewModel.onIntent(GameListIntent.LoadDashboard) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.playLaterGames.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                SpEmptyStates.NoPlayLater()
                            }
                        } else {
                            SpLazyVerticalGrid(
                                columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = SpSpacing.ScreenHorizontal,
                                    end = SpSpacing.ScreenHorizontal,
                                    top = sectionPillClearance() + SpSpacing.Default,
                                    bottom = SpSpacing.Default,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                                verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                            ) {
                                itemsIndexed(state.playLaterGames, key = { _, g -> g.id }) { index, game ->
                                    GameGridItem(
                                        game = game,
                                        onClick = { onGameSelected(game.id) },
                                        onRequestScrape = { viewModel.requestScrapeIfNeeded(it) },
                                        modifier = Modifier.focusRestoreItem(
                                            key = "play_later_screen_${game.id}",
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
