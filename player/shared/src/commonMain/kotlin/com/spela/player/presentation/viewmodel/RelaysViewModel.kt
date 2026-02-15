package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.RelayRepository
import com.spela.player.presentation.intent.RelayIntent
import com.spela.player.presentation.state.RelayListState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RelaysViewModel(
    private val relayRepository: RelayRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(RelayListState())
    val state: StateFlow<RelayListState> = _state.asStateFlow()

    fun onIntent(intent: RelayIntent) {
        when (intent) {
            RelayIntent.LoadRelays -> loadRelays()
            RelayIntent.LoadInvitations -> loadInvitations()
            RelayIntent.RefreshAll -> refreshAll()
            is RelayIntent.CreateRelay -> createRelay(intent.name, intent.gameId, intent.description)
            is RelayIntent.AcceptInvitation -> acceptInvitation(intent.invitationId)
            is RelayIntent.RejectInvitation -> rejectInvitation(intent.invitationId)
            RelayIntent.DismissError -> _state.update { it.copy(error = null) }
            RelayIntent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
        }
    }

    private fun loadRelays() {
        _state.update { it.copy(isLoadingRelays = true) }
        scope.launch(dispatchers.io) {
            relayRepository.getMyRelays().fold(
                onSuccess = { relays ->
                    _state.update { it.copy(relays = relays, isLoadingRelays = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingRelays = false) }
                },
            )
        }
    }

    private fun loadInvitations() {
        _state.update { it.copy(isLoadingInvitations = true) }
        scope.launch(dispatchers.io) {
            relayRepository.getRelayInvitations().fold(
                onSuccess = { invitations ->
                    _state.update { it.copy(invitations = invitations, isLoadingInvitations = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingInvitations = false) }
                },
            )
        }
    }

    private fun refreshAll() {
        _state.update { it.copy(isLoadingRelays = true, isLoadingInvitations = true) }
        scope.launch(dispatchers.io) {
            val relays = relayRepository.getMyRelays().getOrDefault(emptyList())
            val invitations = relayRepository.getRelayInvitations().getOrDefault(emptyList())
            _state.update {
                it.copy(
                    relays = relays,
                    invitations = invitations,
                    isLoadingRelays = false,
                    isLoadingInvitations = false,
                )
            }
        }
    }

    private fun createRelay(name: String, gameId: String, description: String) {
        _state.update { it.copy(isCreating = true) }
        scope.launch(dispatchers.io) {
            relayRepository.createRelay(name, gameId, description).fold(
                onSuccess = {
                    _state.update { it.copy(isCreating = false, successMessage = "Relay created") }
                    loadRelays()
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isCreating = false) }
                },
            )
        }
    }

    private fun acceptInvitation(invitationId: String) {
        scope.launch(dispatchers.io) {
            relayRepository.acceptInvitation(invitationId).fold(
                onSuccess = {
                    _state.update { it.copy(successMessage = "Invitation accepted") }
                    refreshAll()
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun rejectInvitation(invitationId: String) {
        scope.launch(dispatchers.io) {
            relayRepository.rejectInvitation(invitationId).fold(
                onSuccess = {
                    loadInvitations()
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }
}
