package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Challenge
import com.spela.player.domain.model.ChallengeDifficulty
import com.spela.player.domain.model.ChallengeType
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.RecentAchievement
import com.spela.player.domain.model.UserStats
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for Phase 6: Dashboard Widgets.
 * Tests: Personal stats card, recent achievements row, trending challenges row.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class DashboardWidgetsTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // --- Personal Stats Card ---

    @Test
    fun personalStatsCardShowsWhenStatsAvailable() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameStatsRepo.userStats = UserStats(
            totalPlayTime = 7200, // 2h 0m
            gamesPlayed = 15,
            currentStreak = 3,
            longestStreak = 7,
            mostPlayedGame = null,
            mostPlayedGameTime = 0,
            lastPlayedAt = null,
        )

        setContent { harness.App() }
        advance(harness)

        // Section header
        onNodeWithText("Your Stats").assertIsDisplayed()

        // Stat cards with contentDescription: "label: value"
        onNodeWithContentDescription("Play Time: 2h 0m").assertExists()
        onNodeWithContentDescription("Games Played: 15").assertExists()
        onNodeWithContentDescription("Streak: 3 days").assertExists()
        onNodeWithContentDescription("Best Streak: 7 days").assertExists()
    }

    @Test
    fun personalStatsHiddenWhenLibraryEmpty() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Empty game list triggers the EmptyLibrary state, hiding all sections
        harness.gameRepo.games = emptyList()

        setContent { harness.App() }
        advance(harness)

        // When library is empty, the LazyColumn is replaced by EmptyLibrary
        // and the "Your Stats" section should not appear
        onNodeWithText("Your Stats").assertDoesNotExist()
    }

    @Test
    fun personalStatsSeeAllLinkExists() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameStatsRepo.userStats = UserStats(
            totalPlayTime = 3600,
            gamesPlayed = 5,
            currentStreak = 1,
            longestStreak = 1,
            mostPlayedGame = null,
            mostPlayedGameTime = 0,
            lastPlayedAt = null,
        )

        setContent { harness.App() }
        advance(harness)

        // "See all" link for stats
        onNodeWithContentDescription("See all Your Stats").assertIsDisplayed()
    }

    // --- Recent Achievements Row ---

    @Test
    fun recentAchievementsShowsAchievementCards() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameStatsRepo.recentAchievements = listOf(
            RecentAchievement(
                achievementRaId = 1001,
                title = "Dragon Slayer",
                description = "Defeat the dragon boss",
                points = 25,
                badgeUrl = null,
                unlockedAt = "2026-02-18T10:00:00Z",
                isHardcore = false,
                playTimeAtUnlock = null,
                gameId = "1",
                gameTitle = "Castlevania",
                consoleName = "NES",
                coverUrl = null,
            ),
            RecentAchievement(
                achievementRaId = 1002,
                title = "Speed Runner",
                description = "Complete the game in under 30 minutes",
                points = 50,
                badgeUrl = null,
                unlockedAt = "2026-02-17T08:00:00Z",
                isHardcore = false,
                playTimeAtUnlock = null,
                gameId = "2",
                gameTitle = "Super Mario Bros.",
                consoleName = "NES",
                coverUrl = null,
            ),
        )

        setContent { harness.App() }
        advance(harness)

        // Section header
        onNodeWithText("Recent Achievements").assertIsDisplayed()

        // Achievement cards with contentDescription: "title, points, from gameTitle"
        onNodeWithContentDescription("Dragon Slayer, 25 points, from Castlevania")
            .assertExists()
        onNodeWithContentDescription("Speed Runner, 50 points, from Super Mario Bros.")
            .assertExists()

        // Points text visible
        onNodeWithText("25 pts").assertExists()
        onNodeWithText("50 pts").assertExists()
    }

    @Test
    fun recentAchievementsHiddenWhenEmpty() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Default recentAchievements is empty

        setContent { harness.App() }
        advance(harness)

        // Section should not appear
        onNodeWithText("Recent Achievements").assertDoesNotExist()
    }

    @Test
    fun recentAchievementsSeeAllLinkExists() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameStatsRepo.recentAchievements = listOf(
            RecentAchievement(
                achievementRaId = 1001,
                title = "First Win",
                description = "Win once",
                points = 10,
                badgeUrl = null,
                unlockedAt = "2026-02-18T10:00:00Z",
                isHardcore = false,
                playTimeAtUnlock = null,
                gameId = "1",
                gameTitle = "Castlevania",
                consoleName = "NES",
                coverUrl = null,
            ),
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithContentDescription("See all Recent Achievements").assertIsDisplayed()
    }

    // --- Trending Challenges Row ---

    @Test
    fun trendingChallengesShowsChallengeCards() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(
            id = "tc1",
            gameId = "1",
            name = "Beat Dracula Fast",
            type = ChallengeType.SPEEDRUN,
            difficulty = ChallengeDifficulty.HARD,
            attemptCount = 20,
            completionCount = 8,
        )
        harness.challengeRepo.preAddChallenge(
            id = "tc2",
            gameId = "2",
            name = "No Death Run",
            type = ChallengeType.COMPLETION,
            difficulty = ChallengeDifficulty.MEDIUM,
            attemptCount = 15,
            completionCount = 5,
        )

        setContent { harness.App() }
        advance(harness)

        // Section header
        onNodeWithText("Trending Challenges").assertIsDisplayed()

        // Challenge names visible
        onNodeWithText("Beat Dracula Fast").assertExists()
        onNodeWithText("No Death Run").assertExists()
    }

    @Test
    fun trendingChallengesHiddenWhenEmpty() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Default challenges is empty

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Trending Challenges").assertDoesNotExist()
    }

    @Test
    fun trendingChallengesSeeAllLinkExists() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(
            id = "tc1",
            gameId = "1",
            name = "Boss Rush",
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithContentDescription("See all Trending Challenges").assertIsDisplayed()
    }
}
