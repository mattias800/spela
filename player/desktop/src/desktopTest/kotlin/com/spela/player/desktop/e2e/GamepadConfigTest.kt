package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the gamepad configuration UI (local multiplayer).
 *
 * Verifies:
 * - Empty state shows "No controller" placeholders
 * - Device connection appears in the controller list
 * - Multiple devices show on separate port rows
 * - Activity indicator is visible after input reported
 * - Port swap updates the assignments
 * - Disconnect removes the controller from the list
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GamepadConfigTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToConsoleSettings(
        harness: SpelaTestHarness,
        consoleId: String,
    ) {
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ConsoleSettings(consoleId))
        )
        advance(harness)
    }

    @Test
    fun emptyStateShowsNoControllerPlaceholders() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // Controllers heading should exist
        onNodeWithContentDescription("Controllers heading").assertExists()

        // All 4 player slots should show "No controller"
        onNodeWithContentDescription("Player 1: No controller").assertExists()
        onNodeWithContentDescription("Player 2: No controller").assertExists()
        onNodeWithContentDescription("Player 3: No controller").assertExists()
        onNodeWithContentDescription("Player 4: No controller").assertExists()
    }

    @Test
    fun deviceConnectionAppearsInControllerList() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // Connect a device
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        advance(harness)

        // Player 1 should now show the device name
        onNodeWithContentDescription("Player 1: Xbox Controller").assertExists()

        // Player 2 should still be empty
        onNodeWithContentDescription("Player 2: No controller").assertExists()
    }

    @Test
    fun multipleDevicesShowOnSeparatePortRows() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // Connect two devices
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "PS5 Controller")
        advance(harness)

        onNodeWithContentDescription("Player 1: Xbox Controller").assertExists()
        onNodeWithContentDescription("Player 2: PS5 Controller").assertExists()
        onNodeWithContentDescription("Player 3: No controller").assertExists()
    }

    @Test
    fun activityIndicatorShowsActiveAfterInput() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // Connect a device and let the UI settle
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        advance(harness)

        // Report activity, then advance virtual time by LESS than the 500ms
        // activity window (REFRESH_INTERVAL is 200ms) so the config VM's refresh
        // loop recomputes isActive while the input is still fresh. The harness
        // injects the test scheduler as the clock, so this is deterministic —
        // advancing a full second (as advance/advanceQuick do) would push past
        // the window and read inactive. See #1198.
        harness.gamepadPortManager.reportActivity(0)
        harness.testDispatcher.scheduler.advanceTimeBy(300)
        harness.testDispatcher.scheduler.runCurrent()
        waitForIdle()

        // The activity indicator should be active
        onAllNodesWithContentDescription("Activity indicator active").assertCountEquals(1)
    }

    @Test
    fun portSwapUpdatesAssignments() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // Connect two devices
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "PS5 Controller")
        advance(harness)

        // Verify initial assignment
        onNodeWithContentDescription("Player 1: Xbox Controller").assertExists()
        onNodeWithContentDescription("Player 2: PS5 Controller").assertExists()

        // Swap ports 0 and 1
        harness.gamepadPortManager.swapPorts(0, 1)
        advance(harness)

        // After swap, controllers should be on opposite ports
        onNodeWithContentDescription("Player 1: PS5 Controller").assertExists()
        onNodeWithContentDescription("Player 2: Xbox Controller").assertExists()
    }

    @Test
    fun disconnectRemovesControllerFromList() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // Connect then disconnect
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        advance(harness)
        onNodeWithContentDescription("Player 1: Xbox Controller").assertExists()

        harness.gamepadPortManager.disconnectDevice(1)
        advance(harness)

        // Should return to "No controller"
        onNodeWithContentDescription("Player 1: No controller").assertExists()
    }

    @Test
    fun configureButtonExistsForConnectedController() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // Connect a device
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        advance(harness)

        // Configure button should be visible
        onNodeWithText("Configure").assertExists()
    }

    @Test
    fun portManagerTracksCorrectPortAfterSwap() {
        val harness = createLoggedInHarness()

        harness.gamepadPortManager.connectDevice(1, "Xbox")
        harness.gamepadPortManager.connectDevice(2, "PS5")

        assertEquals(0, harness.gamepadPortManager.getPort(1))
        assertEquals(1, harness.gamepadPortManager.getPort(2))

        harness.gamepadPortManager.swapPorts(0, 1)

        assertEquals(1, harness.gamepadPortManager.getPort(1))
        assertEquals(0, harness.gamepadPortManager.getPort(2))
    }
}
