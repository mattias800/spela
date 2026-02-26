package com.spela.player.presentation.intent

sealed interface RelayIntent {
    data object LoadRelays : RelayIntent
    data object LoadInvitations : RelayIntent
    data object RefreshAll : RelayIntent
    data class CreateRelay(val name: String, val gameId: String, val description: String = "") : RelayIntent
    data class AcceptInvitation(val invitationId: String) : RelayIntent
    data class RejectInvitation(val invitationId: String) : RelayIntent
    data object DismissError : RelayIntent
    data object DismissSuccess : RelayIntent
}

sealed interface RelayDetailIntent {
    data class LoadRelay(val relayId: String) : RelayDetailIntent
    data class LoadSaves(val relayId: String) : RelayDetailIntent
    data class InviteUser(val relayId: String, val username: String) : RelayDetailIntent
    data class LeaveRelay(val relayId: String) : RelayDetailIntent
    data class TakeTurn(val relayId: String) : RelayDetailIntent
    data class ReleaseTurn(val relayId: String) : RelayDetailIntent
    data class CopySaveToGame(val relayId: String, val saveId: Long) : RelayDetailIntent
    data object DismissError : RelayDetailIntent
    data object DismissSuccess : RelayDetailIntent
}
