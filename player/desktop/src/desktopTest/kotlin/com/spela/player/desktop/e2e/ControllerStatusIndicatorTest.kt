package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the controller status indicator.
 *
 * The Compose UI test window is wide enough to trigger LABELED_RAIL layout (> 840dp),
 * so the SpNavigationRail with SpControllerStatusCard is what's on screen.  The card:
 *   - Is only shown when 2+ controllers are connected (isMultiplayer = true)
 *   - Has contentDescription "$connectedCount controllers connected"
 *   - Navigates to Settings on click
 *   - Renders all 4 port dots (showEmptySlots = true), including disconnected slots
 *
 * Tests verify:
 * - Visibility rules (hidden with 0 or 1 controller, visible with 2+)
 * - Card disappears when dropping back to 1 controller
 * - Individual dot content descriptions for connected ports
 * - Click navigates to Settings
 * - Disconnect and reconnect behaviour
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ControllerStatusIndicatorTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // ---- Visibility ----

    @Test
    fun hiddenWithNoControllers() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // No pill when there are zero controllers
        onNodeWithContentDescription("0 controllers connected").assertDoesNotExist()
    }

    @Test
    fun hiddenWithOneController() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        advance(harness)

        // Still no pill — one controller does not trigger multiplayer mode
        onNodeWithContentDescription("1 controllers connected").assertDoesNotExist()
    }

    @Test
    fun visibleWithTwoControllers() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        onNodeWithContentDescription("2 controllers connected").assertIsDisplayed()
    }

    @Test
    fun disappearsWhenDroppingToOneController() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        onNodeWithContentDescription("2 controllers connected").assertIsDisplayed()

        harness.gamepadPortManager.disconnectDevice(2)
        advance(harness)

        // Pill gone — back to single-controller (non-multiplayer)
        onNodeWithContentDescription("2 controllers connected").assertDoesNotExist()
        onNodeWithContentDescription("1 controllers connected").assertDoesNotExist()
    }

    // ---- Dot States ----

    @Test
    fun connectedDotsShowCorrectState() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        // The navigation-rail card renders all 4 slots (showEmptySlots = true)
        onNodeWithContentDescription("Player 1 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
        // Slots 3 and 4 are empty and shown as not connected
        onNodeWithContentDescription("Player 3 not connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 4 not connected").assertIsDisplayed()
    }

    @Test
    fun reportingActivityDoesNotBreakConnectedState() = runComposeUiTest {
        // The active dot state uses a real-clock 300ms timeout, making it too
        // fragile to assert a specific "active" state in the test harness.
        // This test verifies that reporting activity does not cause an error
        // or break the connected state of the other port.
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        // Report activity on port 0 (Player 1) — should not crash or break state
        harness.gamepadPortManager.reportActivity(0)
        advanceQuick(harness)

        // The indicator is still visible and both ports still exist
        onNodeWithContentDescription("2 controllers connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
    }

    // ---- Navigation ----

    @Test
    fun clickingCardNavigatesToSettings() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        onNodeWithContentDescription("2 controllers connected").performClick()
        advance(harness)

        assertEquals(
            SpScreen.Settings.route,
            harness.navigationViewModel.state.value.currentScreen.route,
        )
    }

    // ---- Disconnect/Reconnect ----

    @Test
    fun disconnectedPortShowsAsNotConnected() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        harness.gamepadPortManager.connectDevice(3, "Pro Controller")
        advance(harness)

        onNodeWithContentDescription("3 controllers connected").assertIsDisplayed()

        // Disconnect P1
        harness.gamepadPortManager.disconnectDevice(1)
        advance(harness)

        // Still multiplayer; card shows 2 connected controllers
        onNodeWithContentDescription("2 controllers connected").assertIsDisplayed()
        // P1 slot is now empty — the card shows it as "not connected" (showEmptySlots = true)
        onNodeWithContentDescription("Player 1 not connected").assertIsDisplayed()
        // P2 and P3 stay connected
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 3 connected").assertIsDisplayed()
    }

    @Test
    fun reconnectedControllerReclaimsPort() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        // Disconnect P1
        harness.gamepadPortManager.disconnectDevice(1)
        advance(harness)

        // Reconnect — may get a different device ID
        harness.gamepadPortManager.connectDevice(99, "Xbox Controller")
        advance(harness)

        // Port 0 is reclaimed; back to 2 controllers
        onNodeWithContentDescription("2 controllers connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 1 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
    }
}
