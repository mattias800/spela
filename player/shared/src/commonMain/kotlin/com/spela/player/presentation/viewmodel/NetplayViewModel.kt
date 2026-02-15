package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.NetplayRepository
import com.spela.player.presentation.intent.NetplayIntent
import com.spela.player.presentation.state.NetplayState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NetplayViewModel(
    private val netplayRepository: NetplayRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NetplayState())
    val state: StateFlow<NetplayState> = _state.asStateFlow()

    fun onIntent(intent: NetplayIntent) {
        when (intent) {
            NetplayIntent.LoadSessions -> loadSessions()
            is NetplayIntent.CreateSession -> createSession(intent.gameId, intent.inputDelay)
            is NetplayIntent.JoinByCode -> joinByCode(intent.code)
            is NetplayIntent.DeleteSession -> deleteSession(intent.sessionId)
            NetplayIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadSessions() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            netplayRepository.getSessions().fold(
                onSuccess = { sessions ->
                    _state.update { it.copy(sessions = sessions, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun createSession(gameId: String, inputDelay: Int) {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            netplayRepository.createSession(gameId, inputDelay).fold(
                onSuccess = { session ->
                    _state.update { it.copy(joinedSession = session, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun joinByCode(code: String) {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            netplayRepository.joinByInviteCode(code).fold(
                onSuccess = { session ->
                    _state.update { it.copy(joinedSession = session, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun deleteSession(sessionId: String) {
        scope.launch(dispatchers.io) {
            netplayRepository.deleteSession(sessionId).fold(
                onSuccess = {
                    loadSessions()
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }
}
