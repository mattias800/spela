package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * E2E tests for challenge creation flow.
 * Tests: Creating a challenge from the emulation overlay, form validation, cancel flow.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ChallengeCreationTest {

    private fun createHarnessWithGameReady(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.downloadRepo.preCacheGame("1")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        return harness
    }

    @Test
    fun challengeOverlayCreatesChallengeFromPrefilledForm() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Start game
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        // Open overlay
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        onNodeWithContentDescription("Challenge").assertIsDisplayed()
        onNodeWithContentDescription("Challenge").performClick()
        advanceQuick(harness)

        onNodeWithText("Create Challenge").assertIsDisplayed()
        onNodeWithText("Title").assertIsDisplayed()
        onNodeWithText("Completion").assertIsDisplayed()
        onNodeWithText("Medium").assertIsDisplayed()
        onNodeWithText("Castlevania Challenge").assertIsDisplayed()
        onNodeWithText("Create").performClick()
        advance(harness)

        assertTrue(
            harness.libretroController.saveCallCount > 0,
            "Challenge creation should serialize the save state"
        )
        assertTrue(
            harness.challengeRepo.challenges.isNotEmpty(),
            "Challenge should be persisted via repository"
        )
    }

    @Test
    fun cancelChallengeCreationResumesGame() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Start game -> open overlay -> open challenge form -> cancel
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithContentDescription("Challenge").performClick()
        advanceQuick(harness)

        // Cancel
        onNodeWithText("Cancel").performClick()
        advanceQuick(harness)

        // Form should be dismissed, game should be running
        onNodeWithText("Create Challenge").assertDoesNotExist()
        assertTrue(harness.libretroController.isRunning, "Game should still be running after cancel")
    }
}
