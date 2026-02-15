package com.spela.player.presentation.intent

sealed interface EmulationIntent {
    data class StartGame(
        val gameId: String,
        val relayId: String? = null,
        val turnToken: String? = null,
        val netplaySessionId: String? = null,
        val netplayLocalPort: Int = 0,
        val netplayInputDelay: Int = 3,
        val netplayIsHost: Boolean = false,
    ) : EmulationIntent
    data object PauseGame : EmulationIntent
    data object ResumeGame : EmulationIntent
    data object StopGame : EmulationIntent
    data object SaveState : EmulationIntent
    data object LoadState : EmulationIntent
    data object ToggleOverlay : EmulationIntent
    data object ToggleFastForward : EmulationIntent
    data object TakeScreenshot : EmulationIntent

    data object ShowExitConfirm : EmulationIntent
    data object DismissExitConfirm : EmulationIntent
    data object ConfirmExit : EmulationIntent
    data object DismissStatus : EmulationIntent
    data object ClearExitRequest : EmulationIntent

    data object ShowKeyMapping : EmulationIntent
    data object HideKeyMapping : EmulationIntent

    data object DismissAchievement : EmulationIntent

    data class SecondaryDisplayAvailabilityChanged(val available: Boolean) : EmulationIntent

    // Netplay-specific intents
    data object ShowNetplayLeaveConfirm : EmulationIntent
    data object DismissNetplayLeaveConfirm : EmulationIntent
    data object ConfirmNetplayLeave : EmulationIntent
}
