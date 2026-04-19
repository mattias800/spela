package com.spela.player.presentation.state

import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionInvitation
import com.spela.player.domain.model.SharedSessionSave
import com.spela.player.domain.model.UserSearchResult

data class SharedSessionListState(
    val sharedSessions: List<SharedSession> = emptyList(),
    val invitations: List<SharedSessionInvitation> = emptyList(),
    val isLoadingSharedSessions: Boolean = false,
    val isLoadingInvitations: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

data class SharedSessionDetailState(
    val sharedSession: SharedSessionDetail? = null,
    val saves: List<SharedSessionSave> = emptyList(),
    val isLoadingSharedSession: Boolean = false,
    val isLoadingSaves: Boolean = false,
    val isInviting: Boolean = false,
    val isTakingTurn: Boolean = false,
    val isReleasingTurn: Boolean = false,
    val turnToken: String? = null,
    val error: String? = null,
    val successMessage: String? = null,
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
