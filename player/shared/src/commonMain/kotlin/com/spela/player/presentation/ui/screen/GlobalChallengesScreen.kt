package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spela.player.presentation.intent.ChallengeIntent
import com.spela.player.presentation.state.ChallengeTab
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.challenge.SpChallengeCard
import com.spela.player.presentation.ui.components.challenge.SpChallengeCardSkeleton
import com.spela.player.presentation.ui.feature.challenges.ChallengeFilterBar
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ChallengeListViewModel

private val tabs = listOf(
    ChallengeTab.POPULAR to "Popular",
    ChallengeTab.RECENT to "Recent",
    ChallengeTab.MINE to "My Challenges",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalChallengesScreen(
    viewModel: ChallengeListViewModel,
    onChallengeSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(ChallengeIntent.LoadConsoles)
        viewModel.onIntent(ChallengeIntent.SelectTab(state.selectedTab))
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
                    title = "Challenges",
                    showBack = true,
                    onBack = onBack,
                )
            }

        // Tabs
        val selectedTabIndex = tabs.indexOfFirst { it.first == state.selectedTab }
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = SpColor.Surface,
            contentColor = SpColor.OnSurface,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = SpColor.Link,
                    )
                }
            },
        ) {
            tabs.forEachIndexed { index, (tab, title) ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { viewModel.onIntent(ChallengeIntent.SelectTab(tab)) },
                    text = {
                        Text(
                            text = title,
                            style = SpTypography.LabelMedium,
                            color = if (selectedTabIndex == index) SpColor.Primary else SpColor.OnBackgroundTertiary,
                        )
                    },
                )
            }
        }

        // Filters (hidden on "My Challenges" tab)
        if (state.selectedTab != ChallengeTab.MINE) {
            Spacer(Modifier.height(SpSpacing.Small))
            ChallengeFilterBar(
                consoles = state.consoles,
                selectedConsoleFilter = state.selectedConsoleFilter,
                selectedDifficultyFilter = state.selectedDifficultyFilter,
                onFilterByConsole = { viewModel.onIntent(ChallengeIntent.FilterByConsole(it)) },
                onFilterByDifficulty = { viewModel.onIntent(ChallengeIntent.FilterByDifficulty(it)) },
            )
            Spacer(Modifier.height(SpSpacing.Small))
        }

        // Content
        val challenges = when (state.selectedTab) {
            ChallengeTab.MINE -> state.myChallenges
            else -> state.challenges
        }
        val isLoading = when (state.selectedTab) {
            ChallengeTab.MINE -> state.isLoadingMyChallenges
            else -> state.isLoading
        }

        val focusMemory = rememberFocusMemoryState()
        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        if (isLoading && challenges.isEmpty()) {
            LazyVerticalGrid(
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
                isRefreshing = isLoading && challenges.isNotEmpty(),
                onRefresh = { viewModel.onIntent(ChallengeIntent.SelectTab(state.selectedTab)) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (challenges.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.selectedTab == ChallengeTab.MINE) {
                            SpEmptyStates.NoChallenges()
                        } else {
                            SpEmptyStates.NoChallenges()
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = SpSpacing.ChallengeCellMinWidth),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(SpSpacing.ScreenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        items(
                            challenges,
                            key = { it.id },
                        ) { challenge ->
                            SpChallengeCard(
                                challenge = challenge,
                                onClick = { onChallengeSelected(challenge.id) },
                                modifier = (if (challenge == challenges.firstOrNull()) Modifier.autoFocus() else Modifier)
                                    .rememberFocus(challenge.id),
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
