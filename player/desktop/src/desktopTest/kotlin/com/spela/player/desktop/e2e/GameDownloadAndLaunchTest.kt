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
 * E2E tests for game download and launch flow.
 * Tests: Downloading a game, verifying Play button appears, launching emulation.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GameDownloadAndLaunchTest {

    @Test
    fun downloadGameThenShowPlayButton() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        // Start at game detail for Castlevania (not cached)
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )

        setContent { harness.App() }

        advance(harness)

        // Should show Download button initially
        onNodeWithTag("game_detail_download_button").assertIsDisplayed()

        // Tap Download
        onNodeWithTag("game_detail_download_button").performClick()
        advance(harness)

        // After download completes, Play button should appear
        onNodeWithTag("game_detail_play_button").assertIsDisplayed()
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

        advance(harness)

        // Tap Play
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        // Open overlay (hidden by default) and verify controls
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        onNodeWithText("Exit Game").assertIsDisplayed()
        onNodeWithText("Continue").assertIsDisplayed()
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

        advance(harness)

        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

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

        advance(harness)

        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        // Open overlay to see game title
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        // The overlay should show the game title (multiple nodes may match due to merged semantics)
        onAllNodesWithText("Castlevania").onFirst().assertIsDisplayed()
    }
}
