package com.spela.player.presentation.intent

sealed interface SessionDetailIntent {
    data class LoadSession(val sessionId: String) : SessionDetailIntent
    data class RenameSession(val sessionId: String, val name: String) : SessionDetailIntent
    data class DeleteSession(val sessionId: String) : SessionDetailIntent
    data class ToggleCheatsEnabled(val sessionId: String, val enabled: Boolean) : SessionDetailIntent
    data class UpdateCheatSettings(val sessionId: String, val enabledIndices: List<Int>) : SessionDetailIntent
    data class ToggleCheatAtIndex(val sessionId: String, val cheatIndex: Int) : SessionDetailIntent
    data class SelectAllCheats(val sessionId: String) : SessionDetailIntent
    data class DeselectAllCheats(val sessionId: String) : SessionDetailIntent
    data class StartFromSave(val sessionId: String, val saveId: String) : SessionDetailIntent
    /**
     * Clone this session (or one specific save within it) into a new
     * session owned by the caller. [saveId] null → server uses the
     * most-recent save; non-null → clone from that specific save (US-3).
     */
    data class CloneSession(val sessionId: String, val name: String?, val saveId: Long?) : SessionDetailIntent
    /** Clears [SessionDetailState.clonedSessionId] after navigation completes. */
    data object ClearCloneNavigation : SessionDetailIntent
    data object ShowDeleteConfirm : SessionDetailIntent
    data object DismissDeleteConfirm : SessionDetailIntent
    data object DismissError : SessionDetailIntent
    data object DismissSuccess : SessionDetailIntent
}
