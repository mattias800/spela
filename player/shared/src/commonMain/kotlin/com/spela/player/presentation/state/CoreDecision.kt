package com.spela.player.presentation.state

import kotlin.time.Instant

/**
 * Models the "core version has changed since this session's saves were
 * written" decision point from issue #672. Exposed on [EmulationState]
 * as `coreDecision: CoreDecision?` so the UI can render Sheet A / B / C /
 * D without re-deriving which situation applies.
 *
 * v1 ships with the sealed class defined but not emitted from the
 * ViewModel — detection + emission lands in the core-upgrade decision
 * PR alongside the UI that consumes it. This file is the data contract
 * the UI PR will build against.
 *
 * See `design-proposals/core-upgrade-decision-spec.md` for the full
 * decision flow this type backs.
 */
sealed class CoreDecision {

    /**
     * Sheet A. The session has a pinned sha and the server has a newer
     * one for the same core; the session is not locked; `autoUpdateCoresEnabled`
     * is false (or this was reached via session detail's "Check for
     * core update"). User picks Try / Keep new / Lock old / Remind me.
     */
    data class UpgradeAvailable(
        val coreName: String,
        val coreDisplayName: String,
        /**
         * Name of the console this session is for (e.g. "Nintendo
         * Entertainment System"). Populates the fallback title when
         * the core has no display name — spec key
         * `core_upd.sheet_a.title_fallback` reads
         * `"The {console} core has a new version"`. Empty when the
         * VM couldn't resolve it.
         */
        val consoleName: String,
        val gameTitle: String,
        val oldSha: String,
        val newSha: String,
        val lastPlayedAt: Instant? = null,
    ) : CoreDecision()

    /**
     * Sheet B. The session's pinned sha has been rotated out of
     * server-side history retention. "Lock to old" is not offered.
     */
    data class PinPruned(
        val coreName: String,
        val coreDisplayName: String,
        val gameTitle: String,
        val prunedSha: String,
    ) : CoreDecision()

    /**
     * Active during the "Try with my save" rehearsal run. Drives the
     * [SpInGameBanner] overlay and the "Did this work?" affordance that
     * opens Sheet C.
     */
    data class RehearsalPrompt(
        val coreName: String,
        val coreDisplayName: String,
        /**
         * True when the rehearsal is running the server's current (new)
         * core binary, false when the user used the comparison escape
         * hatch to re-run the rehearsal on the pinned (old) binary.
         */
        val usingNewSha: Boolean,
    ) : CoreDecision()

    /**
     * Sheet D. The rehearsal emitter surfaces this after a core crash
     * / failed `retro_unserialize`, or the app detects a previous
     * rehearsal's `RehearsalCrashPending` sentinel on relaunch.
     */
    data class RehearsalCrashed(
        val coreName: String,
        val coreDisplayName: String,
    ) : CoreDecision()
}
