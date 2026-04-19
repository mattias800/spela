package com.spela.player.presentation.intent

sealed interface SharedSessionIntent {
    data object LoadSharedSessions : SharedSessionIntent
    data object LoadInvitations : SharedSessionIntent
    data object RefreshAll : SharedSessionIntent
    data class CreateSharedSession(val name: String, val gameId: String, val description: String = "") : SharedSessionIntent
    data class AcceptInvitation(val invitationId: String) : SharedSessionIntent
    data class RejectInvitation(val invitationId: String) : SharedSessionIntent
    data object DismissError : SharedSessionIntent
    data object DismissSuccess : SharedSessionIntent
}

sealed interface SharedSessionDetailIntent {
    data class LoadSharedSession(val sharedSessionId: String) : SharedSessionDetailIntent
    data class LoadSaves(val sharedSessionId: String) : SharedSessionDetailIntent
    data class InviteUser(val sharedSessionId: String, val username: String) : SharedSessionDetailIntent
    data class LeaveSharedSession(val sharedSessionId: String) : SharedSessionDetailIntent
    data class TakeTurn(val sharedSessionId: String) : SharedSessionDetailIntent
    data class ReleaseTurn(val sharedSessionId: String) : SharedSessionDetailIntent
    data object DismissError : SharedSessionDetailIntent
    data object DismissSuccess : SharedSessionDetailIntent
    data object ShowInviteSheet : SharedSessionDetailIntent
    data object HideInviteSheet : SharedSessionDetailIntent
    data class UpdateInviteSearchQuery(val query: String) : SharedSessionDetailIntent
    data class InviteSearchPage(val page: Int) : SharedSessionDetailIntent
}
