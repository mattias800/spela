package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.gamedetail.GameAchievementsSection
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.spScreenBackground
import com.spela.player.presentation.viewmodel.GameDetailViewModel

@Composable
fun GameAchievementsScreen(
    gameId: String,
    viewModel: GameDetailViewModel,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(gameId) {
        viewModel.onIntent(GameDetailIntent.LoadGame(gameId))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .spScreenBackground(),
    ) {
        SpTopBar(
            title = "Achievements",
            showBack = true,
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SpSpacing.Default),
        ) {
            GameAchievementsSection(
                achievements = state.achievements,
                progress = state.achievementProgress,
                timeline = state.achievementTimeline,
                leaderboard = state.achievementLeaderboard,
                viewMode = state.achievementsView,
                isLoading = state.isLoadingAchievements,
                onToggleView = { mode ->
                    viewModel.onIntent(GameDetailIntent.ToggleAchievementsView(mode))
                },
                achievementsWarning = state.gameDetail?.game?.achievementsWarning,
            )
        }
    }
}
