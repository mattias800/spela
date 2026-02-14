package com.spela.player.presentation.viewmodel

import com.spela.player.domain.usecase.GetActivityFeedUseCase
import com.spela.player.domain.usecase.GetOnlineUsersUseCase
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
}
