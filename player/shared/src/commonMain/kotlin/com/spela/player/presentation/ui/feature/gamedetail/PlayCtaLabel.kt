package com.spela.player.presentation.ui.feature.gamedetail

import com.spela.player.domain.model.PlaySemantics
import com.spela.player.util.formatPlayTime

/**
 * Resolves the user-facing label for the game-detail hero's primary
 * play button. Pure function — moved out of GameHeroContent so the
 * "should we suffix the play time?" decision is unit-testable without
 * spinning up Compose.
 *
 * Rules:
 *  - NoSession → "New game" (nothing meaningful to suffix)
 *  - ResumesFromSaveState / LaunchesFresh, no playtime → "Resume" / "Continue"
 *  - ResumesFromSaveState / LaunchesFresh + playtime ≥ 60 s →
 *    "Resume — {h m}" / "Continue — {h m}"
 *
 * The 60-second threshold is intentional: [formatPlayTime] returns
 * `"< 1 min"` for sub-minute durations which would render as
 * "Resume — < 1 min" — true but visually noisy. Below the threshold we
 * keep the bare label and let the user start playing without the button
 * advertising the trivial play time. (Mirrors the Hyrule design's
 * intuition that the suffix should add concrete signal, not just text.)
 */
fun gameDetailPlayLabel(
    semantics: PlaySemantics,
    sessionPlayTimeSeconds: Long,
): String {
    val base = when (semantics) {
        PlaySemantics.NoSession -> return "New game"
        PlaySemantics.ResumesFromSaveState -> "Resume"
        PlaySemantics.LaunchesFresh -> "Continue"
    }
    return if (sessionPlayTimeSeconds >= 60) {
        "$base — ${formatPlayTime(sessionPlayTimeSeconds)}"
    } else {
        base
    }
}
