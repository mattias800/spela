package com.spela.player.presentation.state

import com.spela.player.domain.model.NetplaySession
import com.spela.player.domain.model.UserSearchResult

data class NetplayState(
    val sessions: List<NetplaySession> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val joinedSession: NetplaySession? = null,
)

data class NetplayLobbyState(
    val session: NetplaySession? = null,
    val currentUserId: String = "",
    val isLoading: Boolean = false,
    val isUpdatingSettings: Boolean = false,
    val error: String? = null,
    val sessionLeft: Boolean = false,
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
