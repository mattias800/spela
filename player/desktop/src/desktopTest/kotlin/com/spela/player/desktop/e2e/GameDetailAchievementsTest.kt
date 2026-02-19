package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.AchievementPlayerRanking
import com.spela.player.domain.model.AchievementProgress
import com.spela.player.domain.model.AchievementTimelineData
import com.spela.player.domain.model.AchievementTimelineEntry
import com.spela.player.domain.model.GameAchievement
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.state.AchievementsViewMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for Game Detail achievements section:
 * - Achievement grid with progress
 * - View mode toggle (Grid, Timeline, Leaderboard)
 * - Timeline view with entries
 * - Leaderboard view with rankings
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GameDetailAchievementsTest {

    private fun createHarnessWithAchievements(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))

        harness.gameStatsRepo.apply {
            // Set up achievements via the repository (these will be returned
            // when the ViewModel calls getGameAchievements/getAchievementProgress)
        }

        return harness
    }

    private fun ComposeUiTest.navigateToGameDetail(harness: SpelaTestHarness) =
        navigateToGameDetail(harness, "1")

    private fun ComposeUiTest.scrollToSection(matcher: SemanticsMatcher) {
        onNodeWithTag("game_detail_content").performScrollToNode(matcher)
    }

    // --- Achievement Grid ---

    @Test
    fun achievementsSectionHiddenWhenNoAchievements() = runComposeUiTest {
        val harness = createHarnessWithAchievements()
        // Default: empty achievements list

        setContent { harness.App() }
        navigateToGameDetail(harness)

        onNodeWithTag("achievements_section").assertDoesNotExist()
    }

    @Test
    fun achievementsSectionShowsGrid() = runComposeUiTest {
        val harness = createHarnessWithAchievements()
        setUpAchievementData(harness)

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("achievements_section"))
        onNodeWithText("Achievements").assertIsDisplayed()
        onNodeWithTag("achievements_grid").assertIsDisplayed()
    }

    @Test
    fun achievementsShowsProgressBar() = runComposeUiTest {
        val harness = createHarnessWithAchievements()
        setUpAchievementData(harness)

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("achievements_section"))
        // Progress label: "2 of 4 unlocked (30/100 pts)"
        onNodeWithText("2 of 4 unlocked", substring = true).assertIsDisplayed()
    }

    @Test
    fun achievementsShowsCardTitles() = runComposeUiTest {
        val harness = createHarnessWithAchievements()
        setUpAchievementData(harness)

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("achievements_section"))
        onNodeWithText("First Blood").assertIsDisplayed()
        onNodeWithText("Speed Run").assertIsDisplayed()
    }

    @Test
    fun achievementsShowsCompleteBadge() = runComposeUiTest {
        val harness = createHarnessWithAchievements()
        // All achievements unlocked
        setUpAllUnlockedData(harness)

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("achievements_section"))
        onNodeWithTag("achievements_complete_badge").assertIsDisplayed()
        onNodeWithText("100% Complete!").assertIsDisplayed()
    }

    // --- View Mode Toggle ---

    @Test
    fun viewModeToggleIsDisplayed() = runComposeUiTest {
        val harness = createHarnessWithAchievements()
        setUpAchievementData(harness)

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("achievements_section"))
        onNodeWithTag("achievements_view_toggle").assertIsDisplayed()
        onNodeWithTag("achievements_tab_grid").assertIsDisplayed()
        onNodeWithTag("achievements_tab_timeline").assertIsDisplayed()
        onNodeWithTag("achievements_tab_leaderboard").assertIsDisplayed()
    }

    @Test
    fun switchToTimelineView() = runComposeUiTest {
        val harness = createHarnessWithAchievements()
        setUpAchievementData(harness)
        setUpTimelineData(harness)

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("achievements_section"))

        // Switch to timeline
        onNodeWithTag("achievements_tab_timeline").performClick()
        advance(harness)
        advance(harness)

        scrollToSection(hasTestTag("achievements_timeline"))
        onNodeWithTag("achievements_timeline").assertIsDisplayed()
    }

    @Test
    fun switchToLeaderboardView() = runComposeUiTest {
        val harness = createHarnessWithAchievements()
        setUpAchievementData(harness)
        setUpLeaderboardData(harness)

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("achievements_section"))

        // Switch to leaderboard
        onNodeWithTag("achievements_tab_leaderboard").performClick()
        advance(harness)
        advance(harness)

        scrollToSection(hasTestTag("achievements_leaderboard"))
        onNodeWithTag("achievements_leaderboard").assertIsDisplayed()
        onNodeWithText("SpeedMaster").assertIsDisplayed()
        onNodeWithText("CasualPro").assertIsDisplayed()
    }

    @Test
    fun leaderboardShowsRankAndPoints() = runComposeUiTest {
        val harness = createHarnessWithAchievements()
        setUpAchievementData(harness)
        setUpLeaderboardData(harness)

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("achievements_section"))

        onNodeWithTag("achievements_tab_leaderboard").performClick()
        advance(harness)
        advance(harness)

        scrollToSection(hasTestTag("achievements_leaderboard"))
        onNodeWithText("#1").assertIsDisplayed()
        onNodeWithText("#2").assertIsDisplayed()
        onNodeWithText("80 pts").assertIsDisplayed()
        onNodeWithText("50 pts").assertIsDisplayed()
    }

    // --- Test Data Helpers ---

    private val testAchievements = listOf(
        GameAchievement(1, "First Blood", "Defeat the first enemy", 10, null, null, 1),
        GameAchievement(2, "Speed Run", "Complete level 1 in under 60 seconds", 20, null, null, 2),
        GameAchievement(3, "Collector", "Find all hidden items", 30, null, null, 3),
        GameAchievement(4, "Boss Slayer", "Defeat all bosses", 40, null, null, 4),
    )

    private val testProgress = listOf(
        AchievementProgress(1, "2026-01-10T10:00:00Z", false, 300),
        AchievementProgress(2, "2026-01-12T15:30:00Z", false, 900),
        AchievementProgress(3, null, false, null),
        AchievementProgress(4, null, false, null),
    )

    private fun setUpAchievementData(harness: SpelaTestHarness) {
        // Override the getGameAchievements and getAchievementProgress calls
        // by setting fields on the FakeGameStatsRepository
        harness.gameStatsRepo.achievements = testAchievements
        harness.gameStatsRepo.achievementProgress = testProgress
    }

    private fun setUpAllUnlockedData(harness: SpelaTestHarness) {
        harness.gameStatsRepo.achievements = testAchievements
        harness.gameStatsRepo.achievementProgress = listOf(
            AchievementProgress(1, "2026-01-10T10:00:00Z", false, 300),
            AchievementProgress(2, "2026-01-12T15:30:00Z", false, 900),
            AchievementProgress(3, "2026-01-14T09:00:00Z", false, 1500),
            AchievementProgress(4, "2026-01-15T20:00:00Z", false, 2000),
        )
    }

    private fun setUpTimelineData(harness: SpelaTestHarness) {
        harness.gameStatsRepo.achievementTimeline = AchievementTimelineData(
            raGameId = 1234,
            gameTitle = "Castlevania",
            totalPlayTime = 3600,
            timeline = listOf(
                AchievementTimelineEntry(1, "First Blood", "Defeat the first enemy", 10, null, "2026-01-10T10:00:00Z", false, 300),
                AchievementTimelineEntry(2, "Speed Run", "Complete level 1 in under 60 seconds", 20, null, "2026-01-12T15:30:00Z", false, 900),
            ),
            totalAchievements = 4,
            unlockedCount = 2,
            totalPoints = 100,
            earnedPoints = 30,
        )
    }

    private fun setUpLeaderboardData(harness: SpelaTestHarness) {
        harness.gameStatsRepo.achievementLeaderboard = listOf(
            AchievementPlayerRanking("u1", "SpeedMaster", null, 4, 80, "2026-01-10T10:00:00Z", "2026-01-15T20:00:00Z"),
            AchievementPlayerRanking("u2", "CasualPro", null, 2, 50, "2026-01-11T08:00:00Z", "2026-01-13T12:00:00Z"),
        )
    }
}
