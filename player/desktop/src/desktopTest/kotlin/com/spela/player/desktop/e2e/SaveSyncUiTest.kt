package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Desktop E2E tests for save sync UI — pre-launch status row, timeout dialog,
 * and post-exit uploading state.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SaveSyncUiTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun playButtonIsDisabledDuringSyncBeforeLaunch() = runComposeUiTest {
        val harness = createHarness()
        harness.downloadRepo.preCacheGame("1")
        // Make the SRAM download take longer than a short advance window
        harness.saveDataRepo.downloadDelayMs = 20_000L

        setContent { harness.App() }

        navigateToGameDetail(harness, "1")

        // Tap Play — dispatches PrepareLaunch
        onNodeWithContentDescription("Play Castlevania").performClick()
        // Advance 2s — sync is still in progress (< 10s timeout)
        advanceQuick(harness)

        // Play button should be disabled while syncing
        onNodeWithContentDescription("Play disabled, syncing").assertIsDisplayed()
    }

    @Test
    fun syncMessageAppearsInGameDetailDuringSync() = runComposeUiTest {
        val harness = createHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.saveDataRepo.downloadDelayMs = 20_000L

        setContent { harness.App() }

        navigateToGameDetail(harness, "1")

        onNodeWithContentDescription("Play Castlevania").performClick()
        advanceQuick(harness)

        // Sync message text should appear below the play button
        onNodeWithText("Syncing", substring = true).assertIsDisplayed()
    }

    @Test
    fun timeoutDialogShowsAfterNetworkTimeout() = runComposeUiTest {
        val harness = createHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.saveDataRepo.downloadDelayMs = 20_000L

        setContent { harness.App() }

        navigateToGameDetail(harness, "1")

        onNodeWithContentDescription("Play Castlevania").performClick()
        // 3 × advance = 12s of virtual time — past the 10s timeout
        advance(harness)
        advance(harness)
        advance(harness)

        // Timeout dialog should be shown
        onNodeWithText("Could not sync save").assertIsDisplayed()
        onNodeWithText("Play with local save").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun playWithLocalSaveDismissesDialogAndLaunchesGame() = runComposeUiTest {
        val harness = createHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.saveDataRepo.downloadDelayMs = 20_000L

        setContent { harness.App() }

        navigateToGameDetail(harness, "1")

        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)
        advance(harness)
        advance(harness)

        // Verify timeout dialog
        onNodeWithText("Could not sync save").assertIsDisplayed()

        // Tap "Play with local save" — emits launchReady, overlay shown
        onNodeWithText("Play with local save").performClick()
        advance(harness)

        // Dialog should be gone
        onAllNodesWithText("Could not sync save").assertCountEquals(0)

        // Game should have launched — open overlay and verify
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithText("Exit Game").assertIsDisplayed()
    }

    @Test
    fun cancelLaunchDismissesDialogAndReEnablesPlayButton() = runComposeUiTest {
        val harness = createHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.saveDataRepo.downloadDelayMs = 20_000L

        setContent { harness.App() }

        navigateToGameDetail(harness, "1")

        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)
        advance(harness)
        advance(harness)

        // Verify timeout dialog
        onNodeWithText("Could not sync save").assertIsDisplayed()

        // Tap "Cancel" — clears sync state, no game launch
        onNodeWithText("Cancel").performClick()
        advanceQuick(harness)

        // Dialog should be gone and play button should be enabled again
        onAllNodesWithText("Could not sync save").assertCountEquals(0)
        onNodeWithContentDescription("Play Castlevania").assertIsDisplayed()
    }

    @Test
    fun playButtonEnabledAfterSuccessfulSyncAndGameExit() = runComposeUiTest {
        val harness = createHarness()
        harness.downloadRepo.preCacheGame("1")
        // No download delay — sync completes instantly

        setContent { harness.App() }

        navigateToGameDetail(harness, "1")

        // Tap Play — sync finishes, game launches
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)

        // Game is running; stop it via the ViewModel
        harness.emulationViewModel.onIntent(EmulationIntent.StopGame)
        advance(harness) // upload completes (fake is instant)

        // Navigate back to game detail
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        advanceFully(harness)

        // Sync state should be cleared — play button enabled (no "Play disabled, syncing")
        onAllNodesWithContentDescription("Play disabled, syncing").assertCountEquals(0)
    }
}
