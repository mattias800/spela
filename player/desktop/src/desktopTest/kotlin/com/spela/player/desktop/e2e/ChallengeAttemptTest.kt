package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for challenge attempt flow.
 * Tests: Starting an attempt, modified overlay, mark complete, give up, restart.
 *
 * Per spec (US-5):
 * - "Attempt" downloads challenge save state and loads game with that state
 * - Modified overlay: "Mark Complete", "Restart", "Give Up" (no Save/Load/FF)
 * - Timer pauses when overlay is open
 * - "Mark Complete" submits result and shows result screen
 * - "Give Up" abandons attempt
 * - "Restart" reloads original save state and resets timer
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ChallengeAttemptTest {

    /**
     * Creates a harness with the game in challenge emulation mode.
     * Navigates via ShowOverlay with challengeId to trigger challenge mode.
     */
    private fun createHarnessInChallengeMode(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.downloadRepo.preCacheGame("1")
        harness.challengeRepo.preAddChallenge(id = "1", gameId = "1", name = "Beat Dracula")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        // Start emulation directly in challenge mode
        harness.navigationViewModel.onIntent(
            NavigationIntent.ShowOverlay(gameId = "1", challengeId = "1")
        )
        return harness
    }

    /**
     * Creates a harness with the game in challenge mode AND the challenge attempt
     * fully initialized (challengeAttemptId set). This requires calling StartGame
     * with challengeSaveData since the navigation path doesn't pass save data.
     */
    private fun ComposeUiTest.createAndStartChallengeAttempt(): SpelaTestHarness {
        val harness = createHarnessInChallengeMode()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // The LaunchedEffect-triggered StartGame doesn't pass save data,
        // so challengeAttemptId stays null. Restart with save data to properly
        // initialize the attempt lifecycle.
        harness.emulationViewModel.onIntent(
            EmulationIntent.StartGame(
                gameId = "1",
                challengeId = "1",
                challengeSaveData = ByteArray(256),
            )
        )
        advance(harness)

        return harness
    }

    @Test
    fun attemptOverlayShowsModifiedButtons() = runComposeUiTest {
        val harness = createHarnessInChallengeMode()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Open overlay during challenge attempt
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        // Challenge-specific overlay buttons should appear
        onNodeWithContentDescription("Mark Complete").assertIsDisplayed()
        onNodeWithContentDescription("Restart").assertIsDisplayed()
        onNodeWithContentDescription("Give Up").assertIsDisplayed()

        // Normal overlay buttons should NOT appear (per spec: no Save/Load/FF)
        onNodeWithContentDescription("Save").assertDoesNotExist()
        onNodeWithContentDescription("Load").assertDoesNotExist()
        onNodeWithContentDescription("Fast").assertDoesNotExist()
    }

    @Test
    fun completeShowsResultDialog() = runComposeUiTest {
        val harness = createAndStartChallengeAttempt()

        // Open overlay -> complete challenge
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithContentDescription("Mark Complete").performClick()
        advance(harness)

        // Result dialog should show completion info
        // The fake completeAttempt returns durationMs=330_000 (5:30.0)
        onNodeWithText("Challenge Complete!", substring = true).assertIsDisplayed()
        onNodeWithText("5:30.0", substring = true).assertIsDisplayed()
    }

    @Test
    fun giveUpShowsConfirmationDialog() = runComposeUiTest {
        val harness = createHarnessInChallengeMode()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Open overlay -> give up
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithContentDescription("Give Up").performClick()
        advanceQuick(harness)

        // Confirmation dialog should appear
        onNodeWithText("Give Up Challenge?", substring = true).assertIsDisplayed()
        onNodeWithText("Keep Playing").assertIsDisplayed()
        // Note: "Give Up" text also appears in the overlay button, so we verify
        // the dialog via its unique title "Give Up Challenge?" and "Keep Playing" button
    }

    @Test
    fun keepPlayingDismissesGiveUpDialog() = runComposeUiTest {
        val harness = createHarnessInChallengeMode()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Open overlay -> give up -> keep playing
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithContentDescription("Give Up").performClick()
        advanceQuick(harness)

        onNodeWithText("Keep Playing").performClick()
        advanceQuick(harness)

        // Dialog should be dismissed
        onNodeWithText("Give Up Challenge?").assertDoesNotExist()
    }

    @Test
    fun challengeTimerIsVisibleDuringPlay() = runComposeUiTest {
        val harness = createHarnessInChallengeMode()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Timer should be visible during gameplay (challenge timer HUD)
        onNodeWithContentDescription("Challenge timer", substring = true).assertIsDisplayed()
    }

    @Test
    fun challengeTimerShowsInOverlay() = runComposeUiTest {
        val harness = createHarnessInChallengeMode()

        mainClock.autoAdvance = false
        setContent { harness.App() }
        advance(harness)

        // Open overlay
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        // Overlay should show challenge timer in display format
        onNodeWithContentDescription("Challenge timer:", substring = true).assertIsDisplayed()
    }
}
