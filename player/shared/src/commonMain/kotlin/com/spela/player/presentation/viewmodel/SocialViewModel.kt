package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ShowcaseAchievement
import com.spela.player.domain.model.UnlockedAchievement
import com.spela.player.domain.usecase.GetActivityFeedUseCase
import com.spela.player.domain.usecase.GetOnlineUsersUseCase
import com.spela.player.domain.usecase.GetPlayHeatmapUseCase
import com.spela.player.domain.usecase.GetPublicProfileUseCase
import com.spela.player.domain.usecase.GetPublicShowcaseUseCase
import com.spela.player.domain.usecase.GetUnlockedAchievementsUseCase
import com.spela.player.domain.usecase.UpdateShowcaseUseCase
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.presentation.intent.SocialIntent
import com.spela.player.presentation.state.SocialState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SocialViewModel(
    private val getOnlineUsersUseCase: GetOnlineUsersUseCase,
    private val getActivityFeedUseCase: GetActivityFeedUseCase,
    private val getPublicProfileUseCase: GetPublicProfileUseCase,
    private val getPlayHeatmapUseCase: GetPlayHeatmapUseCase,
    private val getPublicShowcaseUseCase: GetPublicShowcaseUseCase,
    private val getUnlockedAchievementsUseCase: GetUnlockedAchievementsUseCase,
    private val updateShowcaseUseCase: UpdateShowcaseUseCase,
    private val authRepository: AuthRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SocialState())
    val state: StateFlow<SocialState> = _state.asStateFlow()
    private var profileJob: Job? = null

    fun onIntent(intent: SocialIntent) {
        when (intent) {
            SocialIntent.LoadOnlineUsers -> loadOnlineUsers()
            SocialIntent.LoadActivityFeed -> loadActivityFeed()
            SocialIntent.RefreshAll -> refreshAll()
            SocialIntent.DismissError -> _state.update { it.copy(error = null) }
            is SocialIntent.LoadPublicProfile -> loadPublicProfile(intent.userId)
            SocialIntent.LoadFullActivityFeed -> loadFullActivityFeed()
            SocialIntent.LoadMoreActivity -> loadMoreActivity()
            SocialIntent.OpenShowcaseEditor -> openShowcaseEditor()
            SocialIntent.CloseShowcaseEditor -> _state.update { it.copy(isShowcaseEditorOpen = false) }
            is SocialIntent.ToggleShowcaseAchievement -> toggleShowcaseAchievement(intent.achievement)
            is SocialIntent.MoveShowcaseAchievement -> moveShowcaseAchievement(intent.index, intent.direction)
            SocialIntent.SaveShowcase -> saveShowcase()
            is SocialIntent.SetShowcaseSearchQuery -> _state.update { it.copy(showcaseSearchQuery = intent.query) }
        }
    }

    private fun loadOnlineUsers() {
        _state.update { it.copy(isLoadingOnline = true) }
        scope.launch(dispatchers.io) {
            getOnlineUsersUseCase().fold(
                onSuccess = { users ->
                    _state.update { it.copy(onlineUsers = users, isLoadingOnline = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingOnline = false) }
                },
            )
        }
    }

    private fun loadActivityFeed() {
        _state.update { it.copy(isLoadingActivity = true) }
        scope.launch(dispatchers.io) {
            getActivityFeedUseCase(page = 1, pageSize = 5).fold(
                onSuccess = { events ->
                    _state.update { it.copy(activityEvents = events, isLoadingActivity = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingActivity = false) }
                },
            )
        }
    }

    private fun refreshAll() {
        _state.update { it.copy(isLoadingOnline = true, isLoadingActivity = true) }
        scope.launch(dispatchers.io) {
            val users = getOnlineUsersUseCase().getOrDefault(emptyList())
            val events = getActivityFeedUseCase(page = 1, pageSize = 5).getOrDefault(emptyList())
            _state.update {
                it.copy(
                    onlineUsers = users,
                    activityEvents = events,
                    isLoadingOnline = false,
                    isLoadingActivity = false,
                )
            }
        }
    }

    private fun loadFullActivityFeed() {
        _state.update { it.copy(isLoadingFullActivity = true, fullActivityPage = 1) }
        scope.launch(dispatchers.io) {
            getActivityFeedUseCase(page = 1, pageSize = 20).fold(
                onSuccess = { events ->
                    _state.update {
                        it.copy(
                            fullActivityEvents = events,
                            isLoadingFullActivity = false,
                            fullActivityPage = 1,
                            hasMoreActivity = events.size >= 20,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingFullActivity = false) }
                },
            )
        }
    }

    private fun loadMoreActivity() {
        val currentState = _state.value
        if (currentState.isLoadingFullActivity || !currentState.hasMoreActivity) return
        val nextPage = currentState.fullActivityPage + 1
        _state.update { it.copy(isLoadingFullActivity = true) }
        scope.launch(dispatchers.io) {
            getActivityFeedUseCase(page = nextPage, pageSize = 20).fold(
                onSuccess = { events ->
                    _state.update {
                        it.copy(
                            fullActivityEvents = it.fullActivityEvents + events,
                            isLoadingFullActivity = false,
                            fullActivityPage = nextPage,
                            hasMoreActivity = events.size >= 20,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingFullActivity = false) }
                },
            )
        }
    }

    private fun loadPublicProfile(userId: String) {
        // Cancel any in-flight load for the previous profile so its
        // _state.update calls don't land after the screen has reset to a
        // different profile and clobber the new data.
        profileJob?.cancel()
        _state.update { it.copy(isLoadingProfile = true, publicProfile = null, heatmapData = emptyList(), showcaseAchievements = emptyList(), isOwnProfile = false) }
        profileJob = scope.launch(dispatchers.io) {
            // Detect own profile
            launch {
                authRepository.getCurrentUser().onSuccess { user ->
                    _state.update { it.copy(isOwnProfile = user.id == userId) }
                }
            }
            launch {
                getPublicProfileUseCase(userId).fold(
                    onSuccess = { profile ->
                        _state.update { it.copy(publicProfile = profile, isLoadingProfile = false) }
                    },
                    onFailure = { error ->
                        _state.update { it.copy(error = error.message, isLoadingProfile = false) }
                    },
                )
            }
            // Load heatmap data in parallel — best effort, non-critical
            launch {
                try {
                    val entries = getPlayHeatmapUseCase(userId).getOrDefault(emptyList())
                    _state.update { it.copy(heatmapData = entries) }
                } catch (_: Exception) {
                    // Best effort — heatmap is non-critical
                }
            }
            // Load achievement showcase in parallel — best effort, non-critical
            launch {
                try {
                    val entries = getPublicShowcaseUseCase(userId).getOrDefault(emptyList())
                    _state.update { it.copy(showcaseAchievements = entries) }
                } catch (_: Exception) {
                    // Best effort — showcase is non-critical
                }
            }
        }
    }

    private fun openShowcaseEditor() {
        _state.update { it.copy(isShowcaseEditorOpen = true, isLoadingUnlockedAchievements = true, showcaseSearchQuery = "") }
        // Initialize editor selections from current showcase
        val currentShowcase = _state.value.showcaseAchievements
        _state.update { it.copy(showcaseEditorSelections = currentShowcase) }
        // Load all unlocked achievements for the picker
        scope.launch(dispatchers.io) {
            getUnlockedAchievementsUseCase().fold(
                onSuccess = { achievements ->
                    _state.update { it.copy(allUnlockedAchievements = achievements, isLoadingUnlockedAchievements = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingUnlockedAchievements = false) }
                },
            )
        }
    }

    private fun toggleShowcaseAchievement(achievement: UnlockedAchievement) {
        _state.update { state ->
            val current = state.showcaseEditorSelections
            val existing = current.find { it.achievementRaId == achievement.achievementRaId }
            if (existing != null) {
                // Remove
                state.copy(showcaseEditorSelections = current.filter { it.achievementRaId != achievement.achievementRaId })
            } else if (current.size < 5) {
                // Add
                val newEntry = ShowcaseAchievement(
                    achievementRaId = achievement.achievementRaId,
                    raGameId = achievement.raGameId,
                    showcaseOrder = current.size,
                    title = achievement.title,
                    description = achievement.description,
                    points = achievement.points,
                    badgeUrl = achievement.badgeUrl,
                    rarityPercent = achievement.rarityPercent,
                    gameTitle = achievement.gameTitle,
                )
                state.copy(showcaseEditorSelections = current + newEntry)
            } else {
                state // Max 5 reached
            }
        }
    }

    private fun moveShowcaseAchievement(index: Int, direction: Int) {
        _state.update { state ->
            val list = state.showcaseEditorSelections.toMutableList()
            val newIndex = index + direction
            if (newIndex < 0 || newIndex >= list.size) return@update state
            val item = list.removeAt(index)
            list.add(newIndex, item)
            state.copy(showcaseEditorSelections = list)
        }
    }

    private fun saveShowcase() {
        val selections = _state.value.showcaseEditorSelections
        // Optimistic update
        _state.update { it.copy(showcaseAchievements = selections, isShowcaseEditorOpen = false, isSavingShowcase = true) }
        scope.launch(dispatchers.io) {
            updateShowcaseUseCase(selections).fold(
                onSuccess = { updated ->
                    _state.update { it.copy(showcaseAchievements = updated, isSavingShowcase = false) }
                },
                onFailure = {
                    _state.update { it.copy(isSavingShowcase = false) }
                },
            )
        }
    }
}
