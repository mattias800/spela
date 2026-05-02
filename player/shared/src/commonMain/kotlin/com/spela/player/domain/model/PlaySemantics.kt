package com.spela.player.domain.model

/**
 * Three-state taxonomy for the game-detail hero's primary action
 * label. Captures the difference between "user has played this and
 * the engine will pick up where they left off" vs. "user has played
 * this but launching takes them to the title screen and they have to
 * navigate to the engine's own save menu."
 *
 * Resolved from `(hasSession, consoleSaveStateSupport, effectiveSaveStateChoice)`
 * — see [resolvePlaySemantics]. The view-model maps this enum to the
 * label string the hero renders. See #884.
 */
enum class PlaySemantics {
    /** No session for this game yet. Label: "Play". */
    NoSession,

    /**
     * Session exists AND save state will auto-load on launch — the
     * console supports save states *and* the user hasn't opted out
     * via the per-console / per-game policy from #804. The user lands
     * mid-frame where they left off. Label: "Resume".
     */
    ResumesFromSaveState,

    /**
     * Session exists AND save state will NOT auto-load on launch —
     * the console doesn't support save states (e.g. ScummVM) or the
     * user opted out for this console / game. Launching takes the
     * user to the title screen; in-engine save menus are on them.
     * Label: "Continue" — honest about the difference, vs. "Resume"
     * which would over-promise.
     */
    LaunchesFresh,
}

/**
 * Pure resolver. The four open inputs come from these existing models:
 *
 *  - [hasSession] — `state.sessions.isNotEmpty()` on game-detail.
 *  - [consoleSaveStateSupport] — `Console.saveStateSupport`. Hardcoded
 *    server-side for cores that genuinely cannot snapshot RAM (ScummVM,
 *    demo cores).
 *  - [effectiveChoice] — already resolved upstream by
 *    [effectiveSaveStateChoice]; the resolver here only cares whether
 *    it's `Disabled` (no auto-load) or anything else (auto-load on).
 *    `AskOnce` counts as auto-load — the in-game flow treats it as
 *    Enabled until the prompt resolves to a deliberate choice.
 *
 * Pure function — no IO, no platform calls. Trivial to unit test, and
 * cheap enough to recompute every recomposition.
 */
fun resolvePlaySemantics(
    hasSession: Boolean,
    consoleSaveStateSupport: Boolean,
    effectiveChoice: SaveStateChoice,
): PlaySemantics {
    if (!hasSession) return PlaySemantics.NoSession
    val willAutoLoad = consoleSaveStateSupport && effectiveChoice != SaveStateChoice.Disabled
    return if (willAutoLoad) PlaySemantics.ResumesFromSaveState else PlaySemantics.LaunchesFresh
}
