package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ActiveNowItem
import com.spela.player.domain.model.CommunityTopGame
import com.spela.player.domain.model.CultClassicGame
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.RecentReviewItem
import com.spela.player.domain.model.TrendingGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Desktop E2E tests for Phase 10: Social & Community Discovery.
 *
 * Covers:
 * - Trending shelf renders on Explore screen
 * - Community Favorites shelf renders
 * - Cult Classics shelf renders
 * - Active Now shelf renders
 * - Recently Reviewed shelf renders
 * - Empty social data hides sections
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreSocialTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun makeGame(id: String, title: String): Game = Game(
        id = id,
        title = title,
        consoleId = "snes",
        consoleName = "Super Nintendo",
        fileName = "$title.sfc",
        fileSize = 1024,
        discCount = 1,
        description = "",
        coverUrl = null,
        developer = "",
        publisher = "",
        releaseDate = "",
        genre = "Action",
        players = 1,
        rating = 80.0,
        isFavorite = false,
        isInPlayLater = false,
        coverAspectRatio = 0.75f,
    )

    @Test
    fun trendingSectionRendersWithGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.trendingGames = listOf(
            TrendingGame(game = makeGame("t1", "Trending Alpha"), playersThisWeek = 5),
            TrendingGame(game = makeGame("t2", "Trending Beta"), playersThisWeek = 3),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_trending_section").assertExists()
        onNodeWithTag("trending_row").assertExists()
        onNodeWithTag("trending_players_t1").assertExists()
    }

    @Test
    fun communityTopSectionRenders() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.communityTopGames = listOf(
            CommunityTopGame(game = makeGame("c1", "Community Best"), avgRating = 4.8, ratingCount = 10),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_community_top_section").assertExists()
        onNodeWithTag("community_top_row").assertExists()
    }

    @Test
    fun cultClassicsSectionRenders() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.cultClassicsList = listOf(
            CultClassicGame(
                game = makeGame("cu1", "Hidden Treasure"),
                communityRating = 4.5,
                igdbRating = 55.0,
                ratingCount = 3,
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_cult_classics_section").assertExists()
        onNodeWithTag("cult_classics_row").assertExists()
    }

    @Test
    fun activeNowSectionRenders() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.activeNowGames = listOf(
            ActiveNowItem(game = makeGame("a1", "Active Game"), activeSessions = 2, activeChallenges = 1),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_active_now_section").assertExists()
        onNodeWithTag("active_now_row").assertExists()
        onNodeWithTag("active_badge_a1").assertExists()
    }

    @Test
    fun recentlyReviewedSectionRenders() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.recentReviews = listOf(
            RecentReviewItem(
                game = makeGame("r1", "Reviewed RPG"),
                rating = 5,
                review = "Amazing classic!",
                reviewerName = "player1",
                reviewedAt = "2026-03-01T10:00:00Z",
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_recently_reviewed_section").assertExists()
        onNodeWithTag("recently_reviewed_row").assertExists()
        onNodeWithTag("review_text_r1").assertExists()
    }

    @Test
    fun emptySocialDataHidesSections() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_trending_section").assertDoesNotExist()
        onNodeWithTag("explore_community_top_section").assertDoesNotExist()
        onNodeWithTag("explore_cult_classics_section").assertDoesNotExist()
        onNodeWithTag("explore_active_now_section").assertDoesNotExist()
        onNodeWithTag("explore_recently_reviewed_section").assertDoesNotExist()
    }
}
