package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.NetplayRepository
import com.spela.player.domain.repository.UserRepository
import com.spela.player.presentation.delegate.InviteSheetDelegate
import com.spela.player.presentation.delegate.InviteSheetState
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
    userRepository: UserRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NetplayLobbyState())
    val state: StateFlow<NetplayLobbyState> = _state.asStateFlow()

    private var currentSessionId: String = ""

    private val inviteDelegate = InviteSheetDelegate(
        userRepository = userRepository,
        dispatchers = dispatchers,
        scope = scope,
        onStateUpdate = { inviteState -> _state.update { it.applyInviteState(inviteState) } },
        getState = { _state.value.toInviteSheetState() },
        onError = { msg -> _state.update { it.copy(error = msg) } },
    )

    fun onIntent(intent: NetplayLobbyIntent) {
        when (intent) {
            is NetplayLobbyIntent.LoadSession -> loadSession(intent.sessionId)
            is NetplayLobbyIntent.UpdateInputDelay -> updateInputDelay(intent.inputDelay)
            NetplayLobbyIntent.LeaveSession -> leaveSession()
            NetplayLobbyIntent.DismissError -> _state.update { it.copy(error = null) }
            NetplayLobbyIntent.ShowInviteSheet -> inviteDelegate.show()
            NetplayLobbyIntent.HideInviteSheet -> inviteDelegate.hide()
            is NetplayLobbyIntent.UpdateInviteSearchQuery -> inviteDelegate.updateSearchQuery(intent.query)
            is NetplayLobbyIntent.InviteSearchPage -> inviteDelegate.changePage(intent.page)
            is NetplayLobbyIntent.SendInvite -> sendInvite(intent.username)
            NetplayLobbyIntent.DismissInviteSuccess -> inviteDelegate.dismissInviteSuccess()
        }
    }

    private fun loadSession(sessionId: String) {
        currentSessionId = sessionId
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
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

    private fun sendInvite(username: String) {
        if (currentSessionId.isEmpty()) return
        inviteDelegate.markInviteSending(username)
        scope.launch(dispatchers.io) {
            netplayRepository.sendNetplayInvite(currentSessionId, username).fold(
                onSuccess = {
                    inviteDelegate.markInviteSuccess(username)
                },
                onFailure = { error ->
                    inviteDelegate.markInviteFailed()
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }
}

private fun NetplayLobbyState.toInviteSheetState() = InviteSheetState(
    showInviteSheet = showInviteSheet,
    inviteSearchQuery = inviteSearchQuery,
    inviteSearchResults = inviteSearchResults,
    inviteSearchTotal = inviteSearchTotal,
    inviteSearchPage = inviteSearchPage,
    recentPartners = recentPartners,
    isSearchingUsers = isSearchingUsers,
    isLoadingRecentPartners = isLoadingRecentPartners,
    invitingUsername = invitingUsername,
    invitedUsernames = invitedUsernames,
    inviteSuccessMessage = inviteSuccessMessage,
)

private fun NetplayLobbyState.applyInviteState(s: InviteSheetState) = copy(
    showInviteSheet = s.showInviteSheet,
    inviteSearchQuery = s.inviteSearchQuery,
    inviteSearchResults = s.inviteSearchResults,
    inviteSearchTotal = s.inviteSearchTotal,
    inviteSearchPage = s.inviteSearchPage,
    recentPartners = s.recentPartners,
    isSearchingUsers = s.isSearchingUsers,
    isLoadingRecentPartners = s.isLoadingRecentPartners,
    invitingUsername = s.invitingUsername,
    invitedUsernames = s.invitedUsernames,
    inviteSuccessMessage = s.inviteSuccessMessage,
)
