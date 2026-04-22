package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.CheatRepository
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.SessionRepository
import com.spela.player.presentation.intent.SessionDetailIntent
import com.spela.player.presentation.state.SessionDetailState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionDetailViewModel(
    private val sessionRepository: SessionRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val cheatRepository: CheatRepository? = null,
    private val gameRepository: GameRepository? = null,
) {
    private val _state = MutableStateFlow(SessionDetailState())
    val state: StateFlow<SessionDetailState> = _state.asStateFlow()

    fun onIntent(intent: SessionDetailIntent) {
        when (intent) {
            is SessionDetailIntent.LoadSession -> loadSession(intent.sessionId)
            is SessionDetailIntent.RenameSession -> renameSession(intent.sessionId, intent.name)
            is SessionDetailIntent.DeleteSession -> deleteSession(intent.sessionId)
            is SessionDetailIntent.ToggleCheatsEnabled -> toggleCheatsEnabled(intent.sessionId, intent.enabled)
            is SessionDetailIntent.UpdateCheatSettings -> updateCheatSettings(intent.sessionId, intent.enabledIndices)
            is SessionDetailIntent.ToggleCheatAtIndex -> toggleCheatAtIndex(intent.sessionId, intent.cheatIndex)
            is SessionDetailIntent.SelectAllCheats -> selectAllCheats(intent.sessionId)
            is SessionDetailIntent.DeselectAllCheats -> deselectAllCheats(intent.sessionId)
            is SessionDetailIntent.StartFromSave -> { /* Handled by UI navigation */ }
            is SessionDetailIntent.CloneSession -> cloneSession(intent.sessionId, intent.name, intent.saveId)
            SessionDetailIntent.ClearCloneNavigation -> _state.update { it.copy(clonedSessionId = null) }
            SessionDetailIntent.ShowDeleteConfirm -> _state.update { it.copy(showDeleteConfirm = true) }
            SessionDetailIntent.DismissDeleteConfirm -> _state.update { it.copy(showDeleteConfirm = false) }
            SessionDetailIntent.DismissError -> _state.update { it.copy(error = null) }
            SessionDetailIntent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            is SessionDetailIntent.UnlockCoreVersion -> unlockCoreVersion(intent.sessionId)
        }
    }

    private fun unlockCoreVersion(sessionId: String) {
        // Optimistic: flip the chip off immediately so the tap feels
        // responsive on slow connections. Revert in onFailure — matches
        // the pattern established by `toggleCheatsEnabled` below.
        val previousSession = _state.value.session
        if (previousSession != null && previousSession.id == sessionId) {
            _state.update {
                it.copy(session = previousSession.copy(userLockedCoreVersion = false))
            }
        }
        scope.launch(dispatchers.io) {
            sessionRepository.updateSessionCoreFlags(
                sessionId = sessionId,
                userLockedCoreVersion = false,
            ).fold(
                onSuccess = { updated ->
                    _state.update {
                        it.copy(
                            session = updated,
                            successMessage = "Lock cleared. We'll check for a newer core next time you play.",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            session = previousSession,
                            error = error.message,
                        )
                    }
                },
            )
        }
    }

    private fun cloneSession(sessionId: String, name: String?, saveId: Long?) {
        scope.launch(dispatchers.io) {
            sessionRepository.cloneSession(sessionId, name, saveId).fold(
                onSuccess = { cloned ->
                    _state.update {
                        it.copy(
                            clonedSessionId = cloned.id,
                            successMessage = "Session cloned as \"${cloned.name}\"",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun loadSession(sessionId: String) {
        _state.update { SessionDetailState(isLoading = true) }
        scope.launch(dispatchers.io) {
            sessionRepository.getSession(sessionId).fold(
                onSuccess = { session ->
                    _state.update { it.copy(session = session, isLoading = false) }
                    loadGame(session.gameId)
                    loadSaves(sessionId)
                    loadCheats(sessionId)
                    loadAvailableCheats(session.gameId)
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun loadGame(gameId: String) {
        val repo = gameRepository ?: return
        scope.launch(dispatchers.io) {
            repo.getGameDetail(gameId).fold(
                onSuccess = { detail ->
                    _state.update { it.copy(game = detail.game) }
                },
                onFailure = { /* Non-critical — UI still works without game info */ },
            )
        }
    }

    private fun loadSaves(sessionId: String) {
        _state.update { it.copy(isLoadingSaves = true) }
        scope.launch(dispatchers.io) {
            sessionRepository.getSessionSaves(sessionId).fold(
                onSuccess = { saves ->
                    _state.update { it.copy(saves = saves, isLoadingSaves = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingSaves = false) }
                },
            )
        }
    }

    private fun loadCheats(sessionId: String) {
        scope.launch(dispatchers.io) {
            sessionRepository.getSessionCheats(sessionId).fold(
                onSuccess = { config ->
                    _state.update {
                        it.copy(
                            cheatsEnabled = config.cheatsEnabled,
                            enabledCheatIndices = config.enabledIndices,
                        )
                    }
                },
                onFailure = { /* Cheats may not be configured yet, ignore */ },
            )
        }
    }

    private fun loadAvailableCheats(gameId: String) {
        val repo = cheatRepository ?: return
        _state.update { it.copy(isLoadingCheats = true) }
        scope.launch(dispatchers.io) {
            repo.getCheatsForGame(gameId).fold(
                onSuccess = { cheats ->
                    _state.update {
                        it.copy(
                            availableCheats = cheats,
                            isLoadingCheats = false,
                            cheatsLoadAttempted = true,
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoadingCheats = false,
                            cheatsLoadAttempted = true,
                        )
                    }
                },
            )
        }
    }

    private fun renameSession(sessionId: String, name: String) {
        scope.launch(dispatchers.io) {
            sessionRepository.updateSession(sessionId, name).fold(
                onSuccess = { updated ->
                    _state.update {
                        it.copy(
                            session = updated,
                            successMessage = "Session renamed",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun deleteSession(sessionId: String) {
        _state.update { it.copy(showDeleteConfirm = false) }
        scope.launch(dispatchers.io) {
            sessionRepository.deleteSession(sessionId).fold(
                onSuccess = {
                    _state.update { it.copy(successMessage = "Session deleted") }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun toggleCheatsEnabled(sessionId: String, enabled: Boolean) {
        // Optimistic update
        _state.update { it.copy(cheatsEnabled = enabled) }
        scope.launch(dispatchers.io) {
            val indices = if (enabled) _state.value.enabledCheatIndices else emptyList()
            sessionRepository.updateSessionCheats(sessionId, enabled, indices).fold(
                onSuccess = { config ->
                    _state.update {
                        it.copy(
                            cheatsEnabled = config.cheatsEnabled,
                            enabledCheatIndices = config.enabledIndices,
                        )
                    }
                },
                onFailure = { error ->
                    // Revert
                    _state.update { it.copy(cheatsEnabled = !enabled, error = error.message) }
                },
            )
        }
    }

    private fun updateCheatSettings(sessionId: String, enabledIndices: List<Int>) {
        _state.update { it.copy(enabledCheatIndices = enabledIndices) }
        scope.launch(dispatchers.io) {
            sessionRepository.updateSessionCheats(sessionId, _state.value.cheatsEnabled, enabledIndices).fold(
                onSuccess = { config ->
                    _state.update {
                        it.copy(
                            cheatsEnabled = config.cheatsEnabled,
                            enabledCheatIndices = config.enabledIndices,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun toggleCheatAtIndex(sessionId: String, cheatIndex: Int) {
        val current = _state.value.enabledCheatIndices
        val newIndices = if (cheatIndex in current) {
            current - cheatIndex
        } else {
            current + cheatIndex
        }
        updateCheatSettings(sessionId, newIndices)
    }

    private fun selectAllCheats(sessionId: String) {
        val allIndices = _state.value.availableCheats.map { it.index }
        updateCheatSettings(sessionId, allIndices)
    }

    private fun deselectAllCheats(sessionId: String) {
        updateCheatSettings(sessionId, emptyList())
    }
}
