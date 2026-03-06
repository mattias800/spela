package com.spela.player.presentation.state

import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionInvitation
import com.spela.player.domain.model.SharedSessionSave

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
    val copyingSaveId: Long? = null,
    val error: String? = null,
    val successMessage: String? = null,
)
