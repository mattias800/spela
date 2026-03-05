package com.spela.player.presentation.state

import com.spela.player.domain.model.Cheat
import com.spela.player.domain.model.GameSession
import com.spela.player.domain.model.SaveState

data class SessionDetailState(
    val session: GameSession? = null,
    val saves: List<SaveState> = emptyList(),
    val cheatsEnabled: Boolean = false,
    val enabledCheatIndices: List<Int> = emptyList(),
    val availableCheats: List<Cheat> = emptyList(),
    val isLoadingCheats: Boolean = false,
    val cheatsLoadAttempted: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingSaves: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)
