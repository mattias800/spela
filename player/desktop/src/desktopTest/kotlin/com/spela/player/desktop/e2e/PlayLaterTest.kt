package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ActivityEvent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the Play Later feature on desktop.
 * Tests: Home screen section visibility, game detail toggle button, activity feed event.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class PlayLaterTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // ---- Home screen: Play Later section ----

    @Test
    fun homeScreenHidesPlayLaterSectionWhenEmpty() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // No games have isInPlayLater=true, so the section should not appear

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Play Later").assertDoesNotExist()
    }

    @Test
    fun homeScreenShowsPlayLaterSectionWhenGamesPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Replace the game repo's games list so one game has isInPlayLater=true
        val gamesWithPlayLater = harness.gameRepo.games.toMutableList()
        val updated = gamesWithPlayLater[0].copy(isInPlayLater = true)
        gamesWithPlayLater[0] = updated
        harness.gameRepo.games = gamesWithPlayLater

        setContent { harness.App() }
        advance(harness)

        // Play Later section exists in the tree (may need scrolling to be visible)
        onNodeWithText("Play Later").assertExists()
    }

    // ---- Game detail: Play Later toggle button ----

    @Test
    fun gameDetailShowsAddToPlayLaterButton() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Game is not in Play Later, so the button should say "Add to Play Later"
        onNodeWithContentDescription("Add to Play Later").assertIsDisplayed()
    }

    @Test
    fun gameDetailShowsRemoveFromPlayLaterWhenInQueue() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Set the game as already in Play Later queue
        val gamesWithPlayLater = harness.gameRepo.games.toMutableList()
        val updated = gamesWithPlayLater[0].copy(isInPlayLater = true)
        gamesWithPlayLater[0] = updated
        harness.gameRepo.games = gamesWithPlayLater

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Game is in Play Later, so the button should say "Remove from Play Later"
        onNodeWithContentDescription("Remove from Play Later").assertIsDisplayed()
    }

    @Test
    fun gameDetailTogglePlayLaterChangesButtonState() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Initially "Add to Play Later"
        onNodeWithContentDescription("Add to Play Later").assertIsDisplayed()

        // Click the toggle button
        onNodeWithContentDescription("Add to Play Later").performClick()
        advanceQuick(harness)

        // After toggling, should show "Remove from Play Later"
        onNodeWithContentDescription("Remove from Play Later").assertIsDisplayed()
    }

    // ---- Activity feed: queued_play_later event ----

    @Test
    fun activityFeedShowsPlayLaterEvent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.socialRepo.activityEvents = listOf(
            ActivityEvent(
                "e1", "1", "alice", null,
                "queued_play_later", "g1", "Castlevania", null, "NES",
                "2026-01-01T00:00:00Z",
            ),
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Recent Activity").assertIsDisplayed()
        onNodeWithContentDescription("alice added Castlevania to Play Later queue")
            .assertIsDisplayed()
    }
}
