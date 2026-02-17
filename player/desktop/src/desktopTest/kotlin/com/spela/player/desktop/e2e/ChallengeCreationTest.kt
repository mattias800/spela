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
    fun challengeButtonAppearsInOverlay() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Start game
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)

        // Open overlay
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness)

        // "Challenge" button should be visible in overlay
        onNodeWithContentDescription("Challenge").assertIsDisplayed()
    }

    @Test
    fun challengeButtonOpensCreationForm() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Start game -> open overlay -> tap Challenge
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness)
        onNodeWithContentDescription("Challenge").performClick()
        advance(harness)

        // Creation form should appear with expected fields
        onNodeWithText("Create Challenge").assertIsDisplayed()
        onNodeWithText("Title").assertIsDisplayed()
        // Default type and difficulty chips should be visible
        onNodeWithText("Completion").assertIsDisplayed()
        onNodeWithText("Medium").assertIsDisplayed()
    }

    @Test
    fun createChallengeSubmitsToRepository() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Start game -> open overlay -> create challenge
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness)
        onNodeWithContentDescription("Challenge").performClick()
        advance(harness)

        // Title is pre-filled with "Castlevania Challenge", submit it
        onNodeWithText("Create").performClick()
        advance(harness)

        // Verify save state was serialized (CreateChallenge -> initChallengeCreation -> serialize)
        assertTrue(
            harness.libretroController.saveCallCount > 0,
            "Challenge creation should serialize the save state"
        )

        // Verify challenge was created in repository
        assertTrue(
            harness.challengeRepo.challenges.isNotEmpty(),
            "Challenge should be persisted via repository"
        )
    }

    @Test
    fun creationFormPreFillsTitle() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Start game -> open overlay -> open creation form
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness)
        onNodeWithContentDescription("Challenge").performClick()
        advance(harness)

        // Title should be pre-filled with game title
        onNodeWithText("Castlevania Challenge").assertIsDisplayed()
    }

    @Test
    fun cancelChallengeCreationResumesGame() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Start game -> open overlay -> open challenge form -> cancel
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness)
        onNodeWithContentDescription("Challenge").performClick()
        advance(harness)

        // Cancel
        onNodeWithText("Cancel").performClick()
        advance(harness)

        // Form should be dismissed, game should be running
        onNodeWithText("Create Challenge").assertDoesNotExist()
        assertTrue(harness.libretroController.isRunning, "Game should still be running after cancel")
    }
}
