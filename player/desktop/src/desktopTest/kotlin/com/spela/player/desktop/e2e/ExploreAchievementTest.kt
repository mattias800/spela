package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.AchievementGameItem
import com.spela.player.domain.model.AlmostDoneGame
import com.spela.player.domain.model.ExploreChallenge
import com.spela.player.domain.model.FreshChallengeGame
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Desktop E2E tests for Phase 12: Achievement & Challenge-Driven Discovery.
 *
 * Covers:
 * - Easy to Complete section renders
 * - Almost Done section renders
 * - Empty achievement data hides sections
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreAchievementTest {

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
        releaseDate = null,
        genre = "Action",
        players = 1,
        igdbCriticsRating = 80.0,
        isFavorite = false,
        isInPlayLater = false,
        coverAspectRatio = 0.75f,
    )

    @Test
    fun achievementSectionRendersEasyToComplete() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.easyToCompleteGames = listOf(
            AchievementGameItem(
                game = makeGame("easy1", "Easy Game Alpha"),
                totalAchievements = 20,
                avgCompletion = 85.0f,
                playersAttempted = 50,
                playersCompleted = 42,
            ),
            AchievementGameItem(
                game = makeGame("easy2", "Easy Game Beta"),
                totalAchievements = 15,
                avgCompletion = 90.0f,
                playersAttempted = 30,
                playersCompleted = 27,
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_easy_to_complete_section").assertExists()
        onNodeWithTag("easy_to_complete_row").assertExists()
        onNodeWithTag("easy_completion_easy1").assertExists()
    }

    @Test
    fun achievementSectionRendersAlmostDone() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.almostDoneGamesList = listOf(
            AlmostDoneGame(
                game = makeGame("ad1", "Almost Done RPG"),
                unlockedCount = 18,
                totalCount = 20,
                completionPercent = 90.0f,
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_almost_done_section").assertExists()
        onNodeWithTag("almost_done_row").assertExists()
        onNodeWithTag("almost_done_stat_ad1").assertExists()
    }

    @Test
    fun emptyAchievementDataHidesSections() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_easy_to_complete_section").assertDoesNotExist()
        onNodeWithTag("explore_hardest_games_section").assertDoesNotExist()
        onNodeWithTag("explore_almost_done_section").assertDoesNotExist()
        onNodeWithTag("explore_fresh_challenges_section").assertDoesNotExist()
        onNodeWithTag("explore_active_challenges_section").assertDoesNotExist()
    }
}
