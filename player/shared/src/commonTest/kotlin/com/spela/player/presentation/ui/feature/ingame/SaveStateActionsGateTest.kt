package com.spela.player.presentation.ui.feature.ingame

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the gate that decides whether the in-game overlay shows the
 * Save / Load / Challenge buttons (#804 phase 4 spec point d).
 *
 * Three states matter:
 *
 *   supportsSaveStates=false               → never show (core can't serialise)
 *   supportsSaveStates=true,opt-out=true   → never show (user disabled)
 *   supportsSaveStates=true,opt-out=false  → show
 *
 * Locked in as a pure function so a future copy/paste of the gate
 * across multiple Compose call sites (HUD button, secondary screen
 * indicator, etc.) can reuse the same resolver and stay consistent.
 */
class SaveStateActionsGateTest {

    @Test
    fun showsWhenCoreSupportsAndUserHasNotOptedOut() {
        assertTrue(
            shouldShowSaveStateActions(
                supportsSaveStates = true,
                saveStatesOptedOut = false,
            ),
        )
    }

    @Test
    fun hiddenWhenUserHasOptedOut() {
        assertFalse(
            shouldShowSaveStateActions(
                supportsSaveStates = true,
                saveStatesOptedOut = true,
            ),
        )
    }

    @Test
    fun hiddenWhenCoreDoesNotSupportSaveStates() {
        // Pre-#804 behaviour preserved: cores that can't serialise
        // (e.g. ScummVM) keep the row hidden regardless of opt-out.
        assertFalse(
            shouldShowSaveStateActions(
                supportsSaveStates = false,
                saveStatesOptedOut = false,
            ),
        )
        assertFalse(
            shouldShowSaveStateActions(
                supportsSaveStates = false,
                saveStatesOptedOut = true,
            ),
        )
    }
}
