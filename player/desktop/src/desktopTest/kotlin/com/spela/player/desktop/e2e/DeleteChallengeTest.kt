package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Challenge
import com.spela.player.domain.model.ChallengeDifficulty
import com.spela.player.domain.model.ChallengeType
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for Delete Challenge UI on ChallengeDetailScreen:
 * - Delete button is visible
 * - Clicking delete shows confirmation dialog
 * - Cancel dismisses confirmation
 * - Confirm triggers delete
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class DeleteChallengeTest {

    private val testChallenge = Challenge(
        id = "c1",
        creatorId = "1",
        creatorUsername = "player",
        creatorAvatarUrl = null,
        gameId = "1",
        gameTitle = "Castlevania",
        gameCoverUrl = null,
        gameConsoleName = "NES",
        name = "Speed Boss",
        description = "Beat the boss under 60 seconds",
        type = ChallengeType.SPEEDRUN,
        difficulty = ChallengeDifficulty.HARD,
        status = "active",
        screenshotUrl = null,
        coreName = "NES",
        saveFileSize = 1024,
        attemptCount = 5,
        completionCount = 2,
        bestTimeMs = 45000,
        expiresAt = null,
        createdAt = "2026-02-17T12:00:00Z",
    )

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.challengeRepo.challenges.add(testChallenge)
        return harness
    }

    private fun ComposeUiTest.navigateToChallengeDetail(harness: SpelaTestHarness) {
        advance(harness)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ChallengeDetail("c1"))
        )
        advanceFully(harness)
    }

    @Test
    fun deleteButtonIsVisible() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToChallengeDetail(harness)

        // Delete button may be below visible area, scroll to it
        onNodeWithTag("delete_challenge_button").performScrollTo()
        onNodeWithTag("delete_challenge_button").assertIsDisplayed()
    }

    @Test
    fun deleteButtonShowsConfirmDialog() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToChallengeDetail(harness)

        onNodeWithTag("delete_challenge_button").performScrollTo()
        onNodeWithTag("delete_challenge_button").performClick()
        advanceQuick(harness)

        onNodeWithText("Delete Challenge?").assertIsDisplayed()
    }

    @Test
    fun cancelDismissesConfirmDialog() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToChallengeDetail(harness)

        onNodeWithTag("delete_challenge_button").performScrollTo()
        onNodeWithTag("delete_challenge_button").performClick()
        advanceQuick(harness)
        onNodeWithText("Delete Challenge?").assertIsDisplayed()

        onNodeWithText("Cancel").performClick()
        advanceQuick(harness)

        onNodeWithText("Delete Challenge?").assertDoesNotExist()
    }

    @Test
    fun confirmDeleteTriggersDelete() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToChallengeDetail(harness)

        onNodeWithTag("delete_challenge_button").performScrollTo()
        onNodeWithTag("delete_challenge_button").performClick()
        advanceQuick(harness)

        onNodeWithTag("confirm_delete_button").performClick()
        advance(harness)
        advance(harness)
        advance(harness)

        // After successful delete, dialog and detail screen should be gone
        onNodeWithText("Delete Challenge?").assertDoesNotExist()
    }
}
