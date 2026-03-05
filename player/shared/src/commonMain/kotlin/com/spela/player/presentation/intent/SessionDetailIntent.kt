package com.spela.player.presentation.intent

sealed interface SessionDetailIntent {
    data class LoadSession(val sessionId: String) : SessionDetailIntent
    data class RenameSession(val sessionId: String, val name: String) : SessionDetailIntent
    data class DeleteSession(val sessionId: String) : SessionDetailIntent
    data class ToggleCheatsEnabled(val sessionId: String, val enabled: Boolean) : SessionDetailIntent
    data class UpdateCheatSettings(val sessionId: String, val enabledIndices: List<Int>) : SessionDetailIntent
    data class StartFromSave(val sessionId: String, val saveId: String) : SessionDetailIntent
    data object ShowDeleteConfirm : SessionDetailIntent
    data object DismissDeleteConfirm : SessionDetailIntent
    data object DismissError : SessionDetailIntent
    data object DismissSuccess : SessionDetailIntent
}
