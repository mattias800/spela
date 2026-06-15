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
 * E2E tests for in-game overlay interactions.
 * Tests: Overlay buttons (Save, Load, Screenshot, Fast Forward),
 *        Resume/Exit Game, and overlay toggle.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class InGameOverlayTest {

    private fun createHarnessWithGameReady(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        return harness
    }

    private fun ComposeUiTest.startGame(harness: SpelaTestHarness) {
        setContent { harness.App() }
        advance(harness)

        // Start game
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        // Open overlay (hidden by default on game start)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
    }

    @Test
    fun overlayShowsSaveLoadScreenshotButtons() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGame(harness)

        // Verify overlay action buttons are present
        onNodeWithContentDescription("Save").assertIsDisplayed()
        onNodeWithContentDescription("Load").assertIsDisplayed()
        onNodeWithContentDescription("Screenshot").assertIsDisplayed()
    }

    @Test
    fun keyMappingOverlayShowsLabeledList() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGame(harness)

        harness.emulationViewModel.onIntent(EmulationIntent.ShowKeyMapping)
        advanceQuick(harness)

        // The editor now shows a per-console labeled mapping list (#1335), not a
        // pictorial controller diagram.
        onNodeWithTag("mapping_list").assertExists()

        // Tapping a button row enters single-button listening mode for it.
        onNodeWithTag("mapping_list").onChildren().onFirst().performClick()
        advanceQuick(harness)
        assertTrue(
            harness.keyMappingViewModel.state.value.currentMappingButton != null,
            "Tapping a mapping row should enter listening mode",
        )
    }

    @Test
    fun keyMappingOverlayOffersPerGameOverrideAndSaves() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGame(harness)

        // Open the in-game key-mapping editor.
        harness.emulationViewModel.onIntent(EmulationIntent.ShowKeyMapping)
        advanceQuick(harness)

        // A game is loaded, so the per-game override affordance is offered (#1336).
        onNodeWithTag("save_game_override").assertExists()

        onNodeWithTag("save_game_override").performClick()
        advanceQuick(harness)

        assertTrue(
            harness.keyMappingViewModel.state.value.hasGameOverride,
            "Saving in the overlay should create a per-game override",
        )
    }

    @Test
    fun saveTriggersSerialization() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGame(harness)

        val saveCountBefore = harness.libretroController.saveCallCount

        // Tap Save
        onNodeWithContentDescription("Save").performClick()
        advance(harness)

        assertTrue(
            harness.libretroController.saveCallCount > saveCountBefore,
            "Save should have triggered serialization"
        )
    }

    @Test
    fun loadTriggersUnserialization() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        // The Load button downloads the session's auto-save. Seed a
        // session + auto-save so there's something to unserialize;
        // mirrors the state "a prior play left an auto-save behind".
        val sessionId = "session-1"
        harness.sessionRepo.sessions.add(
            com.spela.player.domain.model.GameSession(id = sessionId, gameId = "1", name = "Default"),
        )
        harness.sessionRepo.preSeedAutoSave(sessionId)

        startGame(harness)

        val loadCountBefore = harness.libretroController.loadCallCount

        // Tap Load
        onNodeWithContentDescription("Load").performClick()
        advance(harness)

        assertTrue(
            harness.libretroController.loadCallCount > loadCountBefore,
            "Load should have triggered unserialization"
        )
    }

    @Test
    fun fastForwardToggle() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGame(harness)

        assertFalse(harness.libretroController.isFastForward, "Fast forward should be off initially")

        // Tap Fast Forward button (labeled "Fast" initially)
        onNodeWithContentDescription("Fast").performClick()
        advanceQuick(harness)

        assertTrue(harness.libretroController.isFastForward, "Fast forward should be on")

        // Tap again to toggle off (now labeled "Normal")
        onNodeWithContentDescription("Normal").performClick()
        advanceQuick(harness)

        assertFalse(harness.libretroController.isFastForward, "Fast forward should be off again")
    }

    @Test
    fun resumeHidesOverlay() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGame(harness)

        // Overlay should be visible
        onNodeWithText("Continue").assertIsDisplayed()

        // Tap Resume
        onNodeWithText("Continue").performClick()
        advanceQuick(harness)

        // Overlay panel should be hidden (Exit Game text should no longer be visible)
        onNodeWithText("Exit Game").assertDoesNotExist()

        // But the game should still be running
        assertTrue(harness.libretroController.isRunning)
        assertFalse(harness.libretroController.isPaused)
    }

    @Test
    fun exitGameStopsEmulationAndHidesOverlay() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGame(harness)

        assertTrue(harness.libretroController.isRunning, "Game should be running before exit")

        // Tap Exit Game
        onNodeWithText("Exit Game").performClick()
        advance(harness)

        // Emulation should stop
        assertFalse(harness.libretroController.isRunning, "Emulation should be stopped after exit")
        assertTrue(harness.libretroController.stopCallCount > 0, "Stop should have been called")

        // Overlay should be hidden
        onNodeWithText("Exit Game").assertDoesNotExist()
    }

    @Test
    fun overlayHidesSaveLoadForNonSaveStateConsoles() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        // Configure controller to report no save state support (e.g. GameCube via Dolphin)
        harness.libretroController.supportsSaveStatesResult = false

        startGame(harness)

        // Save and Load buttons should NOT be present
        onNodeWithContentDescription("Save").assertDoesNotExist()
        onNodeWithContentDescription("Load").assertDoesNotExist()

        // Other action buttons should still be present
        onNodeWithContentDescription("Screenshot").assertIsDisplayed()
    }
}
