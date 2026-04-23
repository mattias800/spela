package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.GameStatsRepository
import com.spela.player.presentation.state.AchievementsViewMode
import com.spela.player.presentation.state.GameDetailState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Achievements slice of [GameDetailViewModel]. Handles the three
 * view modes (grid / timeline / leaderboard) with their own
 * cached-once-then-reuse load logic. Shares the VM's [_state]
 * StateFlow so updates land directly.
 *
 * Extracted from the VM in the #691 refactor. Not a test seam (the
 * achievement methods are thin enough that mocking the repo is
 * enough) — the value is VM size + concern separation.
 */
class GameDetailAchievementManager(
    private val gameStatsRepository: GameStatsRepository,
    private val _state: MutableStateFlow<GameDetailState>,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    fun loadAchievements(gameId: String) {
        _state.update { it.copy(isLoadingAchievements = true) }
        scope.launch(dispatchers.io) {
            val achievements = gameStatsRepository.getGameAchievements(gameId).getOrDefault(emptyList())
            val progress = gameStatsRepository.getAchievementProgress(gameId).getOrDefault(emptyList())
            _state.update {
                it.copy(
                    achievements = achievements,
                    achievementProgress = progress,
                    isLoadingAchievements = false,
                )
            }
        }
    }

    fun loadAchievementTimeline(gameId: String) {
        if (_state.value.achievementTimeline != null) return
        _state.update { it.copy(isLoadingAchievements = true) }
        scope.launch(dispatchers.io) {
            gameStatsRepository.getAchievementTimeline(gameId).fold(
                onSuccess = { timeline ->
                    _state.update { it.copy(achievementTimeline = timeline, isLoadingAchievements = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingAchievements = false) }
                },
            )
        }
    }

    fun loadAchievementLeaderboard(gameId: String) {
        if (_state.value.achievementLeaderboard.isNotEmpty()) return
        _state.update { it.copy(isLoadingAchievements = true) }
        scope.launch(dispatchers.io) {
            gameStatsRepository.getAchievementLeaderboard(gameId).fold(
                onSuccess = { leaderboard ->
                    _state.update { it.copy(achievementLeaderboard = leaderboard, isLoadingAchievements = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingAchievements = false) }
                },
            )
        }
    }

    /**
     * Switch the achievements view and lazy-load the matching
     * backing data. [currentGameId] is the VM's field, passed
     * through because the mode toggle isn't game-scoped itself.
     */
    fun toggleAchievementsView(mode: AchievementsViewMode, currentGameId: String?) {
        _state.update { it.copy(achievementsView = mode) }
        val gameId = currentGameId ?: return
        when (mode) {
            AchievementsViewMode.TIMELINE -> loadAchievementTimeline(gameId)
            AchievementsViewMode.LEADERBOARD -> loadAchievementLeaderboard(gameId)
            AchievementsViewMode.GRID -> { /* Already loaded */ }
        }
    }
}
