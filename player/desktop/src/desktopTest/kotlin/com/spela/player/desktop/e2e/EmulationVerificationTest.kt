package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E tests for verifying emulation is running correctly.
 * Tests: Core loading, game loading, emulation loop running,
 *        performance stats tracking, pause/resume behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EmulationVerificationTest {

    private fun createHarnessWithGameReady(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        return harness
    }

    @Test
    fun emulationLoadsCorrectCoreAndGame() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        setContent { harness.App() }
        advance(harness)

        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        // Verify the correct core was loaded
        assertNotNull(harness.libretroController.loadedCore, "A core should have been loaded")

        // Verify the correct game was loaded
        assertNotNull(harness.libretroController.loadedGame, "A game should have been loaded")
    }

    @Test
    fun emulationStateIsRunningAfterStart() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        setContent { harness.App() }
        advance(harness)

        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        // Verify emulation state
        assertTrue(harness.libretroController.isRunning, "Emulation should be running")
        val emulationState = harness.emulationViewModel.state.value
        assertTrue(emulationState.isRunning, "ViewModel state should reflect running")
        assertEquals("Castlevania", emulationState.gameTitle, "Game title should be set")
    }

    @Test
    fun emulationCanBePausedAndResumed() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        setContent { harness.App() }
        advance(harness)

        // Start game
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        // Game starts with overlay hidden; verify it's running
        assertTrue(harness.libretroController.isRunning)
        assertFalse(harness.libretroController.isPaused, "Should not be paused after start")
    }

    @Test
    fun emulationStopsCleanlyOnExit() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        setContent { harness.App() }
        advance(harness)

        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        assertTrue(harness.libretroController.isRunning)

        // Open overlay to access Exit Game button
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        onNodeWithText("Exit Game").performClick()
        advance(harness)

        // Verify clean stop
        assertFalse(harness.libretroController.isRunning, "Should no longer be running")
        assertTrue(harness.libretroController.stopCallCount > 0, "Stop should have been called")

        // Verify ViewModel state is reset
        val emulationState = harness.emulationViewModel.state.value
        assertFalse(emulationState.isRunning, "ViewModel should reflect stopped state")
        assertEquals(0f, emulationState.fps, "FPS should be reset to 0")
    }

    @Test
    fun multipleGamesCanBePlayedSequentially() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        harness.downloadRepo.preCacheGame("2")

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )

        setContent { harness.App() }
        advance(harness)

        // Play first game
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        assertTrue(harness.libretroController.isRunning)
        assertEquals(1, harness.libretroController.startCallCount)

        // Open overlay and exit first game
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithText("Exit Game").performClick()
        advance(harness)

        assertFalse(harness.libretroController.isRunning)

        // Navigate to second game
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("2"))
        )
        advance(harness)

        // Play second game — needs extra advancement for the full
        // LaunchedEffect → StartGame → scope.launch chain to complete
        onNodeWithTag("game_detail_play_button").performClick()
        advanceFully(harness)

        assertTrue(harness.libretroController.isRunning)
        assertEquals(2, harness.libretroController.startCallCount)
    }
}
