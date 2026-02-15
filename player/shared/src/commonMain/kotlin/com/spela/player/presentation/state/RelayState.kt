package com.spela.player.presentation.state

import com.spela.player.domain.model.Relay
import com.spela.player.domain.model.RelayDetail
import com.spela.player.domain.model.RelayInvitation
import com.spela.player.domain.model.RelaySave

data class RelayListState(
    val relays: List<Relay> = emptyList(),
    val invitations: List<RelayInvitation> = emptyList(),
    val isLoadingRelays: Boolean = false,
    val isLoadingInvitations: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

data class RelayDetailState(
    val relay: RelayDetail? = null,
    val saves: List<RelaySave> = emptyList(),
    val isLoadingRelay: Boolean = false,
    val isLoadingSaves: Boolean = false,
    val isInviting: Boolean = false,
    val isTakingTurn: Boolean = false,
    val isReleasingTurn: Boolean = false,
    val turnToken: String? = null,
    val error: String? = null,
    val successMessage: String? = null,
)
