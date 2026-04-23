package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.SessionRepository
import com.spela.player.presentation.state.GameDetailState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Session CRUD slice of [GameDetailViewModel]. Extracted from the
 * VM in the #691 refactor to shrink the 894-line god-class. Owns
 * nothing that the VM doesn't also own — it shares the same
 * [_state] StateFlow so updates are visible immediately without a
 * merge step — but keeps the session-specific intent handlers out
 * of the VM's `when` ladder.
 *
 * When [sessionRepository] is null (player app configured without
 * session support — rare; mostly in tests), every method is a
 * no-op. The VM must not guard on that itself.
 */
class GameDetailSessionManager(
    private val sessionRepository: SessionRepository?,
    private val _state: MutableStateFlow<GameDetailState>,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    fun loadSessions(gameId: String) {
        val repo = sessionRepository ?: return
        _state.update { it.copy(isLoadingSessions = true) }
        scope.launch(dispatchers.io) {
            repo.getSessionsForGame(gameId).fold(
                onSuccess = { sessions ->
                    _state.update { it.copy(sessions = sessions, isLoadingSessions = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingSessions = false) }
                },
            )
        }
    }

    fun createSession(gameId: String, name: String) {
        val repo = sessionRepository ?: return
        scope.launch(dispatchers.io) {
            repo.createSession(gameId, name).fold(
                onSuccess = { session ->
                    _state.update { state ->
                        state.copy(
                            sessions = state.sessions + session,
                            successMessage = "Session \"${session.name}\" created",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    fun renameSession(sessionId: String, name: String) {
        val repo = sessionRepository ?: return
        scope.launch(dispatchers.io) {
            repo.updateSession(sessionId, name).fold(
                onSuccess = { updated ->
                    _state.update { state ->
                        state.copy(
                            sessions = state.sessions.map {
                                if (it.id == sessionId) updated else it
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    fun deleteSession(sessionId: String) {
        val repo = sessionRepository ?: return
        scope.launch(dispatchers.io) {
            repo.deleteSession(sessionId).fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            sessions = state.sessions.filter { it.id != sessionId },
                            successMessage = "Session deleted",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    fun cloneSession(sessionId: String, name: String?, saveId: Long?) {
        val repo = sessionRepository ?: return
        scope.launch(dispatchers.io) {
            repo.cloneSession(sessionId, name, saveId).fold(
                onSuccess = { newSession ->
                    _state.update { state ->
                        state.copy(
                            sessions = state.sessions + newSession,
                            successMessage = "Session cloned as \"${newSession.name}\"",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }
}
