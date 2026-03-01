package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the floating section indicator that replaces the bottom tab bar
 * when a gamepad controller is connected.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SectionIndicatorTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun bottomNavVisibleWhenNoGamepad() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Bottom nav tabs should be visible
        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()
        onNodeWithContentDescription("Settings").assertExists()

        // Section indicator should not exist
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }

    @Test
    fun sectionIndicatorAppearsOnConnect() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Connect a gamepad
        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        advanceQuick(harness)

        // Section indicator should be visible (within 3s auto-hide window)
        onNodeWithContentDescription("Section indicator").assertExists()

        // Bottom nav tabs should be gone
        onNodeWithContentDescription("Home").assertDoesNotExist()
    }

    @Test
    fun sectionIndicatorDisappearsOnDisconnect() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Connect then disconnect
        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        advanceQuick(harness)
        harness.gamepadPortManager.disconnectDevice(1)
        advanceQuick(harness)

        // Section indicator should be gone
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()

        // Bottom nav should be back
        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()
    }

    @Test
    fun sectionIndicatorShowsActiveSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Connect gamepad (we're on Home screen)
        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        advanceQuick(harness)

        // Home should be the active section
        onNodeWithContentDescription("Section: Home, active").assertExists()
        onNodeWithContentDescription("Section: Consoles").assertExists()
        onNodeWithContentDescription("Section: Settings").assertExists()
    }

    @Test
    fun sectionCyclingShowsIndicator() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Connect gamepad
        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        advance(harness)

        // Wait for auto-hide (advance past the 3s window)
        advance(harness)

        // Cycle to next section
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        advanceQuick(harness)

        // Indicator should reappear with new active section
        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Section: Consoles, active").assertExists()
    }

    @Test
    fun sectionIndicatorAutoHides() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Connect gamepad — indicator appears
        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        advanceQuick(harness)
        onNodeWithContentDescription("Section indicator").assertExists()

        // Advance past the 3s auto-hide window (advance = 4 iterations of 1s each)
        advance(harness)

        // Indicator should have auto-hidden
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }
}
