package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.RelayRepository
import com.spela.player.presentation.intent.RelayDetailIntent
import com.spela.player.presentation.state.RelayDetailState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RelayDetailViewModel(
    private val relayRepository: RelayRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(RelayDetailState())
    val state: StateFlow<RelayDetailState> = _state.asStateFlow()

    fun onIntent(intent: RelayDetailIntent) {
        when (intent) {
            is RelayDetailIntent.LoadRelay -> loadRelay(intent.relayId)
            is RelayDetailIntent.LoadSaves -> loadSaves(intent.relayId)
            is RelayDetailIntent.InviteUser -> inviteUser(intent.relayId, intent.username)
            is RelayDetailIntent.LeaveRelay -> leaveRelay(intent.relayId)
            is RelayDetailIntent.TakeTurn -> takeTurn(intent.relayId)
            is RelayDetailIntent.ReleaseTurn -> releaseTurn(intent.relayId)
            RelayDetailIntent.DismissError -> _state.update { it.copy(error = null) }
            RelayDetailIntent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
        }
    }

    private fun loadRelay(relayId: String) {
        _state.update { it.copy(isLoadingRelay = true) }
        scope.launch(dispatchers.io) {
            relayRepository.getRelay(relayId).fold(
                onSuccess = { relay ->
                    _state.update { it.copy(relay = relay, isLoadingRelay = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingRelay = false) }
                },
            )
        }
    }

    private fun loadSaves(relayId: String) {
        _state.update { it.copy(isLoadingSaves = true) }
        scope.launch(dispatchers.io) {
            relayRepository.getRelaySaves(relayId).fold(
                onSuccess = { saves ->
                    _state.update { it.copy(saves = saves, isLoadingSaves = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingSaves = false) }
                },
            )
        }
    }

    private fun inviteUser(relayId: String, username: String) {
        _state.update { it.copy(isInviting = true) }
        scope.launch(dispatchers.io) {
            relayRepository.inviteUser(relayId, username).fold(
                onSuccess = {
                    _state.update { it.copy(isInviting = false, successMessage = "Invitation sent to $username") }
                    loadRelay(relayId)
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isInviting = false) }
                },
            )
        }
    }

    private fun leaveRelay(relayId: String) {
        scope.launch(dispatchers.io) {
            relayRepository.leaveRelay(relayId).fold(
                onSuccess = {
                    _state.update { it.copy(successMessage = "You left the relay") }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun takeTurn(relayId: String) {
        _state.update { it.copy(isTakingTurn = true) }
        scope.launch(dispatchers.io) {
            relayRepository.takeTurn(relayId).fold(
                onSuccess = { turnToken ->
                    _state.update { it.copy(turnToken = turnToken, isTakingTurn = false) }
                    loadRelay(relayId)
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isTakingTurn = false) }
                },
            )
        }
    }

    private fun releaseTurn(relayId: String) {
        _state.update { it.copy(isReleasingTurn = true) }
        scope.launch(dispatchers.io) {
            relayRepository.releaseTurn(relayId).fold(
                onSuccess = {
                    _state.update { it.copy(turnToken = null, isReleasingTurn = false) }
                    loadRelay(relayId)
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isReleasingTurn = false) }
                },
            )
        }
    }
}
