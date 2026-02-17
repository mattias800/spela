package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ChallengeLeaderboardEntry
import com.spela.player.presentation.intent.ChallengeIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for challenge leaderboard display.
 * Tests: Ranked entries, time formatting, empty leaderboard.
 *
 * Per spec (US-6):
 * - Ranked by server-computed duration, ascending
 * - One entry per user (best attempt only)
 * - Top 3: gold/silver/bronze rank badges
 * - Current user's rank always visible (highlighted row)
 * - Time displayed as M:SS.s in monospace font
 *
 * IMPORTANT: ChallengeDetailScreen has SpShimmer in its skeleton.
 * We pre-load challenge data to skip the skeleton and avoid animation hangs.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ChallengeLeaderboardTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun SpelaTestHarness.preLoadChallengeDetail(challengeId: String) {
        challengeDetailViewModel.onIntent(ChallengeIntent.LoadChallengeDetail(challengeId))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /**
     * Navigate to ChallengeDetail by going through ChallengeList and clicking
     * the challenge card. This avoids the SpShimmer skeleton hang that occurs
     * with direct programmatic navigation to ChallengeDetail.
     */
    private fun ComposeUiTest.navigateToChallengeDetailViaList(
        harness: SpelaTestHarness,
        challengeName: String,
        gameId: String = "1",
        gameTitle: String = "Castlevania",
    ) {
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeList(gameId, gameTitle))
        )
        advance(harness)
        onNodeWithText(challengeName).performClick()
        advance(harness)
    }

    companion object {
        val SAMPLE_LEADERBOARD = listOf(
            ChallengeLeaderboardEntry(
                rank = 1,
                userId = "10",
                username = "speedking",
                avatarUrl = null,
                durationMs = 62_300,
                completedAt = "2026-02-17T12:01:02Z",
                isCurrentUser = false,
            ),
            ChallengeLeaderboardEntry(
                rank = 2,
                userId = "20",
                username = "retro_master",
                avatarUrl = null,
                durationMs = 85_700,
                completedAt = "2026-02-17T12:01:25Z",
                isCurrentUser = false,
            ),
            ChallengeLeaderboardEntry(
                rank = 3,
                userId = "1",
                username = "player",
                avatarUrl = null,
                durationMs = 120_500,
                completedAt = "2026-02-17T12:02:00Z",
                isCurrentUser = true,
            ),
        )
    }

    @Test
    fun leaderboardShowsRankedEntries() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(id = "1", gameId = "1", name = "Beat Dracula")
        harness.challengeRepo.preSetLeaderboard(SAMPLE_LEADERBOARD)

        setContent { harness.App() }
        navigateToChallengeDetailViaList(harness, "Beat Dracula")

        // All three entries should be visible (scroll to leaderboard area)
        onNodeWithText("speedking").performScrollTo().assertIsDisplayed()
        onNodeWithText("retro_master").performScrollTo().assertIsDisplayed()
        onNodeWithText("player").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun leaderboardTimesFormattedCorrectly() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(id = "1", gameId = "1", name = "Beat Dracula")
        harness.challengeRepo.preSetLeaderboard(SAMPLE_LEADERBOARD)

        setContent { harness.App() }
        navigateToChallengeDetailViaList(harness, "Beat Dracula")

        // Times should be formatted as M:SS.s
        // 62_300ms -> 1:02.3, 85_700ms -> 1:25.7, 120_500ms -> 2:00.5
        onNodeWithText("1:02.3", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("1:25.7", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("2:00.5", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun currentUserEntryHasSemantics() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(id = "1", gameId = "1", name = "Beat Dracula")
        harness.challengeRepo.preSetLeaderboard(SAMPLE_LEADERBOARD)

        setContent { harness.App() }
        navigateToChallengeDetailViaList(harness, "Beat Dracula")

        // Each leaderboard row has content description "Rank N: username, time"
        onNodeWithContentDescription("Rank 1: speedking, 1:02.3")
            .performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Rank 3: player, 2:00.5")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyLeaderboardShowsCallToAction() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(id = "1", gameId = "1", name = "Beat Dracula")
        harness.challengeRepo.preSetLeaderboard(emptyList())

        setContent { harness.App() }
        navigateToChallengeDetailViaList(harness, "Beat Dracula")

        // SpEmptyStates.NoAttempts: "No attempts yet" + "Be the first..."
        onNodeWithText("No attempts yet", substring = true)
            .performScrollTo().assertIsDisplayed()
        onNodeWithText("Be the first", substring = true)
            .performScrollTo().assertIsDisplayed()
    }
}
