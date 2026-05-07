package com.spela.player.presentation.ui.feature.gamedetail

import com.spela.player.domain.model.PlaySemantics
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure resolver behind the game-detail hero's
 * primary play button label. Captures the contract the user sees on
 * the button text — getting this wrong silently misleads (e.g.
 * "Resume" on a no-session game promises a save that doesn't exist).
 */
class PlayCtaLabelTest {

    @Test
    fun noSessionAlwaysReturnsNewGameRegardlessOfPlayTime() {
        // The playtime input is irrelevant when there's no session;
        // both branches must return the same label, otherwise the
        // hero would advertise "Resume — 0m" before any save existed.
        assertEquals("New game", gameDetailPlayLabel(PlaySemantics.NoSession, 0L))
        assertEquals("New game", gameDetailPlayLabel(PlaySemantics.NoSession, 9999L))
    }

    @Test
    fun resumesFromSaveStateWithoutPlayTimeStaysBare() {
        // A session exists but the user only logged a few seconds —
        // not worth surfacing "< 1 min" on a hero CTA.
        assertEquals("Resume", gameDetailPlayLabel(PlaySemantics.ResumesFromSaveState, 0L))
        assertEquals("Resume", gameDetailPlayLabel(PlaySemantics.ResumesFromSaveState, 59L))
    }

    @Test
    fun launchesFreshWithoutPlayTimeStaysBare() {
        assertEquals("Continue", gameDetailPlayLabel(PlaySemantics.LaunchesFresh, 0L))
        assertEquals("Continue", gameDetailPlayLabel(PlaySemantics.LaunchesFresh, 59L))
    }

    @Test
    fun resumeAtThresholdGetsMinuteSuffix() {
        // 60 s is the exact threshold — formatPlayTime returns "1m"
        // here, NOT "< 1 min". Both labels should reflect that.
        assertEquals("Resume — 1m", gameDetailPlayLabel(PlaySemantics.ResumesFromSaveState, 60L))
    }

    @Test
    fun resumeWithHoursAndMinutesUsesTheSameSuffixFormatAsRestOfApp() {
        // 27h 14m mirrors the Chrono Trigger reference value used by
        // the Hyrule design exploration. Format must match the rest
        // of the app's `formatPlayTime` so the button text stays
        // consistent with the SessionsSection / SessionDetail / etc.
        val twentySevenHoursFourteenMinutes = 27L * 3600 + 14L * 60
        assertEquals(
            "Resume — 27h 14m",
            gameDetailPlayLabel(PlaySemantics.ResumesFromSaveState, twentySevenHoursFourteenMinutes),
        )
    }

    @Test
    fun continueWithMinutesOnly() {
        // LaunchesFresh covers ScummVM and console-save-state-disabled
        // games. The label must be "Continue", not "Resume" — getting
        // this wrong is exactly the bug PlaySemantics fixed in #884.
        assertEquals("Continue — 45m", gameDetailPlayLabel(PlaySemantics.LaunchesFresh, 45L * 60))
    }
}
