package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the controller status indicator.
 *
 * Since #1187 the nav style is driven by *physical controller connection*
 * (`controllerStatus.connectedCount > 0`): connecting a controller puts the app
 * into gamepad mode, which hides the side rail / bottom bar and shows the
 * floating `SpSectionIndicator` pill. When 2+ controllers are connected
 * (`isMultiplayer`), the pill additionally renders an `SpControllerStatusRow`
 * of connected-player dots with `showEmptySlots = false` — so each connected
 * port exposes a "Player N connected" / "Player N active" content description,
 * and empty ports render nothing.
 *
 * (The older `SpControllerStatusCard`-in-rail and bottom-bar mini-pill became
 * unreachable when #1187 gated the rail/bottom-bar on `!isGamepadMode`; the
 * section-indicator dots are now the live controller-status surface. See #1198.)
 *
 * Tests verify:
 * - No player dots with 0 or 1 controller (not multiplayer)
 * - Connected-player dots appear with 2+ controllers
 * - Dots update on drop-to-one, disconnect, and reconnect
 * - Only connected ports render (no empty-slot dots)
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
    fun noPlayerDotsWithoutControllers() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // No controllers → not gamepad mode, no controller dots anywhere.
        onNodeWithContentDescription("Player 1 connected").assertDoesNotExist()
    }

    @Test
    fun noPlayerDotsWithOneController() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        advance(harness)

        // One controller enters gamepad mode (section indicator shows) but is
        // not multiplayer, so the pill renders no controller dots.
        onNodeWithContentDescription("Player 1 connected").assertDoesNotExist()
    }

    @Test
    fun playerDotsVisibleWithTwoControllers() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        onNodeWithContentDescription("Player 1 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
    }

    @Test
    fun playerDotsDisappearWhenDroppingToOneController() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        onNodeWithContentDescription("Player 1 connected").assertIsDisplayed()

        harness.gamepadPortManager.disconnectDevice(2)
        advance(harness)

        // Back to a single controller — no longer multiplayer, dots gone.
        onNodeWithContentDescription("Player 1 connected").assertDoesNotExist()
        onNodeWithContentDescription("Player 2 connected").assertDoesNotExist()
    }

    // ---- Dot States ----

    @Test
    fun onlyConnectedPortsRenderDots() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        // The section-indicator row uses showEmptySlots = false, so only the
        // two connected ports render. Ports 3 and 4 show nothing.
        onNodeWithContentDescription("Player 1 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 3 not connected").assertDoesNotExist()
        onNodeWithContentDescription("Player 4 not connected").assertDoesNotExist()
    }

    @Test
    fun reportingActivityKeepsOtherPortConnected() = runComposeUiTest {
        // Reporting input on one port must not error or break the connected
        // state of the other port. (The active-dot state uses a 300ms timeout;
        // after a normal advance it has lapsed back to connected, which is the
        // stable thing to assert.)
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        harness.gamepadPortManager.reportActivity(0)
        advance(harness)

        onNodeWithContentDescription("Player 1 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
    }

    // ---- Disconnect/Reconnect ----

    @Test
    fun disconnectedPortDropsItsDot() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        harness.gamepadPortManager.connectDevice(3, "Pro Controller")
        advance(harness)

        onNodeWithContentDescription("Player 1 connected").assertIsDisplayed()

        // Disconnect P1 (frees port 0). Still multiplayer (ports 1 and 2 stay),
        // so the pill keeps showing dots — but only for the connected ports.
        harness.gamepadPortManager.disconnectDevice(1)
        advance(harness)

        onNodeWithContentDescription("Player 1 connected").assertDoesNotExist()
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

        // Reconnect — may get a different device ID, reclaims the freed port 0.
        harness.gamepadPortManager.connectDevice(99, "Xbox Controller")
        advance(harness)

        onNodeWithContentDescription("Player 1 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
    }
}
