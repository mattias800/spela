package com.spela.player.presentation.state

import com.spela.player.domain.model.NetplaySession

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
)
