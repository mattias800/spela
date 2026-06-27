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
    fun overlayActionsSaveLoadFastForwardResumeAndExit() = runComposeUiTest {
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

        onNodeWithContentDescription("Save").assertIsDisplayed()
        onNodeWithContentDescription("Load").assertIsDisplayed()
        onNodeWithContentDescription("Screenshot").assertIsDisplayed()

        val saveCountBefore = harness.libretroController.saveCallCount
        onNodeWithContentDescription("Save").performClick()
        advance(harness)
        assertTrue(
            harness.libretroController.saveCallCount > saveCountBefore,
            "Save should have triggered serialization",
        )

        val loadCountBefore = harness.libretroController.loadCallCount
        onNodeWithContentDescription("Load").performClick()
        advance(harness)
        assertTrue(
            harness.libretroController.loadCallCount > loadCountBefore,
            "Load should have triggered unserialization",
        )

        assertFalse(harness.libretroController.isFastForward, "Fast forward should be off initially")
        onNodeWithContentDescription("Fast").performClick()
        advanceQuick(harness)
        assertTrue(harness.libretroController.isFastForward, "Fast forward should be on")
        onNodeWithContentDescription("Normal").performClick()
        advanceQuick(harness)
        assertFalse(harness.libretroController.isFastForward, "Fast forward should be off again")

        onNodeWithText("Continue").assertIsDisplayed()
        onNodeWithText("Continue").performClick()
        advanceQuick(harness)

        onNodeWithText("Exit Game").assertDoesNotExist()
        assertTrue(harness.libretroController.isRunning)
        assertFalse(harness.libretroController.isPaused)

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithText("Exit Game").performClick()
        advance(harness)
        assertFalse(harness.libretroController.isRunning, "Emulation should be stopped after exit")
        assertTrue(harness.libretroController.stopCallCount > 0, "Stop should have been called")
        onNodeWithText("Exit Game").assertDoesNotExist()
    }

    @Test
    fun keyMappingOverlayShowsListAndSavesPerGameOverride() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGame(harness)

        harness.emulationViewModel.onIntent(EmulationIntent.ShowKeyMapping)
        advanceQuick(harness)

        // The editor now shows a per-console labeled mapping list (#1335), not a
        // pictorial controller diagram.
        onNodeWithTag("mapping_list").assertExists()
        onNodeWithTag("save_game_override").assertExists()

        onNodeWithTag("save_game_override").performClick()
        advanceQuick(harness)

        assertTrue(
            harness.keyMappingViewModel.state.value.hasGameOverride,
            "Saving in the overlay should create a per-game override",
        )

        // Tapping a button row enters single-button listening mode for it.
        onNodeWithTag("mapping_list").onChildren().onFirst().performClick()
        advanceQuick(harness)
        assertTrue(
            harness.keyMappingViewModel.state.value.currentMappingButton != null,
            "Tapping a mapping row should enter listening mode",
        )
    }

    @Test
    fun overlayHidesSaveLoadForNonSaveStateConsoles() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        // Configure controller to report no save state support (e.g. GameCube via Dolphin)
        harness.libretroController.supportsSaveStatesResult = false

        startGame(harness)

        onNodeWithContentDescription("Save").assertDoesNotExist()
        onNodeWithContentDescription("Load").assertDoesNotExist()
        onNodeWithContentDescription("Screenshot").assertIsDisplayed()
    }

}
