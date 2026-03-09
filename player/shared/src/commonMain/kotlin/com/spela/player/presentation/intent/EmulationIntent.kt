package com.spela.player.presentation.intent

sealed interface EmulationIntent {
    data class StartGame(
        val gameId: String,
        val sharedSessionId: String? = null,
        val turnToken: String? = null,
        val netplaySessionId: String? = null,
        val netplayLocalPort: Int = 0,
        val netplayInputDelay: Int = 3,
        val netplayIsHost: Boolean = false,
        val challengeId: String? = null,
        val challengeSaveData: ByteArray? = null,
        val skipAutoLoad: Boolean = false,
        val sessionId: String? = null,
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

    // Lifecycle pause/resume (e.g. clamshell close on Android)
    data object LifecyclePause : EmulationIntent
    data object LifecycleResume : EmulationIntent

    data object ShowKeyMapping : EmulationIntent
    data object HideKeyMapping : EmulationIntent
    data object ShowGamepadConfig : EmulationIntent
    data object HideGamepadConfig : EmulationIntent

    data object DismissAchievement : EmulationIntent

    data class SecondaryDisplayAvailabilityChanged(val available: Boolean) : EmulationIntent

    // Netplay-specific intents
    data object ShowNetplayLeaveConfirm : EmulationIntent
    data object DismissNetplayLeaveConfirm : EmulationIntent
    data object ConfirmNetplayLeave : EmulationIntent

    // Challenge-specific intents
    data object CreateChallenge : EmulationIntent
    data class SubmitChallenge(
        val name: String,
        val description: String,
        val type: String,
        val difficulty: String,
    ) : EmulationIntent
    data object DismissChallengeCreation : EmulationIntent
    data object CompleteChallenge : EmulationIntent
    data object RestartChallenge : EmulationIntent
    data object ShowGiveUpConfirm : EmulationIntent
    data object DismissGiveUpConfirm : EmulationIntent
    data object ConfirmGiveUp : EmulationIntent
    data object DismissChallengeResult : EmulationIntent

    // BIOS
    data object DismissMissingBiosDialog : EmulationIntent
    data object TryAnywayMissingBios : EmulationIntent

    // Quick-save slots
    data object QuickSave : EmulationIntent
    data object QuickLoad : EmulationIntent
    data class SelectSlot(val slot: Int) : EmulationIntent
    /** Save state to the given slot (selects it first, then saves). */
    data class SaveToSlot(val slot: Int) : EmulationIntent
    /** Load state from the given slot (selects it first, then loads). */
    data class LoadFromSlot(val slot: Int) : EmulationIntent

    // Rewind
    data object RewindStep : EmulationIntent
    data object ToggleRewind : EmulationIntent

    // Pre-launch sync
    data class PrepareLaunch(
        val gameId: String,
        val skipAutoLoad: Boolean = false,
        val sessionId: String? = null,
    ) : EmulationIntent
    data object PlayWithLocalSave : EmulationIntent
    data object CancelLaunch : EmulationIntent

    // Core mismatch
    /** User chose "Try Loading Anyway" — attempt to load the mismatched save state. */
    data object CoreMismatchTryAnyway : EmulationIntent
    /** User chose "Start with Game Save Only" — skip save state, SRAM already loaded. */
    data object CoreMismatchGameSaveOnly : EmulationIntent
    /** User chose "Start Fresh" — skip all saves. */
    data object CoreMismatchStartFresh : EmulationIntent

    // Cheats
    data object ShowCheatBrowser : EmulationIntent
    data object HideCheatBrowser : EmulationIntent
    data class ToggleCheatInGame(val cheatId: String, val enabled: Boolean) : EmulationIntent
}
