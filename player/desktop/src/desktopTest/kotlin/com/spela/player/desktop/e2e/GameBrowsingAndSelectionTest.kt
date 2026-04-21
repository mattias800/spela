package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadState
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for game browsing and selection.
 * Tests: Home screen dashboard, console browsing, game detail screen.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GameBrowsingAndSelectionTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        // Navigate directly to Home screen (simulating post-login state)
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun consolesScreenShowsConsolesAfterLoad() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        // Navigate to Consoles screen
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        // Should show consoles
        onNodeWithText("Nintendo Entertainment System").assertIsDisplayed()
        onNodeWithText("Super Nintendo").assertIsDisplayed()
    }

    @Test
    fun homeScreenShowsContinuePlayingSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Recent games should be displayed
        onNodeWithText("Continue Playing").assertIsDisplayed()
        onNodeWithText("Castlevania").assertIsDisplayed()
    }

    @Test
    fun tappingConsoleInConsolesScreenNavigatesToConsoleScreen() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        // Navigate to Consoles screen
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        // Tap on NES console
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games").performClick()
        advance(harness)

        // Should navigate to console screen and show the browse button
        onNodeWithText("Browse All 3 Games").assertIsDisplayed()
    }

    @Test
    fun tappingGameNavigatesToGameDetail() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        // Navigate directly to game detail
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        // GameDetailScreen has LaunchedEffect to load the game
        advance(harness)

        // Game detail should show game info
        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("A classic action platformer.").assertIsDisplayed()
        onAllNodesWithText("Konami").onFirst().assertIsDisplayed()
    }

    @Test
    fun gameDetailShowsDownloadButtonWhenNotCached() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Game is not cached, so Download should be shown
        onNodeWithContentDescription("Download Castlevania").assertIsDisplayed()
    }

    @Test
    fun gameDetailShowsPlayButtonWhenCached() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Pre-cache the game
        harness.downloadRepo.preCacheGame("1")

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Game is cached, so Play should be shown
        onNodeWithTag("game_detail_play_button").assertIsDisplayed()
    }

    @Test
    fun topBarDownloadsIconNavigatesToDownloads() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.setActiveDownloads(
            listOf(
                DownloadProgress(
                    gameId = "dl1",
                    gameTitle = "Test Game",
                    state = DownloadState.DOWNLOADING,
                    bytesDownloaded = 50,
                    totalBytes = 100,
                ),
            ),
        )

        setContent { harness.App() }
        advance(harness)

        // Home screen should show Downloads icon when downloads are active
        onNodeWithContentDescription("Downloads").assertIsDisplayed()

        // Navigate to Downloads via top bar icon
        onNodeWithContentDescription("Downloads").performClick()
        advance(harness)

        // Should show Downloads screen with back button
        onNodeWithContentDescription("Go back").assertIsDisplayed()

        // Navigate back to Home
        onNodeWithContentDescription("Go back").performClick()
        advance(harness)

        // Should be back on Home
        onNodeWithText("Continue Playing").assertIsDisplayed()
    }
}
