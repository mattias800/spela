package com.spela.player.presentation.delegate

import com.spela.player.domain.model.UserSearchResult
import com.spela.player.domain.repository.UserRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State for the invite sheet, embedded in parent ViewModel states.
 */
data class InviteSheetState(
    val showInviteSheet: Boolean = false,
    val inviteSearchQuery: String = "",
    val inviteSearchResults: List<UserSearchResult> = emptyList(),
    val inviteSearchTotal: Long = 0,
    val inviteSearchPage: Int = 1,
    val recentPartners: List<UserSearchResult> = emptyList(),
    val isSearchingUsers: Boolean = false,
    val isLoadingRecentPartners: Boolean = false,
    val invitingUsername: String? = null,
    val invitedUsernames: List<String> = emptyList(),
    val inviteSuccessMessage: String? = null,
)

/**
 * Delegate that handles invite sheet logic (search, pagination, recent partners).
 * Used by both NetplayLobbyViewModel and SharedSessionDetailViewModel to avoid duplication.
 */
class InviteSheetDelegate(
    private val userRepository: UserRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val onStateUpdate: (InviteSheetState) -> Unit,
    private val getState: () -> InviteSheetState,
    private val onError: (String?) -> Unit,
) {
    private var searchJob: Job? = null

    fun show() {
        onStateUpdate(
            InviteSheetState(
                showInviteSheet = true,
            )
        )
        loadRecentPartners()
        searchUsers(page = 1)
    }

    fun hide() {
        searchJob?.cancel()
        onStateUpdate(InviteSheetState())
    }

    fun updateSearchQuery(query: String) {
        onStateUpdate(getState().copy(inviteSearchQuery = query, inviteSearchPage = 1))
        searchJob?.cancel()
        searchJob = scope.launch(dispatchers.io) {
            delay(300)
            searchUsersInternal(query, 1)
        }
    }

    fun changePage(page: Int) {
        onStateUpdate(getState().copy(inviteSearchPage = page))
        scope.launch(dispatchers.io) {
            searchUsersInternal(getState().inviteSearchQuery, page)
        }
    }

    fun markInviteSending(username: String) {
        onStateUpdate(getState().copy(invitingUsername = username))
    }

    fun markInviteSuccess(username: String) {
        onStateUpdate(
            getState().copy(
                invitingUsername = null,
                invitedUsernames = getState().invitedUsernames + username,
                inviteSuccessMessage = "Invite sent to $username",
            )
        )
    }

    fun markInviteFailed() {
        onStateUpdate(getState().copy(invitingUsername = null))
    }

    fun dismissInviteSuccess() {
        onStateUpdate(getState().copy(inviteSuccessMessage = null))
    }

    private suspend fun searchUsersInternal(query: String, page: Int) {
        onStateUpdate(getState().copy(isSearchingUsers = true))
        userRepository.searchUsers(query, page).fold(
            onSuccess = { searchPage ->
                onStateUpdate(
                    getState().copy(
                        inviteSearchResults = searchPage.data,
                        inviteSearchTotal = searchPage.total,
                        inviteSearchPage = searchPage.page,
                        isSearchingUsers = false,
                    )
                )
            },
            onFailure = { error ->
                onStateUpdate(getState().copy(isSearchingUsers = false))
                onError(error.message)
            },
        )
    }

    private fun loadRecentPartners() {
        onStateUpdate(getState().copy(isLoadingRecentPartners = true))
        scope.launch(dispatchers.io) {
            userRepository.getRecentPartners().fold(
                onSuccess = { partners ->
                    onStateUpdate(getState().copy(recentPartners = partners, isLoadingRecentPartners = false))
                },
                onFailure = {
                    onStateUpdate(getState().copy(isLoadingRecentPartners = false))
                },
            )
        }
    }

    private fun searchUsers(page: Int) {
        onStateUpdate(getState().copy(inviteSearchPage = page))
        scope.launch(dispatchers.io) {
            searchUsersInternal(getState().inviteSearchQuery, page)
        }
    }
}
