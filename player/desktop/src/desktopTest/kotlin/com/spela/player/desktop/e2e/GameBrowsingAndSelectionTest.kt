package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
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
    fun homeScreenShowsConsolesAfterLoad() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        // HomeScreen has LaunchedEffect that loads dashboard
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Should show consoles section
        onNodeWithText("Consoles").assertIsDisplayed()
        onNodeWithText("Nintendo Entertainment System").assertIsDisplayed()
        onNodeWithText("Super Nintendo").assertIsDisplayed()
    }

    @Test
    fun homeScreenShowsContinuePlayingSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Recent games should be displayed
        onNodeWithText("Continue Playing").assertIsDisplayed()
        onNodeWithText("Castlevania").assertIsDisplayed()
    }

    @Test
    fun tappingConsoleNavigatesToConsoleScreen() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Tap on NES console
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games").performClick()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Should navigate to console screen and show NES games
        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("Super Mario Bros.").assertIsDisplayed()
        onNodeWithText("Mega Man 2").assertIsDisplayed()
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
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Game detail should show game info
        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("About").assertIsDisplayed()
        onNodeWithText("A classic action platformer.").assertIsDisplayed()
        onNodeWithText("Konami").assertIsDisplayed()
    }

    @Test
    fun gameDetailShowsDownloadButtonWhenNotCached() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

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
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Game is cached, so Play should be shown
        onNodeWithContentDescription("Play Castlevania").assertIsDisplayed()
    }

    @Test
    fun topBarIconsNavigateToDownloadsAndSettings() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Home screen should show Downloads and Settings icons in top bar
        onNodeWithContentDescription("Downloads").assertIsDisplayed()
        onNodeWithContentDescription("Settings").assertIsDisplayed()

        // Navigate to Downloads via top bar icon
        onNodeWithContentDescription("Downloads").performClick()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Should show Downloads screen with back button
        onNodeWithContentDescription("Go back").assertIsDisplayed()

        // Navigate back to Home
        onNodeWithContentDescription("Go back").performClick()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Navigate to Settings via top bar icon
        onNodeWithContentDescription("Settings").performClick()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Should show Settings screen with back button
        onNodeWithContentDescription("Go back").assertIsDisplayed()

        // Navigate back to Home
        onNodeWithContentDescription("Go back").performClick()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Should be back on Home
        onNodeWithText("Consoles").assertIsDisplayed()
    }
}
