package com.spela.player.presentation.intent

sealed interface EmulationIntent {
    data class StartGame(val gameId: String) : EmulationIntent
    data object PauseGame : EmulationIntent
    data object ResumeGame : EmulationIntent
    data object StopGame : EmulationIntent
    data object SaveState : EmulationIntent
    data object LoadState : EmulationIntent
    data object ToggleOverlay : EmulationIntent
    data object ToggleFastForward : EmulationIntent
    data object TakeScreenshot : EmulationIntent
    data object DismissControlHint : EmulationIntent
    data object ShowExitConfirm : EmulationIntent
    data object DismissExitConfirm : EmulationIntent
    data object ConfirmExit : EmulationIntent
    data object DismissStatus : EmulationIntent
}
