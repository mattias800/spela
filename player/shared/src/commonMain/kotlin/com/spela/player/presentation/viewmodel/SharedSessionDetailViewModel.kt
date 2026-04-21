package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.SessionRepository
import com.spela.player.domain.repository.SharedSessionRepository
import com.spela.player.domain.repository.UserRepository
import com.spela.player.presentation.delegate.InviteSheetDelegate
import com.spela.player.presentation.delegate.InviteSheetState
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
    userRepository: UserRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    // Nullable so existing test constructors without a session repo
    // (e.g. the shared-saves-only unit tests) keep compiling. The clone
    // action is a no-op when this is null — the UI hides the menu entry.
    private val sessionRepository: SessionRepository? = null,
) {
    private val _state = MutableStateFlow(SharedSessionDetailState())
    val state: StateFlow<SharedSessionDetailState> = _state.asStateFlow()

    private val inviteDelegate = InviteSheetDelegate(
        userRepository = userRepository,
        dispatchers = dispatchers,
        scope = scope,
        onStateUpdate = { inviteState -> _state.update { it.applyInviteState(inviteState) } },
        getState = { _state.value.toInviteSheetState() },
        onError = { msg -> _state.update { it.copy(error = msg) } },
    )

    fun onIntent(intent: SharedSessionDetailIntent) {
        when (intent) {
            is SharedSessionDetailIntent.LoadSharedSession -> loadSharedSession(intent.sharedSessionId)
            is SharedSessionDetailIntent.LoadSaves -> loadSaves(intent.sharedSessionId)
            is SharedSessionDetailIntent.InviteUser -> inviteUser(intent.sharedSessionId, intent.username)
            is SharedSessionDetailIntent.LeaveSharedSession -> leaveSharedSession(intent.sharedSessionId)
            is SharedSessionDetailIntent.TakeTurn -> takeTurn(intent.sharedSessionId)
            is SharedSessionDetailIntent.ReleaseTurn -> releaseTurn(intent.sharedSessionId)
            is SharedSessionDetailIntent.CloneToMyLibrary -> cloneToMyLibrary(intent.backingGameSessionId, intent.name)
            SharedSessionDetailIntent.ClearCloneNavigation -> _state.update { it.copy(clonedSessionId = null) }
            SharedSessionDetailIntent.DismissError -> _state.update { it.copy(error = null) }
            SharedSessionDetailIntent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            SharedSessionDetailIntent.ShowInviteSheet -> inviteDelegate.show()
            SharedSessionDetailIntent.HideInviteSheet -> inviteDelegate.hide()
            is SharedSessionDetailIntent.UpdateInviteSearchQuery -> inviteDelegate.updateSearchQuery(intent.query)
            is SharedSessionDetailIntent.InviteSearchPage -> inviteDelegate.changePage(intent.page)
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
        inviteDelegate.markInviteSending(username)
        _state.update { it.copy(isInviting = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.inviteUser(sharedSessionId, username).fold(
                onSuccess = {
                    inviteDelegate.markInviteSuccess(username)
                    _state.update {
                        it.copy(
                            isInviting = false,
                            successMessage = "Invitation sent to $username",
                        )
                    }
                    loadSharedSession(sharedSessionId)
                },
                onFailure = { error ->
                    inviteDelegate.markInviteFailed()
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

    /**
     * US-1: non-owner member clones the shared session's backing game
     * session into their own library. The new session inherits
     * totalPlayTime and pinnedCoreSha256 from the source — the server
     * copies the most-recent save so the clone can be played solo
     * from that point.
     */
    private fun cloneToMyLibrary(backingGameSessionId: String, name: String?) {
        val repo = sessionRepository ?: return
        scope.launch(dispatchers.io) {
            repo.cloneSession(backingGameSessionId, name, null).fold(
                onSuccess = { cloned ->
                    _state.update {
                        it.copy(
                            clonedSessionId = cloned.id,
                            successMessage = "Cloned as \"${cloned.name}\"",
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

private fun SharedSessionDetailState.toInviteSheetState() = InviteSheetState(
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

private fun SharedSessionDetailState.applyInviteState(s: InviteSheetState) = copy(
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
