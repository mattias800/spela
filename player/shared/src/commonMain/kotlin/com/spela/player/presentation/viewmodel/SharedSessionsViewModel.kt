package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.SharedSessionRepository
import com.spela.player.presentation.intent.SharedSessionIntent
import com.spela.player.presentation.state.SharedSessionListState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SharedSessionsViewModel(
    private val sharedSessionRepository: SharedSessionRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SharedSessionListState())
    val state: StateFlow<SharedSessionListState> = _state.asStateFlow()

    fun onIntent(intent: SharedSessionIntent) {
        when (intent) {
            SharedSessionIntent.LoadSharedSessions -> loadSharedSessions()
            SharedSessionIntent.LoadInvitations -> loadInvitations()
            SharedSessionIntent.RefreshAll -> refreshAll()
            is SharedSessionIntent.CreateSharedSession -> createSharedSession(intent.name, intent.gameId, intent.description)
            is SharedSessionIntent.AcceptInvitation -> acceptInvitation(intent.invitationId)
            is SharedSessionIntent.RejectInvitation -> rejectInvitation(intent.invitationId)
            SharedSessionIntent.DismissError -> _state.update { it.copy(error = null) }
            SharedSessionIntent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
        }
    }

    private fun loadSharedSessions() {
        _state.update { it.copy(isLoadingSharedSessions = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.getMySharedSessions().fold(
                onSuccess = { sharedSessions ->
                    _state.update { it.copy(sharedSessions = sharedSessions, isLoadingSharedSessions = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingSharedSessions = false) }
                },
            )
        }
    }

    private fun loadInvitations() {
        _state.update { it.copy(isLoadingInvitations = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.getSharedSessionInvitations().fold(
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
        _state.update { it.copy(isLoadingSharedSessions = true, isLoadingInvitations = true) }
        scope.launch(dispatchers.io) {
            val sharedSessions = sharedSessionRepository.getMySharedSessions().getOrDefault(emptyList())
            val invitations = sharedSessionRepository.getSharedSessionInvitations().getOrDefault(emptyList())
            _state.update {
                it.copy(
                    sharedSessions = sharedSessions,
                    invitations = invitations,
                    isLoadingSharedSessions = false,
                    isLoadingInvitations = false,
                )
            }
        }
    }

    private fun createSharedSession(name: String, gameId: String, description: String) {
        _state.update { it.copy(isCreating = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.createSharedSession(name, gameId, description).fold(
                onSuccess = {
                    _state.update { it.copy(isCreating = false, successMessage = "Shared session created") }
                    loadSharedSessions()
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isCreating = false) }
                },
            )
        }
    }

    private fun acceptInvitation(invitationId: String) {
        scope.launch(dispatchers.io) {
            sharedSessionRepository.acceptInvitation(invitationId).fold(
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
            sharedSessionRepository.rejectInvitation(invitationId).fold(
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
