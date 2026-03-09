package com.spela.player.presentation.intent

sealed interface NetplayIntent {
    data object LoadSessions : NetplayIntent
    data class CreateSession(val gameId: String, val inputDelay: Int = 3) : NetplayIntent
    data class JoinByCode(val code: String) : NetplayIntent
    data class DeleteSession(val sessionId: String) : NetplayIntent
    data object DismissError : NetplayIntent
    data object ClearJoinedSession : NetplayIntent
}

sealed interface NetplayLobbyIntent {
    data class LoadSession(val sessionId: String) : NetplayLobbyIntent
    data class UpdateInputDelay(val inputDelay: Int) : NetplayLobbyIntent
    data object LeaveSession : NetplayLobbyIntent
    data object DismissError : NetplayLobbyIntent
    data object ShowInviteSheet : NetplayLobbyIntent
    data object HideInviteSheet : NetplayLobbyIntent
    data class UpdateInviteSearchQuery(val query: String) : NetplayLobbyIntent
    data class InviteSearchPage(val page: Int) : NetplayLobbyIntent
    data class SendInvite(val username: String) : NetplayLobbyIntent
    data object DismissInviteSuccess : NetplayLobbyIntent
}
