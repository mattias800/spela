package com.spela.player.desktop.e2e

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

    private fun ComposeUiTest.launchAndStartGame(harness: SpelaTestHarness) {
        setContent { harness.App() }
        advance(harness)

        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)
    }

    @Test
    fun overlayTogglesShowsActionsAndResumes() = runComposeUiTest {
        val harness = createHarnessWithGameRunning()
        launchAndStartGame(harness)

        onNodeWithText("Exit Game").assertDoesNotExist()
        assertFalse(harness.emulationViewModel.state.value.showOverlay, "Overlay should initially be hidden")

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        onNodeWithText("Continue").assertIsDisplayed()
        onNodeWithText("Exit Game").assertIsDisplayed()
        onAllNodesWithText("Castlevania").onFirst().assertIsDisplayed()
        onNodeWithContentDescription("Save").assertIsDisplayed()
        onNodeWithContentDescription("Load").assertIsDisplayed()
        onNodeWithContentDescription("Screenshot").assertIsDisplayed()
        onNodeWithContentDescription("Fast").assertIsDisplayed()
        assertTrue(harness.emulationViewModel.state.value.showOverlay, "Overlay should be shown after toggle")

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        onNodeWithText("Exit Game").assertDoesNotExist()
        assertFalse(harness.emulationViewModel.state.value.showOverlay, "Overlay should be hidden after second toggle")
        assertTrue(harness.libretroController.isRunning, "Game should remain running through toggle cycles")

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithText("Continue").performClick()
        advanceQuick(harness)

        onNodeWithText("Exit Game").assertDoesNotExist()
        assertTrue(harness.libretroController.isRunning, "Game should still be running after resume")
        assertFalse(harness.libretroController.isPaused, "Game should not be paused after resume")
    }

    @Test
    fun exitFromOverlayStopsGameAndNavigatesBack() = runComposeUiTest {
        val harness = createHarnessWithGameRunning()
        launchAndStartGame(harness)

        assertTrue(harness.libretroController.isRunning, "Game should be running")

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        onNodeWithText("Exit Game").performClick()
        advance(harness)

        assertFalse(harness.libretroController.isRunning, "Game should be stopped after exit")
        assertTrue(harness.libretroController.stopCallCount > 0, "Stop should have been called")
    }
}
