package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.GameRating
import com.spela.player.domain.model.GameStats
import com.spela.player.domain.model.RatingSummary
import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.TopPlayer
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for Game Detail community sections:
 * - Community Stats (Play Activity)
 * - Reviews Feed
 * - Active Shared Sessions
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GameDetailCommunitySectionsTest {

    private fun createHarnessOnGameDetail(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToGameDetail(harness: SpelaTestHarness) =
        navigateToGameDetail(harness, "1")

    /**
     * Scroll the game detail LazyColumn until the node matching [matcher] is visible.
     * LazyColumn only composes items near the viewport; this uses performScrollToNode
     * on the scrollable container to bring off-screen lazy items into composition.
     */
    private fun ComposeUiTest.scrollToSection(matcher: SemanticsMatcher) {
        onNodeWithTag("game_detail_content").performScrollToNode(matcher)
    }

    // --- Community Stats Section ---

    @Test
    fun communityStatsShowsWhenDataAvailable() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameStatsRepo.gameStats = GameStats(
            totalPlayers = 42,
            totalPlayTime = 360000,
            averagePlayTime = 8571,
            topPlayers = listOf(
                TopPlayer("u1", "Alice", null, 120000),
                TopPlayer("u2", "Bob", null, 90000),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Confirm we're on the game detail page
        onNodeWithText("Castlevania").assertIsDisplayed()

        // Scroll to the community stats section
        scrollToSection(hasTestTag("community_stats_section"))
        onNodeWithText("Play Activity").assertIsDisplayed()
        onNodeWithText("42").assertExists()
        onNodeWithText("players").assertExists()
    }

    @Test
    fun communityStatsHiddenWhenNoPlayers() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        // gameStats defaults to 0 players — section should not render

        setContent { harness.App() }
        navigateToGameDetail(harness)

        onNodeWithTag("community_stats_section").assertDoesNotExist()
    }

    @Test
    fun clickingTopPlayerNavigatesToUserProfile() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameStatsRepo.gameStats = GameStats(
            totalPlayers = 5,
            totalPlayTime = 100000,
            averagePlayTime = 20000,
            topPlayers = listOf(
                TopPlayer("user-42", "SpeedKing", null, 50000),
                TopPlayer("user-43", "CasualGamer", null, 30000),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("community_stats_section"))
        onNodeWithText("SpeedKing").performClick()
        advance(harness)

        // UserProfile screen should be shown — assert by the screen's
        // root testTag so we don't depend on whether the fake repo
        // returns a profile or a fallback.
        onNodeWithTag("user_profile_screen").assertIsDisplayed()
    }

    @Test
    fun communityStatsShowsTopPlayers() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameStatsRepo.gameStats = GameStats(
            totalPlayers = 10,
            totalPlayTime = 50000,
            averagePlayTime = 5000,
            topPlayers = listOf(
                TopPlayer("u1", "SpeedRunner", null, 25000),
                TopPlayer("u2", "CasualGamer", null, 15000),
                TopPlayer("u3", "Newbie", null, 10000),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("community_stats_section"))
        onNodeWithText("TOP PLAYERS").assertIsDisplayed()
        onNodeWithText("SpeedRunner").assertIsDisplayed()
        onNodeWithText("CasualGamer").assertIsDisplayed()
        onNodeWithText("Newbie").assertIsDisplayed()
    }

    // --- Reviews Section ---

    @Test
    fun reviewsSectionShowsEmptyState() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("reviews_section"))
        onNodeWithText("Reviews").assertIsDisplayed()
        onNodeWithTag("reviews_empty").assertIsDisplayed()
    }

    @Test
    fun reviewsSectionShowsReviews() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.ratingRepo.gameRatings = listOf(
            GameRating("r1", "u1", "Alice", null, "1", 5, "Amazing game!", "2026-01-15T10:00:00Z"),
            GameRating("r2", "u2", "Bob", null, "1", 3, "It's okay", "2026-01-14T08:00:00Z"),
        )
        harness.ratingRepo.ratingSummary = RatingSummary(4.0, 2, emptyMap())

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("reviews_section"))
        onNodeWithText("Reviews").assertIsDisplayed()
        onNodeWithText("Alice").assertIsDisplayed()
        onNodeWithText("Amazing game!").assertIsDisplayed()
        onNodeWithText("Bob").assertIsDisplayed()
        onNodeWithText("It's okay").assertIsDisplayed()
    }

    @Test
    fun reviewsSectionHidesLoadMoreWhenAllLoaded() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.ratingRepo.gameRatings = listOf(
            GameRating("r1", "u1", "Alice", null, "1", 4, "Great!", "2026-01-15T10:00:00Z"),
        )
        harness.ratingRepo.ratingSummary = RatingSummary(4.0, 1, emptyMap())

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("reviews_section"))
        onNodeWithTag("load_more_reviews").assertDoesNotExist()
    }

    // --- Active Shared Sessions Section ---

    @Test
    fun sharedSessionsSectionHiddenWhenNoSharedSessions() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        // gameSharedSessions defaults to empty — section should not render

        setContent { harness.App() }
        navigateToGameDetail(harness)

        onNodeWithTag("game_shared_sessions_section").assertDoesNotExist()
    }

    @Test
    fun sharedSessionsSectionShowsSharedSessions() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.sharedSessionRepo.gameSharedSessions = listOf(
            SharedSession(
                id = "ss1",
                name = "Speed Run Session",
                gameId = "1",
                ownerId = "u1",
                ownerUsername = "Alice",
                status = "active",
                memberCount = 3,
            ),
            SharedSession(
                id = "ss2",
                name = "Casual Play",
                gameId = "1",
                ownerId = "u2",
                ownerUsername = "Bob",
                status = "paused",
                memberCount = 2,
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("game_shared_sessions_section"))
        onNodeWithText("Active Shared Sessions").assertIsDisplayed()
        onNodeWithText("Speed Run Session").assertIsDisplayed()
        onNodeWithText("Alice").assertIsDisplayed()
        onNodeWithText("3 members").assertIsDisplayed()
        onNodeWithText("Active").assertIsDisplayed()
        onNodeWithText("Casual Play").assertIsDisplayed()
        onNodeWithText("Paused").assertIsDisplayed()
    }

    @Test
    fun sharedSessionItemIsClickable() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.sharedSessionRepo.gameSharedSessions = listOf(
            SharedSession(
                id = "ss1",
                name = "Test Session",
                gameId = "1",
                ownerId = "u1",
                ownerUsername = "Alice",
                status = "active",
                memberCount = 2,
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("game_shared_sessions_section"))
        onNodeWithTag("shared_session_item_ss1").assertIsDisplayed()
        onNodeWithTag("shared_session_item_ss1").assertHasClickAction()
    }

}
