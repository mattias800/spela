package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * E2E tests for game download and launch flow.
 * Tests: Downloading a game, verifying Play button appears, launching emulation.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GameDownloadAndLaunchTest {

    private fun advance(harness: SpelaTestHarness, scope: ComposeUiTest) {
        harness.testDispatcher.scheduler.advanceUntilIdle()
        scope.waitForIdle()
        // Second advance to process any newly enqueued coroutines
        harness.testDispatcher.scheduler.advanceUntilIdle()
        scope.waitForIdle()
    }

    @Test
    fun downloadGameThenShowPlayButton() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        // Start at game detail for Castlevania (not cached)
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )

        setContent { harness.App() }

        advance(harness, this)

        // Should show Download button initially
        onNodeWithContentDescription("Download Castlevania").assertIsDisplayed()

        // Tap Download
        onNodeWithContentDescription("Download Castlevania").performClick()
        advance(harness, this)

        // After download completes, Play button should appear
        onNodeWithContentDescription("Play Castlevania").assertIsDisplayed()
    }

    @Test
    fun tappingPlayShowsInGameOverlay() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        // Pre-cache game so Play button is available
        harness.downloadRepo.preCacheGame("1")

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )

        setContent { harness.App() }

        advance(harness, this)

        // Tap Play
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness, this)

        // In-game overlay should appear with game controls
        onNodeWithText("Exit Game").assertIsDisplayed()
        onNodeWithText("Resume").assertIsDisplayed()
    }

    @Test
    fun emulationStartsAfterPlayTapped() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.downloadRepo.preCacheGame("1")

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )

        setContent { harness.App() }

        advance(harness, this)

        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness, this)

        // Verify emulation actually started via the fake controller
        assertTrue(harness.libretroController.isRunning, "Emulation should be running")
        assertTrue(harness.libretroController.startCallCount > 0, "Start should have been called")
    }

    @Test
    fun overlayShowsGameTitle() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.downloadRepo.preCacheGame("1")

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )

        setContent { harness.App() }

        advance(harness, this)

        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness, this)

        // The overlay should show the game title
        onNodeWithText("Castlevania").assertIsDisplayed()
    }
}
