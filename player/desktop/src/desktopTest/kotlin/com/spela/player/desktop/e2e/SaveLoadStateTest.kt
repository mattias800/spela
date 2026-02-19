package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * E2E tests for save/load state functionality.
 * Tests: Manual save, manual load, auto-save on exit.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SaveLoadStateTest {

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
    fun saveStatePersistsThroughSaveRepository() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        setContent { harness.App() }
        advance(harness)

        // Start game
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)

        // Open overlay to access Save button
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        // Tap Save
        onNodeWithContentDescription("Save").performClick()
        advance(harness)

        // Verify save was persisted through the repository
        assertTrue(
            harness.libretroController.saveCallCount > 0,
            "Save state should have been serialized"
        )
    }

    @Test
    fun loadStateRestoresFromSaveRepository() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        setContent { harness.App() }
        advance(harness)

        // Start game
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)

        // Open overlay to access Save/Load buttons
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        // First save, then load
        onNodeWithContentDescription("Save").performClick()
        advance(harness)

        val loadCountBefore = harness.libretroController.loadCallCount

        onNodeWithContentDescription("Load").performClick()
        advance(harness)

        assertTrue(
            harness.libretroController.loadCallCount > loadCountBefore,
            "Load state should have been deserialized"
        )
    }

    @Test
    fun exitGameTriggersAutoSave() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        setContent { harness.App() }
        advance(harness)

        // Start game
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)

        val saveCountBefore = harness.libretroController.saveCallCount

        // Open overlay to access Exit Game button
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        // Exit game (should auto-save)
        onNodeWithText("Exit Game").performClick()
        advance(harness)

        // Auto-save should have been triggered (serialize called during stop)
        assertTrue(
            harness.libretroController.saveCallCount > saveCountBefore,
            "Exiting game should trigger auto-save serialization"
        )
    }
}
