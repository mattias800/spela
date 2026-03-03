package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.gamepad.InputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the floating section indicator that replaces the bottom tab bar
 * when the user is in gamepad input mode.
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
    fun sectionIndicatorAppearsOnGamepadInput() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Switch to gamepad input mode (simulates D-pad press)
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        // Section indicator should be visible
        onNodeWithContentDescription("Section indicator").assertExists()

        // Bottom nav tabs should be gone
        onNodeWithContentDescription("Home").assertDoesNotExist()
    }

    @Test
    fun gamepadConnectAloneDoesNotShowIndicator() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Just connecting a gamepad (without D-pad input) should NOT switch nav style
        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        advanceQuick(harness)

        // Tab bar should still be visible (touch mode)
        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()

        // Section indicator should not exist
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }

    @Test
    fun sectionIndicatorDisappearsOnTouchInput() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)
        onNodeWithContentDescription("Section indicator").assertExists()

        // Switch back to touch mode
        harness.gamepadPortManager.setInputMode(InputMode.TOUCH)
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

        // Switch to gamepad mode (we're on Home screen)
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        // Home should be the active section
        onNodeWithContentDescription("Section: Home, active").assertExists()
        onNodeWithContentDescription("Section: Consoles").assertExists()
        onNodeWithContentDescription("Section: Settings").assertExists()
    }

    @Test
    fun sectionCyclingUpdatesActiveSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Switch to gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advance(harness)

        // Cycle to next section
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        advanceQuick(harness)

        // Indicator should show new active section
        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Section: Consoles, active").assertExists()
    }
}
