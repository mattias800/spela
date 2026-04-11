package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spela.player.presentation.intent.StatsIntent
import com.spela.player.presentation.ui.feature.stats.ActivePlayerItem
import com.spela.player.presentation.ui.feature.stats.MostPlayedGameItem
import com.spela.player.presentation.ui.feature.stats.PersonalStatsSection
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpSectionHeader
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onGameSelected: (String) -> Unit,
    onUserSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(StatsIntent.LoadStats)
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    SpScreen {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (isGamepad) {
                SpScreenTopSpacer()
            } else {
                SpTopBar(
                    title = "Stats",
                    showBack = true,
                    onBack = onBack,
                )
            }

            if (state.isLoading && state.mostPlayedGames.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading stats...")
                }
            } else {
                val isEmpty = state.mostPlayedGames.isEmpty() && state.activePlayers.isEmpty() && state.personalStats == null

                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.onIntent(StatsIntent.LoadStats) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (isEmpty && !state.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpEmptyState(
                                icon = Icons.Filled.BarChart,
                                title = "No stats yet",
                                message = "Play some games and stats will appear here",
                            )
                        }
                    } else {
                        val focusMemory = rememberFocusMemoryState()
                        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = SpSpacing.Default),
                        ) {
                            // Personal Stats section
                            if (state.personalStats != null) {
                                item {
                                    PersonalStatsSection(
                                        stats = state.personalStats!!,
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    )
                                    Spacer(Modifier.height(SpSpacing.XXLarge))
                                }
                            }

                            // Most Played Games section
                            if (state.mostPlayedGames.isNotEmpty()) {
                                item {
                                    SpSectionHeader(
                                        title = "Most Played Games",
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                }

                                itemsIndexed(
                                    state.mostPlayedGames,
                                    key = { _, item -> "game-${item.game.id}" },
                                ) { index, item ->
                                    MostPlayedGameItem(
                                        rank = index + 1,
                                        item = item,
                                        onClick = { onGameSelected(item.game.id) },
                                        modifier = (if (index == 0) Modifier.autoFocus() else Modifier)
                                            .rememberFocus("game_${item.game.id}")
                                            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall),
                                    )
                                }

                                item {
                                    Spacer(Modifier.height(SpSpacing.XXLarge))
                                }
                            }

                            // Most Active Players section
                            if (state.activePlayers.isNotEmpty()) {
                                item {
                                    SpSectionHeader(
                                        title = "Most Active Players",
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                }

                                itemsIndexed(
                                    state.activePlayers,
                                    key = { _, item -> "player-${item.userId}" },
                                ) { index, item ->
                                    ActivePlayerItem(
                                        rank = index + 1,
                                        item = item,
                                        onClick = { onUserSelected(item.userId) },
                                        modifier = Modifier.rememberFocus("player_${item.userId}")
                                            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall),
                                    )
                                }
                            }
                        }
                        } // CompositionLocalProvider
                    }
                }
            }
        }

        // Error snackbar
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(StatsIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(StatsIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
