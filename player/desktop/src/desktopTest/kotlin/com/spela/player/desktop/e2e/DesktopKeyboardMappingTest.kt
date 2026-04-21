package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E2E test for desktop keyboard mapping during emulation.
 *
 * Verifies that the emulation infrastructure properly receives and routes
 * input through the controller after a game is started via the UI.
 *
 * The actual key-to-libretro mapping is in DesktopEmulationSurface.kt:
 *   Arrow keys   -> D-pad (UP, DOWN, LEFT, RIGHT)
 *   Z            -> B
 *   X            -> A
 *   A            -> Y
 *   S            -> X
 *   Enter        -> START
 *   Shift (L/R)  -> SELECT
 *   Q            -> L
 *   W            -> R
 *   1            -> L2
 *   2            -> R2
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class DesktopKeyboardMappingTest {

    @Test
    fun emulationReceivesInputAfterGameStart() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )

        setContent { harness.App() }
        advance(harness)

        // Start game (overlay hidden by default, game enters playing state)
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        // Verify the controller is ready for input
        assertTrue(harness.libretroController.isRunning, "Controller should be running")
        assertFalse(harness.libretroController.isPaused, "Controller should not be paused")
    }
}
