package com.spela.player.presentation.viewmodel

import com.spela.player.domain.usecase.GetActivityFeedUseCase
import com.spela.player.domain.usecase.GetOnlineUsersUseCase
import com.spela.player.domain.usecase.GetPlayHeatmapUseCase
import com.spela.player.domain.usecase.GetPublicProfileUseCase
import com.spela.player.presentation.intent.SocialIntent
import com.spela.player.presentation.state.SocialState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
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
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SocialState())
    val state: StateFlow<SocialState> = _state.asStateFlow()

    fun onIntent(intent: SocialIntent) {
        when (intent) {
            SocialIntent.LoadOnlineUsers -> loadOnlineUsers()
            SocialIntent.LoadActivityFeed -> loadActivityFeed()
            SocialIntent.RefreshAll -> refreshAll()
            SocialIntent.DismissError -> _state.update { it.copy(error = null) }
            is SocialIntent.LoadPublicProfile -> loadPublicProfile(intent.userId)
            SocialIntent.LoadFullActivityFeed -> loadFullActivityFeed()
            SocialIntent.LoadMoreActivity -> loadMoreActivity()
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
        _state.update { it.copy(isLoadingProfile = true, publicProfile = null, heatmapData = emptyList()) }
        scope.launch(dispatchers.io) {
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
        scope.launch(dispatchers.io) {
            try {
                val entries = getPlayHeatmapUseCase(userId).getOrDefault(emptyList())
                _state.update { it.copy(heatmapData = entries) }
            } catch (_: Exception) {
                // Best effort — heatmap is non-critical
            }
        }
    }
}
