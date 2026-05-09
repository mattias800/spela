package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import com.spela.player.presentation.ui.components.SpLazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spela.player.presentation.intent.ChallengeIntent
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.challenge.SpChallengeCard
import com.spela.player.presentation.ui.components.challenge.SpChallengeCardSkeleton
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.ChallengeListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeListScreen(
    viewModel: ChallengeListViewModel,
    gameId: String,
    gameTitle: String = "Challenges",
    onChallengeSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(gameId) {
        viewModel.onIntent(ChallengeIntent.LoadGameChallenges(gameId))
    }

    PlatformBackHandler(onBack = onBack)

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
                    title = gameTitle,
                    showBack = true,
                    onBack = onBack,
                )
            }

        val focusMemory = rememberFocusMemoryState()
        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        if (state.isLoadingGameChallenges && state.gameChallenges.isEmpty()) {
            // Skeleton grid while loading
            SpLazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = SpSpacing.ChallengeCellMinWidth),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(SpSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                items(6) { SpChallengeCardSkeleton() }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = state.isLoadingGameChallenges && state.gameChallenges.isNotEmpty(),
                onRefresh = { viewModel.onIntent(ChallengeIntent.LoadGameChallenges(gameId)) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.gameChallenges.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyStates.NoChallenges()
                    }
                } else {
                    SpLazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = SpSpacing.ChallengeCellMinWidth),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(SpSpacing.ScreenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        items(
                            state.gameChallenges,
                            key = { it.id },
                        ) { challenge ->
                            SpChallengeCard(
                                challenge = challenge,
                                onClick = { onChallengeSelected(challenge.id) },
                                modifier = Modifier.focusRestoreItem(
                                    key = challenge.id,
                                    isDefault = challenge == state.gameChallenges.firstOrNull(),
                                ),
                            )
                        }
                    }
                }
            }
        }
        } // CompositionLocalProvider
        }
    }
}
