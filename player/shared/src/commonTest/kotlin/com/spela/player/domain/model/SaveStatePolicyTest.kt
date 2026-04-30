package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the resolver the in-game overlay calls every render to decide
 * whether to grey out the save/load buttons. If this drifts the
 * Disabled state can leak through as Enabled (silent footgun for the
 * user that asked for opt-out) or vice versa (broken UX for users
 * who didn't opt out).
 */
class SaveStatePolicyTest {

    @Test
    fun overrideTakesPrecedenceOverTier() {
        val resolved = effectiveSaveStateChoice(
            consoleAbbr = "GC",
            tier = SaveStatePolicyTier.Large,
            overrides = mapOf("gc" to SaveStateChoice.Enabled),
        )
        assertEquals(SaveStateChoice.Enabled, resolved)
    }

    @Test
    fun overrideMatchedCaseInsensitively() {
        // The map keys are stored lowercased on the server but the
        // game-launch flow passes the abbreviation as-typed. The
        // resolver must canonicalise so a case mismatch doesn't
        // silently fall through to the tier default.
        val resolved = effectiveSaveStateChoice(
            consoleAbbr = "GC",
            tier = SaveStatePolicyTier.Large,
            overrides = mapOf("gc" to SaveStateChoice.Disabled),
        )
        assertEquals(SaveStateChoice.Disabled, resolved)

        val resolvedUpper = effectiveSaveStateChoice(
            consoleAbbr = "gc",
            tier = SaveStatePolicyTier.Large,
            overrides = mapOf("gc" to SaveStateChoice.Disabled),
        )
        assertEquals(SaveStateChoice.Disabled, resolvedUpper)
    }

    @Test
    fun smallTierDefaultsToEnabled() {
        val resolved = effectiveSaveStateChoice(
            consoleAbbr = "NES",
            tier = SaveStatePolicyTier.Small,
            overrides = emptyMap(),
        )
        assertEquals(SaveStateChoice.Enabled, resolved)
    }

    @Test
    fun mediumTierDefaultsToEnabled() {
        val resolved = effectiveSaveStateChoice(
            consoleAbbr = "PSX",
            tier = SaveStatePolicyTier.Medium,
            overrides = emptyMap(),
        )
        assertEquals(SaveStateChoice.Enabled, resolved)
    }

    @Test
    fun largeTierDefaultsToAskOnce() {
        val resolved = effectiveSaveStateChoice(
            consoleAbbr = "GC",
            tier = SaveStatePolicyTier.Large,
            overrides = emptyMap(),
        )
        assertEquals(SaveStateChoice.AskOnce, resolved)
    }

    @Test
    fun overrideForOneConsoleDoesNotLeakToOthers() {
        val overrides = mapOf("gc" to SaveStateChoice.Disabled)
        val gc = effectiveSaveStateChoice("GC", SaveStatePolicyTier.Large, overrides)
        val ps2 = effectiveSaveStateChoice("PS2", SaveStatePolicyTier.Large, overrides)
        assertEquals(SaveStateChoice.Disabled, gc)
        assertEquals(SaveStateChoice.AskOnce, ps2, "no override → tier default")
    }

    @Test
    fun saveStatePolicyTierFallsBackToSmallOnUnknown() {
        // A future server tier (e.g. "huge") must not crash older
        // clients — they should treat it as the safest existing tier
        // (small = unbounded named saves) so the user keeps working.
        // The opposite default would silently disable saves.
        assertEquals(SaveStatePolicyTier.Small, SaveStatePolicyTier.fromApiId(null))
        assertEquals(SaveStatePolicyTier.Small, SaveStatePolicyTier.fromApiId(""))
        assertEquals(SaveStatePolicyTier.Small, SaveStatePolicyTier.fromApiId("future-tier"))
        assertEquals(SaveStatePolicyTier.Large, SaveStatePolicyTier.fromApiId("large"))
    }

    @Test
    fun saveStateChoiceUnknownReturnsNull() {
        // Unlike tier, an unknown choice must not silently become
        // Enabled — the DTO mapper drops the entry instead so the
        // resolver falls back to tier default for that console.
        assertEquals(null, SaveStateChoice.fromApiId(null))
        assertEquals(null, SaveStateChoice.fromApiId(""))
        assertEquals(null, SaveStateChoice.fromApiId("foo"))
        assertEquals(SaveStateChoice.Disabled, SaveStateChoice.fromApiId("disabled"))
    }
}
