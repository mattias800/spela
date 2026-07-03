package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ChallengeDifficulty
import com.spela.player.domain.model.ChallengeType
import com.spela.player.domain.model.UserStats
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.state.ChallengeTab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for Phase 7: Global Challenges Browse + Personal Stats on Stats Page.
 * Tests: Global challenges screen tabs, filters, challenge grid, personal stats section on Stats.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GlobalChallengesTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // --- Global Challenges Screen ---

    @Test
    fun globalChallengesScreenShowsTabs() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GlobalChallenges)
        )
        advance(harness)

        // Top bar title
        onNodeWithText("Challenges").assertIsDisplayed()

        // Tabs
        onNodeWithText("Popular").assertIsDisplayed()
        onNodeWithText("Recent").assertIsDisplayed()
        onNodeWithText("My Challenges").assertIsDisplayed()
    }

    @Test
    fun tabSwitchingWorks() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GlobalChallenges)
        )
        advance(harness)

        // Default tab is Popular
        assertEquals(ChallengeTab.POPULAR, harness.challengeListViewModel.state.value.selectedTab)

        // Switch to Recent tab
        onNodeWithText("Recent").performClick()
        advanceQuick(harness)
        assertEquals(ChallengeTab.RECENT, harness.challengeListViewModel.state.value.selectedTab)

        // Switch to My Challenges tab
        onNodeWithText("My Challenges").performClick()
        advanceQuick(harness)
        assertEquals(ChallengeTab.MINE, harness.challengeListViewModel.state.value.selectedTab)
    }

    @Test
    fun consoleFilterDropdownWorks() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GlobalChallenges)
        )
        advance(harness)

        // Console filter should show "All Consoles" initially
        onNodeWithContentDescription("Console: All Consoles").assertIsDisplayed()

        // Open dropdown and select NES
        onNodeWithContentDescription("Console: All Consoles").performClick()
        advanceQuick(harness)
        onNodeWithText("Nintendo Entertainment System").performClick()
        advanceQuick(harness)

        // Verify filter applied in ViewModel state
        assertEquals("nes", harness.challengeListViewModel.state.value.selectedConsoleFilter)
    }

    @Test
    fun difficultyFilterDropdownWorks() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GlobalChallenges)
        )
        advance(harness)

        // Difficulty filter should show "All Difficulties" initially
        onNodeWithContentDescription("Difficulty: All Difficulties").assertIsDisplayed()

        // Open dropdown and select Hard
        onNodeWithContentDescription("Difficulty: All Difficulties").performClick()
        advanceQuick(harness)
        onNodeWithText("Hard").performClick()
        advanceQuick(harness)

        // Verify filter applied
        assertEquals("hard", harness.challengeListViewModel.state.value.selectedDifficultyFilter)
    }

    @Test
    fun challengeCardsDisplayInGrid() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(
            id = "gc1",
            gameId = "1",
            name = "Beat Dracula",
            type = ChallengeType.SPEEDRUN,
            difficulty = ChallengeDifficulty.HARD,
            attemptCount = 10,
        )
        harness.challengeRepo.preAddChallenge(
            id = "gc2",
            gameId = "1",
            name = "Perfect Clear",
            type = ChallengeType.COMPLETION,
            difficulty = ChallengeDifficulty.MEDIUM,
            attemptCount = 5,
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GlobalChallenges)
        )
        advance(harness)

        // Challenge cards should be visible
        onNodeWithText("Beat Dracula").assertExists()
        onNodeWithText("Perfect Clear").assertExists()
    }

    @Test
    fun emptyChallengesShowsEmptyState() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // No challenges pre-loaded

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GlobalChallenges)
        )
        advance(harness)

        // Empty state message
        onNodeWithText("No challenges yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun myChallengesTabHidesFilters() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GlobalChallenges)
        )
        advance(harness)

        // Filters visible on Popular tab
        onNodeWithContentDescription("Console: All Consoles").assertIsDisplayed()

        // Switch to My Challenges
        onNodeWithText("My Challenges").performClick()
        advanceQuick(harness)

        // Filters should be hidden on My Challenges tab
        onNodeWithContentDescription("Console: All Consoles").assertDoesNotExist()
    }

    // --- Personal Stats on Stats Screen ---

    @Test
    fun statsScreenShowsPersonalStatsSection() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameStatsRepo.userStats = UserStats(
            totalPlayTime = 10800, // 3h 0m
            gamesPlayed = 25,
            currentStreak = 5,
            longestStreak = 12,
            mostPlayedGame = null,
            mostPlayedGameTime = 0,
            lastPlayedAt = null,
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Stats)
        )
        advance(harness)

        // Stats screen top bar
        onNodeWithText("Stats").assertIsDisplayed()

        // Personal stats section header
        onNodeWithText("Your Stats").assertIsDisplayed()

        // Stat cards
        onNodeWithContentDescription("Play Time: 3h 0m").assertExists()
        onNodeWithContentDescription("Games Played: 25").assertExists()
        onNodeWithContentDescription("Current Streak: 5 days").assertExists()
        onNodeWithContentDescription("Longest Streak: 12 days").assertExists()
    }

    @Test
    fun statsScreenShowsZeroStatsWhenNoPlayHistory() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Default userStats has all zeros — section still renders with zero values

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Stats)
        )
        advance(harness)

        // Stats screen renders and personal stats section shows zero values
        onNodeWithText("Stats").assertIsDisplayed()
        onNodeWithText("Your Stats").assertIsDisplayed()
        onNodeWithContentDescription("Play Time: < 1 min").assertExists()
        onNodeWithContentDescription("Games Played: 0").assertExists()
        onNodeWithContentDescription("Current Streak: 0 days").assertExists()
    }

    @Test
    fun statsScreenShowsMostPlayedGame() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val castlevania = harness.gameRepo.games.first { it.id == "1" }
        harness.gameStatsRepo.userStats = UserStats(
            totalPlayTime = 7200,
            gamesPlayed = 10,
            currentStreak = 2,
            longestStreak = 5,
            mostPlayedGame = castlevania,
            mostPlayedGameTime = 5400, // 1h 30m
            lastPlayedAt = "2026-02-18T10:00:00Z",
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Stats)
        )
        advance(harness)

        // Most played game card
        onNodeWithContentDescription("Most played game: Castlevania, 1h 30m").assertExists()
        onNodeWithText("Most Played").assertExists()
        onNodeWithText("Castlevania").assertIsDisplayed()
    }
}
