package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.ui.feature.ingame.EmulationErrorOverlay
import kotlin.test.Test

/**
 * Regression test for #1411: when a game fails to start, the error overlay's
 * Exit button must auto-focus so a gamepad acts on it. Without the fix, the
 * overlay had no focused target, so the A button activated whatever was still
 * focused on the screen underneath (e.g. the game-detail Play / "…" actions).
 *
 * The Exit button is the only focusable node in the overlay, so asserting it
 * is focused after the layout-settle delay directly proves the auto-focus.
 */
@OptIn(ExperimentalTestApi::class)
class EmulationErrorOverlayTest {

    @Test
    fun exitButtonAutoFocusesWhenErrorShown() = runComposeUiTest {
        setContent {
            EmulationErrorOverlay(error = "Failed to prepare game", onExit = {})
        }

        // Advance past the ~120 ms layout-settle delay so the auto-focus
        // requestFocus() fires.
        mainClock.advanceTimeBy(300)
        waitForIdle()

        onNodeWithTag("emulation_error_exit").assertIsFocused()
    }
}
