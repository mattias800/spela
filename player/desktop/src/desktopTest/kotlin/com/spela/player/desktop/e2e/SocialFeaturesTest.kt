package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.OnlineUser
import com.spela.player.domain.model.OnlineUserGame
import com.spela.player.domain.model.RatingSummary
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for social features: online users, activity feed,
 * star ratings, and community saves.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SocialFeaturesTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // ---- Home screen: Online Users ----

    @Test
    fun homeScreenShowsOnlineUsersSection() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.socialRepo.onlineUsers = listOf(
            OnlineUser("1", "alice", null, null),
            OnlineUser("2", "bob", null, OnlineUserGame("g1", "Zelda", null, "SNES")),
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Online Now").assertIsDisplayed()
        onNodeWithContentDescription("alice online").assertIsDisplayed()
        onNodeWithContentDescription("bob playing Zelda").assertIsDisplayed()
    }

    @Test
    fun homeScreenShowsUsernames() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.socialRepo.onlineUsers = listOf(
            OnlineUser("1", "alice", null, null),
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("alice").assertIsDisplayed()
    }

    // ---- Home screen: Activity Feed ----

    @Test
    fun homeScreenShowsActivityFeed() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.socialRepo.activityEvents = listOf(
            ActivityEvent(
                "e1", "1", "alice", null,
                "started_playing", "g1", "Mario", null, "NES",
                "2026-01-01T00:00:00Z",
            ),
            ActivityEvent(
                "e2", "2", "bob", null,
                "favorited_game", "g2", "Zelda", null, "SNES",
                "2026-01-01T01:00:00Z",
            ),
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Recent Activity").assertIsDisplayed()
        onNodeWithContentDescription("alice started playing Mario").assertIsDisplayed()
        onNodeWithContentDescription("bob favorited Zelda").assertIsDisplayed()
    }

    // ---- Game detail: Star Rating ----

    @Test
    fun gameDetailShowsStarRating() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        onNodeWithText("Your Rating").assertIsDisplayed()
        onNodeWithContentDescription("Tap to rate this game").assertIsDisplayed()
    }

    @Test
    fun gameDetailShowsAverageRatingWhenPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.ratingRepo.ratingSummary = RatingSummary(4.2, 15, emptyMap())

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        onNodeWithText("Your Rating").assertIsDisplayed()
        onNodeWithText("4.2").assertIsDisplayed()
        onNodeWithText("(15)").assertIsDisplayed()
    }

    // ---- Game detail: Community Saves ----

    @Test
    fun gameDetailShowsCommunitySavesSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Community Saves is deep in the LazyColumn — scroll to bring it into composition
        onNodeWithTag("game_detail_content").performScrollToNode(hasText("Community Saves"))
        onNodeWithText("Community Saves").assertIsDisplayed()
        onNodeWithText("No community saves yet. Be the first to share!").assertExists()
    }

    @Test
    fun gameDetailShowsSharedSavesWhenPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSaveRepo.sharedSaves = listOf(
            SharedSaveState(
                "s1", "2", "bob", null, "1",
                "Boss Rush Save", "Right before the final boss",
                2048, 5, "2026-01-15T12:00:00Z",
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Community Saves is deep in the LazyColumn — scroll to bring it into composition
        onNodeWithTag("game_detail_content").performScrollToNode(hasText("Community Saves"))
        onNodeWithText("Community Saves").assertIsDisplayed()
        // The empty state text should NOT be present since we have saves
        onNodeWithText("No community saves yet. Be the first to share!").assertDoesNotExist()
        onNodeWithContentDescription("Boss Rush Save by bob").assertExists()
    }
}
