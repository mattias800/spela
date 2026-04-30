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

    /** First-launch save-state prompt actions (#804 phase 4b). All
     *  three buttons write a deliberate console-level choice and
     *  dismiss the prompt — "Decide per game" picks Enabled at the
     *  console level so individual games can be opted out from the
     *  game-detail toggle (a future slice), avoiding leaving the
     *  console in an indeterminate ask-once state forever. */
    data object AcceptSaveStatesForConsole : EmulationIntent
    data object RejectSaveStatesForConsole : EmulationIntent
    data object DeferSaveStateChoiceToPerGame : EmulationIntent
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
    /** Dismiss the slot-primary slot picker modal without saving or
     *  loading. The user is bailing out of the choice. See #804 phase 5. */
    data object DismissSlotPicker : EmulationIntent

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

    /**
     * "Start fresh on the new version" — launch with the new core and
     * skip auto-load of the save state. Used by Sheet B (pinned
     * binary pruned) and, later, Sheet D (rehearsal crashed). Sets
     * `AutoLoadSuppressed = true` on the session so the next launch
     * also skips the stale save; cleared on first successful manual
     * save.
     */
    data object ResolveCoreDecisionStartFresh : EmulationIntent

    // ── #672 PR 3c.ii — rehearsal flow (Sheets C/D + banner) ──

    /**
     * Banner "Did this work?" tap — opens Sheet C over the running
     * rehearsal session. The banner stays visible behind the sheet
     * so the user can still see the gameplay they are evaluating.
     */
    data object RehearsalShowConfirmation : EmulationIntent

    /**
     * Sheet C dismissed via back button without picking an action.
     * Treated identically to "Let me try a bit longer" — close the
     * sheet, stay in rehearsal mode.
     */
    data object RehearsalDismissConfirmation : EmulationIntent

    /**
     * Sheet C primary — "Yes, keep the new version". Exits rehearsal
     * mode, discards the in-memory save buffer, resumes normal play.
     * The session's pin advances on the next successful save (matches
     * Sheet A's "Keep the new version anyway" pin-advancement rule).
     */
    data object RehearsalCompletedKeepNew : EmulationIntent

    /**
     * Sheet C secondary OR Sheet D primary — "(No,) lock to the older
     * version". Exits rehearsal mode, sets `UserLockedCoreVersion = true`
     * on the session, tears down the current emulator, downloads the
     * pinned binary, and re-launches the session on the old core. The
     * pin itself is unchanged (still the original sha).
     */
    data object RehearsalCompletedLockOld : EmulationIntent

    /**
     * Sheet C ghost — "Let me try a bit longer". Closes Sheet C and
     * resumes the rehearsal banner; no state change. Same effect as
     * back-button dismiss but exposed as an explicit action so users
     * who don't trust system-back behaviour have a labeled way out.
     */
    data object RehearsalContinueLonger : EmulationIntent

    /**
     * Emitted by the libretro bridge / SaveManager when a core crash
     * or `retro_unserialize` failure is detected during rehearsal.
     * Transitions `coreDecision` from `RehearsalPrompt` to
     * `RehearsalCrashed`, which in turn renders Sheet D.
     */
    data class RehearsalCrashed(
        val coreName: String,
        val coreDisplayName: String,
    ) : EmulationIntent

    /**
     * Sheet D secondary — "Start fresh on the new version" after a
     * rehearsal crash. Repins to the current sha, sets
     * `AutoLoadSuppressed = true`, and re-launches without attempting
     * to load the save state that crashed.
     */
    data object RehearsalCrashStartFresh : EmulationIntent

    /**
     * Sheet D more-options — "Just go back — I'll try again later".
     * Closes the sheet without changing server state. The user is
     * left on the (crashed) emulator surface and can quit normally;
     * the next session launch will re-enter the upgrade decision flow.
     */
    data object RehearsalCrashDismiss : EmulationIntent
}
