package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.spela.player.presentation.ui.components.SpLazyColumn
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
import com.spela.player.presentation.ui.feature.stats.PersonalStatsSection
import com.spela.player.presentation.ui.feature.stats.mostActivePlayersStatsSection
import com.spela.player.presentation.ui.feature.stats.mostPlayedStatsSection
import com.spela.player.presentation.ui.feature.stats.topAchieversStatsSection
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.components.rememberLoadingFlashDebounce
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.sectionPillClearance
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
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
            if (!isGamepad) {
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
                    ScreenLoadingIndicator(message = "Loading stats...")
                }
            } else {
                val isEmpty = state.mostPlayedGames.isEmpty() && state.activePlayers.isEmpty() &&
                    state.personalStats == null && state.meshAchievers.isEmpty()

                PullToRefreshBox(
                    isRefreshing = rememberLoadingFlashDebounce(state.isLoading),
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
                        SpLazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = sectionPillClearance() + SpSpacing.Default,
                                bottom = SpSpacing.Default,
                            ),
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

                            // Most Played Games section (This server | Across servers).
                            // Its scope toggle is the default-focus element when the
                            // section renders (first focusable in composition order).
                            if (state.mostPlayedGames.isNotEmpty()) {
                                mostPlayedStatsSection(
                                    games = state.mostPlayedGames,
                                    meshStats = state.meshMostPlayed,
                                    scope = state.mostPlayedScope,
                                    isLoadingMesh = state.isLoadingMeshMostPlayed,
                                    isDefaultFocus = true,
                                    onScopeChange = { viewModel.onIntent(StatsIntent.SetMostPlayedScope(it)) },
                                    onGameSelected = onGameSelected,
                                )
                            }

                            // Most Active Players section. Its toggle is the default
                            // only when the Most Played section above isn't rendered.
                            if (state.activePlayers.isNotEmpty()) {
                                mostActivePlayersStatsSection(
                                    players = state.activePlayers,
                                    meshStats = state.meshActivePlayers,
                                    scope = state.activePlayersScope,
                                    isLoadingMesh = state.isLoadingMeshActivePlayers,
                                    isDefaultFocus = state.mostPlayedGames.isEmpty(),
                                    onScopeChange = { viewModel.onIntent(StatsIntent.SetActivePlayersScope(it)) },
                                    onUserSelected = onUserSelected,
                                )
                            }

                            // Top Achievers (federated). Default focus only when
                            // it's the first rendered section.
                            if (state.meshAchievers.isNotEmpty()) {
                                topAchieversStatsSection(
                                    achievers = state.meshAchievers,
                                    scope = state.achieversScope,
                                    isLoading = state.isLoadingMeshAchievers,
                                    isDefaultFocus = state.mostPlayedGames.isEmpty() && state.activePlayers.isEmpty(),
                                    onScopeChange = { viewModel.onIntent(StatsIntent.SetAchieversScope(it)) },
                                )
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
