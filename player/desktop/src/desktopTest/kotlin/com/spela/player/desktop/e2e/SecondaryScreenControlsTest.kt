package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.ui.feature.ingame.SecondaryControlsPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the secondary screen controls page P1/P2 port selector.
 *
 * Since PlatformTouchControls is a no-op on desktop, these tests focus
 * on verifying the port toggle UI (P1/P2 buttons, selection state, callback).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SecondaryScreenControlsTest {

    @Test
    fun controlsPageShowsPortSelector() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                onSelectPort = {},
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Player 1 controls").assertExists()
        onNodeWithContentDescription("Player 2 controls").assertExists()
        onNodeWithContentDescription("Control port: Player 1").assertExists()
    }

    @Test
    fun portSelectorSwitchesToP2() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 1,
                onSelectPort = {},
            )
        }
        waitForIdle()

        // When port=1, the row should describe "Player 2"
        onNodeWithContentDescription("Control port: Player 2").assertExists()
        // Both buttons should still exist
        onNodeWithContentDescription("Player 1 controls").assertExists()
        onNodeWithContentDescription("Player 2 controls").assertExists()
    }

    @Test
    fun portSelectorCallsCallback() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        var selectedPort = -1

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                onSelectPort = { selectedPort = it },
            )
        }
        waitForIdle()

        // Click P2 button
        onNodeWithContentDescription("Player 2 controls").performClick()
        waitForIdle()

        assertEquals(1, selectedPort, "Expected selectedPort to be 1 after clicking P2")
    }

    // -- Helpers ---------------------------------------------------------------

    private fun createHarnessWithNesGame(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        return harness
    }

    private fun ComposeUiTest.advanceSeconds(harness: SpelaTestHarness, seconds: Int) {
        mainClock.autoAdvance = false
        repeat(seconds) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
        }
        mainClock.autoAdvance = true
    }

    // -- Integration tests via full harness ------------------------------------

    @Test
    fun touchControlPortResetsOnNewGame() = runComposeUiTest {
        val harness = createHarnessWithNesGame()

        // Start a game
        harness.emulationViewModel.onIntent(EmulationIntent.StartGame(gameId = "1"))
        mainClock.autoAdvance = false
        repeat(4) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
        }
        mainClock.autoAdvance = true

        // Set touchControlPort to 1
        harness.emulationViewModel.onIntent(EmulationIntent.SelectTouchControlPort(1))
        mainClock.autoAdvance = false
        repeat(2) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
        }
        mainClock.autoAdvance = true

        // Verify state is 1
        assertEquals(
            1,
            harness.emulationViewModel.state.value.touchControlPort,
            "Expected touchControlPort to be 1 after selecting port 1"
        )

        // Stop game
        harness.emulationViewModel.onIntent(EmulationIntent.StopGame)
        mainClock.autoAdvance = false
        repeat(4) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
        }
        mainClock.autoAdvance = true

        // Verify touchControlPort reset to 0
        assertEquals(
            0,
            harness.emulationViewModel.state.value.touchControlPort,
            "Expected touchControlPort to reset to 0 after stopping game"
        )
    }

    @Test
    fun portSelectorCallsCallbackForP1() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        var selectedPort = -1

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 1,
                onSelectPort = { selectedPort = it },
            )
        }
        waitForIdle()

        // Click P1 button while on P2
        onNodeWithContentDescription("Player 1 controls").performClick()
        waitForIdle()

        assertEquals(0, selectedPort, "Expected selectedPort to be 0 after clicking P1")
    }
}
