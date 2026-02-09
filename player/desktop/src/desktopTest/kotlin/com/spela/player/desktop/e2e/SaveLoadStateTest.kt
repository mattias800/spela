package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
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

    private fun advance(harness: SpelaTestHarness, scope: ComposeUiTest) {
        harness.testDispatcher.scheduler.advanceUntilIdle()
        scope.waitForIdle()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        scope.waitForIdle()
    }

    @Test
    fun saveStatePersistsThroughSaveRepository() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        setContent { harness.App() }
        advance(harness, this)

        // Start game
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness, this)

        // Tap Save
        onNodeWithContentDescription("Save").performClick()
        advance(harness, this)

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
        advance(harness, this)

        // Start game
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness, this)

        // First save, then load
        onNodeWithContentDescription("Save").performClick()
        advance(harness, this)

        val loadCountBefore = harness.libretroController.loadCallCount

        onNodeWithContentDescription("Load").performClick()
        advance(harness, this)

        assertTrue(
            harness.libretroController.loadCallCount > loadCountBefore,
            "Load state should have been deserialized"
        )
    }

    @Test
    fun exitGameTriggersAutoSave() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        setContent { harness.App() }
        advance(harness, this)

        // Start game
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness, this)

        val saveCountBefore = harness.libretroController.saveCallCount

        // Exit game (should auto-save)
        onNodeWithText("Exit Game").performClick()
        advance(harness, this)

        // Auto-save should have been triggered (serialize called during stop)
        assertTrue(
            harness.libretroController.saveCallCount > saveCountBefore,
            "Exiting game should trigger auto-save serialization"
        )
    }
}
