package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.gamepad.InputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the floating section indicator (pill) that replaces the bottom
 * tab bar in gamepad navigation mode.
 *
 * Nav style follows the control method currently IN USE (see
 * `resolveGamepadNavStyle`): the pill shows when [InputMode] is GAMEPAD and the
 * tab bar shows for TOUCH (keyboard/mouse/touch) — regardless of whether a
 * controller is connected.
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
    fun bottomNavVisibleInTouchMode() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()
        onNodeWithContentDescription("Settings").assertExists()

        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }

    @Test
    fun sectionIndicatorAppearsWhenUsingGamepad() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Home").assertDoesNotExist()
    }

    @Test
    fun touchInputKeepsTabBarEvenWithControllerConnected() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // A controller is connected, but the user is navigating with keyboard/mouse
        // (TOUCH). Nav style follows usage, so the tab bar stays and the pill does not show.
        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        harness.gamepadPortManager.setInputMode(InputMode.TOUCH)
        advanceQuick(harness)

        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }

    @Test
    fun switchingBetweenInputMethodsTogglesNavStyle() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Use gamepad -> pill.
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)
        onNodeWithContentDescription("Section indicator").assertExists()

        // Switch back to keyboard/mouse -> tab bar.
        harness.gamepadPortManager.setInputMode(InputMode.TOUCH)
        advanceQuick(harness)
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
        onNodeWithContentDescription("Home").assertExists()
    }

    @Test
    fun sectionIndicatorShowsActiveSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        onNodeWithContentDescription("Section: Home, active").assertExists()
        onNodeWithContentDescription("Section: Consoles").assertExists()
        onNodeWithContentDescription("Section: Settings").assertExists()
    }

    @Test
    fun sectionCyclingUpdatesActiveSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advance(harness)

        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        advanceQuick(harness)

        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Section: Explore, active").assertExists()
    }
}
