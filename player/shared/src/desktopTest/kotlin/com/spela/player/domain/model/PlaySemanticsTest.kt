package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaySemanticsTest {

    @Test
    fun noSession_alwaysPlay() {
        val out = resolvePlaySemantics(
            hasSession = false,
            consoleSaveStateSupport = true,
            effectiveChoice = SaveStateChoice.Enabled,
        )
        assertEquals(PlaySemantics.NoSession, out)
    }

    @Test
    fun noSession_overridesEverythingElse() {
        // Even if the user has actively disabled save states or the
        // console doesn't support them, no session means "Play". The
        // label should never imply continuation when there's nothing
        // to continue from.
        val out = resolvePlaySemantics(
            hasSession = false,
            consoleSaveStateSupport = false,
            effectiveChoice = SaveStateChoice.Disabled,
        )
        assertEquals(PlaySemantics.NoSession, out)
    }

    @Test
    fun sessionExists_consoleSupportsAndEnabled_resumes() {
        val out = resolvePlaySemantics(
            hasSession = true,
            consoleSaveStateSupport = true,
            effectiveChoice = SaveStateChoice.Enabled,
        )
        assertEquals(PlaySemantics.ResumesFromSaveState, out)
    }

    @Test
    fun sessionExists_askOnceCountsAsAutoLoad() {
        // AskOnce is a deliberate "haven't decided yet" — the in-game
        // overlay treats it as Enabled until the prompt resolves. The
        // hero label should match — promising "Resume" here is honest
        // because the auto-load will fire.
        val out = resolvePlaySemantics(
            hasSession = true,
            consoleSaveStateSupport = true,
            effectiveChoice = SaveStateChoice.AskOnce,
        )
        assertEquals(PlaySemantics.ResumesFromSaveState, out)
    }

    @Test
    fun sessionExists_consoleDoesNotSupport_continues() {
        // ScummVM, demo cores, etc.
        val out = resolvePlaySemantics(
            hasSession = true,
            consoleSaveStateSupport = false,
            effectiveChoice = SaveStateChoice.Enabled,
        )
        assertEquals(PlaySemantics.LaunchesFresh, out)
    }

    @Test
    fun sessionExists_consoleSupportsButUserDisabled_continues() {
        // Per-console / per-game opt-out via #804. "Resume" would
        // over-promise — launch goes to the title screen.
        val out = resolvePlaySemantics(
            hasSession = true,
            consoleSaveStateSupport = true,
            effectiveChoice = SaveStateChoice.Disabled,
        )
        assertEquals(PlaySemantics.LaunchesFresh, out)
    }
}
