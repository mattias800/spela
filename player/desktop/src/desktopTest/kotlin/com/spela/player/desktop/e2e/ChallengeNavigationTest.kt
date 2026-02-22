package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ChallengeDifficulty
import com.spela.player.domain.model.ChallengeType
import com.spela.player.presentation.intent.ChallengeIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for challenge navigation flows.
 * Tests: Navigation from game detail to challenges, back navigation, view challenges button.
 *
 * Per spec (US-3, US-7):
 * - Player: "Challenges" section on game detail screen
 * - Back navigation returns to correct previous screen
 *
 * IMPORTANT: ChallengeDetailScreen has SpShimmer in its skeleton.
 * We pre-load challenge data to skip the skeleton and avoid animation hangs.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ChallengeNavigationTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun SpelaTestHarness.preLoadChallengeDetail(challengeId: String) {
        challengeDetailViewModel.onIntent(ChallengeIntent.LoadChallengeDetail(challengeId))
        // Bounded advance instead of advanceUntilIdle() — the gamepad config
        // ViewModel runs a perpetual 200ms refresh loop on the shared dispatcher,
        // so advanceUntilIdle() would hang forever.
        testDispatcher.scheduler.advanceTimeBy(2_000)
        testDispatcher.scheduler.runCurrent()
    }

    @Test
    fun viewChallengesFromGameDetailNavigatesToList() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(
            id = "1",
            gameId = "1",
            name = "Beat Dracula",
            type = ChallengeType.SPEEDRUN,
            difficulty = ChallengeDifficulty.HARD,
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Navigate programmatically to challenge list (same flow the button triggers)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeList("1", "Castlevania"))
        )
        advance(harness)

        // Should show challenges on the ChallengeList screen
        onNodeWithText("Beat Dracula").assertIsDisplayed()
    }

    @Test
    fun backFromChallengeListReturnsToGameDetail() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(id = "1", gameId = "1", name = "Beat Dracula")

        setContent { harness.App() }

        // Navigate: Home -> Game Detail -> Challenge List
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeList("1", "Castlevania"))
        )
        advance(harness)

        // Go back
        onNodeWithContentDescription("Go back").performClick()
        advance(harness)

        // Should be back on game detail (Castlevania title visible at top)
        onNodeWithText("Castlevania").assertIsDisplayed()
    }

    @Test
    fun backFromChallengeDetailReturnsToChallengeList() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(id = "1", gameId = "1", name = "Beat Dracula")
        // Pre-load to avoid skeleton hang
        harness.preLoadChallengeDetail("1")

        setContent { harness.App() }

        // Navigate: Home -> ChallengeList -> ChallengeDetail
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeList("1", "Castlevania"))
        )
        advance(harness)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeDetail("1"))
        )
        advance(harness)

        // Verify we're on the detail screen (scroll to "Attempt Challenge" which is below hero)
        onNodeWithText("Attempt Challenge").performScrollTo().assertIsDisplayed()

        // Go back
        onNodeWithContentDescription("Go back").performClick()
        advance(harness)

        // Should be back on challenge list showing the challenge card
        onNodeWithText("Beat Dracula").assertIsDisplayed()
    }

    @Test
    fun challengeDetailShowsDifficultyAndTypeChips() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(
            id = "1",
            gameId = "1",
            name = "Beat Dracula",
            type = ChallengeType.SPEEDRUN,
            difficulty = ChallengeDifficulty.HARD,
        )
        harness.preLoadChallengeDetail("1")

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeDetail("1"))
        )
        advance(harness)

        // Detail screen shows difficulty and type chips (may need scroll past hero image)
        onNodeWithText("Hard").performScrollTo().assertIsDisplayed()
        onNodeWithText("Speedrun").performScrollTo().assertIsDisplayed()
        // Stats row
        onNodeWithText("Attempts").performScrollTo().assertIsDisplayed()
        onNodeWithText("Completed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun challengeHiddenInNetplayOverlay() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Start game in netplay mode
        harness.navigationViewModel.onIntent(
            NavigationIntent.ShowOverlay(
                gameId = "1",
                netplaySessionId = "test-session",
            )
        )
        advance(harness)

        // Open overlay
        harness.emulationViewModel.onIntent(
            com.spela.player.presentation.intent.EmulationIntent.ToggleOverlay
        )
        advanceQuick(harness)

        // Challenge button should NOT exist in netplay mode overlay
        onNodeWithContentDescription("Challenge").assertDoesNotExist()
    }
}
