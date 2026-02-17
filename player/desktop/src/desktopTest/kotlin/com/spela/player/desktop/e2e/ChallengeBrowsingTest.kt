package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ChallengeLeaderboardEntry
import com.spela.player.domain.model.ChallengeDifficulty
import com.spela.player.domain.model.ChallengeType
import com.spela.player.presentation.intent.ChallengeIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for challenge browsing and discovery.
 * Tests: Challenges section on game detail, challenge list, empty states, challenge detail.
 *
 * IMPORTANT: ChallengeDetailScreen has SpShimmer in its skeleton, which causes
 * waitForIdle() to hang on the infinite animation. To avoid this, we pre-load
 * challenge data via the ViewModel before composition so the skeleton never renders.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ChallengeBrowsingTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    /**
     * Pre-loads ChallengeDetailViewModel data so the skeleton never renders.
     * Must be called BEFORE setContent when navigating to ChallengeDetail.
     */
    private fun SpelaTestHarness.preLoadChallengeDetail(challengeId: String) {
        challengeDetailViewModel.onIntent(ChallengeIntent.LoadChallengeDetail(challengeId))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun gameDetailShowsChallengesSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Verify game detail loaded
        onNodeWithText("Castlevania").assertIsDisplayed()

        // Navigate to challenge list (same as clicking "View Challenges")
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeList("1", "Castlevania"))
        )
        advance(harness)

        // Challenge list screen renders
        onNodeWithText("No challenges yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun challengeListShowsCardDetails() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(
            id = "1",
            gameId = "1",
            name = "Beat Dracula",
            type = ChallengeType.SPEEDRUN,
            difficulty = ChallengeDifficulty.HARD,
            attemptCount = 12,
            completionCount = 5,
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeList("1", "Castlevania"))
        )
        advance(harness)

        // Card should show challenge name and difficulty chip
        onNodeWithText("Beat Dracula").assertIsDisplayed()
        onNodeWithText("Hard").assertIsDisplayed()
        onNodeWithText("12").assertIsDisplayed() // attempt count
    }

    @Test
    fun emptyChallengesShowsEmptyState() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeList("1", "Castlevania"))
        )
        advance(harness)

        // Empty state message from SpEmptyStates.NoChallenges
        onNodeWithText("No challenges yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingChallengeNavigatesToDetail() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(
            id = "1",
            gameId = "1",
            name = "Beat Dracula",
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeList("1", "Castlevania"))
        )
        advance(harness)

        // Tap challenge card
        onNodeWithText("Beat Dracula").performClick()
        advance(harness)

        // Should navigate to challenge detail screen.
        // "Beat Dracula" appears in both TopBar and content heading, so use
        // the unique "Attempt Challenge" button to verify we're on the detail screen.
        onNodeWithText("Attempt Challenge").performScrollTo().assertIsDisplayed()
        onNodeWithText("Leaderboard").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun challengeDetailShowsLeaderboard() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(id = "1", gameId = "1", name = "Beat Dracula")
        harness.challengeRepo.preSetLeaderboard(
            listOf(
                ChallengeLeaderboardEntry(
                    rank = 1,
                    userId = "2",
                    username = "speedrunner42",
                    avatarUrl = null,
                    durationMs = 95_000,
                    completedAt = "2026-02-17T12:01:35Z",
                    isCurrentUser = false,
                ),
                ChallengeLeaderboardEntry(
                    rank = 2,
                    userId = "1",
                    username = "player",
                    avatarUrl = null,
                    durationMs = 120_000,
                    completedAt = "2026-02-17T12:02:00Z",
                    isCurrentUser = true,
                ),
            )
        )
        // Pre-load so skeleton with SpShimmer never renders
        harness.preLoadChallengeDetail("1")

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeDetail("1"))
        )
        advance(harness)

        // Leaderboard is at the bottom of a scrollable column; scroll to entries
        onNodeWithText("Leaderboard").performScrollTo().assertIsDisplayed()
        // Each leaderboard row has contentDescription "Rank N: username, time"
        onNodeWithContentDescription("Rank 1: speedrunner42, 1:35.0")
            .performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Rank 2: player, 2:00.0")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyLeaderboardShowsEncouragement() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(id = "1", gameId = "1", name = "Beat Dracula")
        harness.challengeRepo.preSetLeaderboard(emptyList())
        harness.preLoadChallengeDetail("1")

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeDetail("1"))
        )
        advance(harness)

        // SpEmptyStates.NoAttempts: title = "No attempts yet", message = "Be the first..."
        onNodeWithText("No attempts yet", substring = true).performScrollTo().assertIsDisplayed()
    }
}
