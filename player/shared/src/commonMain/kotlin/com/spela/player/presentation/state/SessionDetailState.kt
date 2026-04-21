package com.spela.player.presentation.state

import com.spela.player.domain.model.Cheat
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameSession
import com.spela.player.domain.model.SaveState

data class SessionDetailState(
    val session: GameSession? = null,
    val game: Game? = null,
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
    /**
     * After a successful Clone Session, the id of the newly-created
     * session. The screen observes this and navigates to the clone's
     * own detail page, then dispatches [SessionDetailIntent.ClearCloneNavigation].
     */
    val clonedSessionId: String? = null,
)
