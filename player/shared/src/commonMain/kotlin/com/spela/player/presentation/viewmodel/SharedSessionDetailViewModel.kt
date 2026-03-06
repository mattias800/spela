package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.SharedSessionRepository
import com.spela.player.presentation.intent.SharedSessionDetailIntent
import com.spela.player.presentation.state.SharedSessionDetailState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SharedSessionDetailViewModel(
    private val sharedSessionRepository: SharedSessionRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SharedSessionDetailState())
    val state: StateFlow<SharedSessionDetailState> = _state.asStateFlow()

    fun onIntent(intent: SharedSessionDetailIntent) {
        when (intent) {
            is SharedSessionDetailIntent.LoadSharedSession -> loadSharedSession(intent.sharedSessionId)
            is SharedSessionDetailIntent.LoadSaves -> loadSaves(intent.sharedSessionId)
            is SharedSessionDetailIntent.InviteUser -> inviteUser(intent.sharedSessionId, intent.username)
            is SharedSessionDetailIntent.LeaveSharedSession -> leaveSharedSession(intent.sharedSessionId)
            is SharedSessionDetailIntent.TakeTurn -> takeTurn(intent.sharedSessionId)
            is SharedSessionDetailIntent.ReleaseTurn -> releaseTurn(intent.sharedSessionId)
            is SharedSessionDetailIntent.CopySaveToGame -> copySaveToGame(intent.sharedSessionId, intent.saveId)
            SharedSessionDetailIntent.DismissError -> _state.update { it.copy(error = null) }
            SharedSessionDetailIntent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
        }
    }

    private fun loadSharedSession(sharedSessionId: String) {
        _state.update { it.copy(isLoadingSharedSession = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.getSharedSession(sharedSessionId).fold(
                onSuccess = { sharedSession ->
                    _state.update { it.copy(sharedSession = sharedSession, isLoadingSharedSession = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingSharedSession = false) }
                },
            )
        }
    }

    private fun loadSaves(sharedSessionId: String) {
        _state.update { it.copy(isLoadingSaves = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.getSharedSessionSaves(sharedSessionId).fold(
                onSuccess = { saves ->
                    _state.update { it.copy(saves = saves, isLoadingSaves = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingSaves = false) }
                },
            )
        }
    }

    private fun inviteUser(sharedSessionId: String, username: String) {
        _state.update { it.copy(isInviting = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.inviteUser(sharedSessionId, username).fold(
                onSuccess = {
                    _state.update { it.copy(isInviting = false, successMessage = "Invitation sent to $username") }
                    loadSharedSession(sharedSessionId)
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isInviting = false) }
                },
            )
        }
    }

    private fun leaveSharedSession(sharedSessionId: String) {
        scope.launch(dispatchers.io) {
            sharedSessionRepository.leaveSharedSession(sharedSessionId).fold(
                onSuccess = {
                    _state.update { it.copy(successMessage = "You left the shared session") }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun takeTurn(sharedSessionId: String) {
        _state.update { it.copy(isTakingTurn = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.takeTurn(sharedSessionId).fold(
                onSuccess = { turnToken ->
                    _state.update { it.copy(turnToken = turnToken, isTakingTurn = false) }
                    loadSharedSession(sharedSessionId)
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isTakingTurn = false) }
                },
            )
        }
    }

    private fun copySaveToGame(sharedSessionId: String, saveId: Long) {
        _state.update { it.copy(copyingSaveId = saveId) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.copySharedSessionSaveToGame(sharedSessionId, saveId).fold(
                onSuccess = {
                    _state.update { it.copy(copyingSaveId = null, successMessage = "Save copied to your library") }
                },
                onFailure = { error ->
                    _state.update { it.copy(copyingSaveId = null, error = error.message) }
                },
            )
        }
    }

    private fun releaseTurn(sharedSessionId: String) {
        _state.update { it.copy(isReleasingTurn = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.releaseTurn(sharedSessionId).fold(
                onSuccess = {
                    _state.update { it.copy(turnToken = null, isReleasingTurn = false) }
                    loadSharedSession(sharedSessionId)
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isReleasingTurn = false) }
                },
            )
        }
    }
}
