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
        val forceNewSession: Boolean = false,
        val sessionId: String? = null,
        /**
         * When true, the core-upgrade decision sheet (#672) is
         * suppressed for this launch. Set by the VM when re-firing
         * `StartGame` after the user resolves a previous Sheet A
         * prompt — without this flag we'd loop back into the sheet
         * indefinitely because the pin still differs from the server.
         */
        val skipCoreDecisionPrompt: Boolean = false,
    ) : EmulationIntent
    data object PauseGame : EmulationIntent
    data object ResumeGame : EmulationIntent
    data object StopGame : EmulationIntent
    data object SaveState : EmulationIntent
    data object LoadState : EmulationIntent
    data object ToggleOverlay : EmulationIntent
    data object ToggleFastForward : EmulationIntent
    data class SetVolume(val volume: Float) : EmulationIntent
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
        val forceNewSession: Boolean = false,
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

    // Core mismatch save warning
    /** User confirmed saving the save state despite core mismatch. */
    data object ConfirmCoreMismatchSave : EmulationIntent
    /** User chose to skip saving the save state (SRAM already saved). */
    data object SkipCoreMismatchSave : EmulationIntent

    // Cheats
    data object ShowCheatBrowser : EmulationIntent
    data object HideCheatBrowser : EmulationIntent
    data class ToggleCheatInGame(val cheatId: String, val enabled: Boolean) : EmulationIntent

    // Secondary screen toast
    data object ClearSecondaryToast : EmulationIntent

    // Secondary screen touch control port
    data class SelectTouchControlPort(val port: Int) : EmulationIntent

    // Secondary screen control tab
    data class SelectControlTab(val tab: com.spela.player.presentation.state.ControlTab) : EmulationIntent

    // #672 core-upgrade decision — Sheet A resolution intents.
    // Emitted when the user picks one of the four actions on the
    // "We updated {core}" sheet. The VM reads EmulationState
    // .coreDecision for the context it needs (core name, shas, etc.).

    /**
     * "Try with my save" — enter rehearsal mode and launch with the
     * new core. Save writes are suppressed until the user confirms
     * via Sheet C (PR 3c). On PR 3b this activates rehearsal mode
     * but does not yet wire up the in-game banner or Sheet C exit;
     * the user can quit the game normally to leave rehearsal.
     */
    data object ResolveCoreDecisionTry : EmulationIntent

    /**
     * "Keep the new version anyway" — launch with the new core. The
     * session's pin advances only after a successful save on the new
     * core, so the user can still change their mind mid-session.
     */
    data object ResolveCoreDecisionKeepNew : EmulationIntent

    /**
     * "Lock this session to the older version" — launch with the
     * pinned binary and mark the session as user-locked so future
     * `startEmulation` calls skip the decision sheet entirely.
     */
    data object ResolveCoreDecisionLockOld : EmulationIntent

    /**
     * "Remind me the next time I play this" — in-memory dismiss that
     * launches with the new core without touching the pin. The sheet
     * will fire again on the next `startEmulation` call for this
     * session.
     */
    data object ResolveCoreDecisionRemindLater : EmulationIntent
}
