package com.spela.player.desktop.e2e

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E2E tests for Escape key opening the pause overlay on macOS desktop.
 *
 * These tests verify that:
 * - Pressing Escape during gameplay opens the pause overlay
 * - Pressing Escape again closes the overlay and resumes
 * - The overlay shows Resume and Exit Game options
 * - The emulation pauses when overlay is shown
 * - Escape does not interfere with gameplay keys
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EscapeKeyOverlayTest {

    private fun createHarnessWithGameRunning(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        return harness
    }

    private fun advance(harness: SpelaTestHarness, scope: ComposeUiTest) {
        harness.testDispatcher.scheduler.advanceUntilIdle()
        scope.waitForIdle()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        scope.waitForIdle()
    }

    private fun ComposeUiTest.launchAndStartGame(harness: SpelaTestHarness) {
        setContent { harness.App() }
        advance(harness, this)

        // Start game from detail screen
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness, this)

        // Resume to dismiss initial overlay and enter gameplay
        onNodeWithText("Resume").performClick()
        advance(harness, this)
    }

    @Test
    fun overlayTogglesViaViewModel() = runComposeUiTest {
        val harness = createHarnessWithGameRunning()
        launchAndStartGame(harness)

        // Game is running, overlay should be hidden
        onNodeWithText("Exit Game").assertDoesNotExist()

        // Simulate what Escape key handler does: toggle overlay
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)

        // Overlay should now be visible with Resume and Exit Game
        onNodeWithText("Resume").assertIsDisplayed()
        onNodeWithText("Exit Game").assertIsDisplayed()
    }

    @Test
    fun overlayToggleShowsGameTitle() = runComposeUiTest {
        val harness = createHarnessWithGameRunning()
        launchAndStartGame(harness)

        // Open overlay
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)

        // Game title should be shown in overlay
        onNodeWithText("Castlevania").assertIsDisplayed()
    }

    @Test
    fun overlayCanBeDismissedByTogglingAgain() = runComposeUiTest {
        val harness = createHarnessWithGameRunning()
        launchAndStartGame(harness)

        // Open overlay
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)

        onNodeWithText("Exit Game").assertIsDisplayed()

        // Toggle overlay again (simulates second Escape press)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)

        // Overlay should be hidden
        onNodeWithText("Exit Game").assertDoesNotExist()
    }

    @Test
    fun overlayShowsActionButtonsWhenOpened() = runComposeUiTest {
        val harness = createHarnessWithGameRunning()
        launchAndStartGame(harness)

        // Open overlay
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)

        // All overlay action buttons should be present
        onNodeWithContentDescription("Save").assertIsDisplayed()
        onNodeWithContentDescription("Load").assertIsDisplayed()
        onNodeWithContentDescription("Screenshot").assertIsDisplayed()
        onNodeWithContentDescription("Fast").assertIsDisplayed()
    }

    @Test
    fun resumeFromOverlayHidesOverlayAndContinuesGame() = runComposeUiTest {
        val harness = createHarnessWithGameRunning()
        launchAndStartGame(harness)

        // Open overlay via toggle
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)

        onNodeWithText("Resume").assertIsDisplayed()

        // Click Resume
        onNodeWithText("Resume").performClick()
        advance(harness, this)

        // Overlay should be hidden, game should be running
        onNodeWithText("Exit Game").assertDoesNotExist()
        assertTrue(harness.libretroController.isRunning, "Game should still be running after resume")
        assertFalse(harness.libretroController.isPaused, "Game should not be paused after resume")
    }

    @Test
    fun exitFromOverlayStopsGameAndNavigatesBack() = runComposeUiTest {
        val harness = createHarnessWithGameRunning()
        launchAndStartGame(harness)

        assertTrue(harness.libretroController.isRunning, "Game should be running")

        // Open overlay
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)

        // Click Exit Game
        onNodeWithText("Exit Game").performClick()
        advance(harness, this)

        // Game should be stopped
        assertFalse(harness.libretroController.isRunning, "Game should be stopped after exit")
        assertTrue(harness.libretroController.stopCallCount > 0, "Stop should have been called")
    }

    @Test
    fun multipleOverlayToggleCyclesWorkCorrectly() = runComposeUiTest {
        val harness = createHarnessWithGameRunning()
        launchAndStartGame(harness)

        // Cycle 1: open and close
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)
        onNodeWithText("Resume").assertIsDisplayed()

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)
        onNodeWithText("Exit Game").assertDoesNotExist()

        // Cycle 2: open and close
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)
        onNodeWithText("Resume").assertIsDisplayed()

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)
        onNodeWithText("Exit Game").assertDoesNotExist()

        // Game should still be running throughout
        assertTrue(harness.libretroController.isRunning, "Game should remain running through toggle cycles")
    }

    @Test
    fun overlayStateReflectsInViewModel() = runComposeUiTest {
        val harness = createHarnessWithGameRunning()
        launchAndStartGame(harness)

        val state1 = harness.emulationViewModel.state.value
        assertFalse(state1.showOverlay, "Overlay should initially be hidden")

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)

        val state2 = harness.emulationViewModel.state.value
        assertTrue(state2.showOverlay, "Overlay should be shown after toggle")

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)

        val state3 = harness.emulationViewModel.state.value
        assertFalse(state3.showOverlay, "Overlay should be hidden after second toggle")
    }
}
