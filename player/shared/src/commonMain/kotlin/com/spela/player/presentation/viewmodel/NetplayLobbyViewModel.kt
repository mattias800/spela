package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.NetplayRepository
import com.spela.player.presentation.intent.NetplayLobbyIntent
import com.spela.player.presentation.state.NetplayLobbyState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NetplayLobbyViewModel(
    private val netplayRepository: NetplayRepository,
    private val authRepository: AuthRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NetplayLobbyState())
    val state: StateFlow<NetplayLobbyState> = _state.asStateFlow()

    private var currentSessionId: String = ""

    fun onIntent(intent: NetplayLobbyIntent) {
        when (intent) {
            is NetplayLobbyIntent.LoadSession -> loadSession(intent.sessionId)
            is NetplayLobbyIntent.UpdateInputDelay -> updateInputDelay(intent.inputDelay)
            NetplayLobbyIntent.LeaveSession -> leaveSession()
            NetplayLobbyIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadSession(sessionId: String) {
        currentSessionId = sessionId
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            // Fetch current user ID if not yet known
            if (_state.value.currentUserId.isEmpty()) {
                authRepository.getCurrentUser().onSuccess { user ->
                    _state.update { it.copy(currentUserId = user.id) }
                }
            }
            netplayRepository.getSession(sessionId).fold(
                onSuccess = { session ->
                    _state.update { it.copy(session = session, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun updateInputDelay(inputDelay: Int) {
        if (currentSessionId.isEmpty()) return
        _state.update { it.copy(isUpdatingSettings = true) }
        scope.launch(dispatchers.io) {
            netplayRepository.updateInputDelay(currentSessionId, inputDelay).fold(
                onSuccess = { session ->
                    _state.update { it.copy(session = session, isUpdatingSettings = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isUpdatingSettings = false) }
                },
            )
        }
    }

    private fun leaveSession() {
        if (currentSessionId.isEmpty()) return
        scope.launch(dispatchers.io) {
            netplayRepository.leaveSession(currentSessionId).fold(
                onSuccess = {
                    _state.update { it.copy(session = null, sessionLeft = true) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }
}
