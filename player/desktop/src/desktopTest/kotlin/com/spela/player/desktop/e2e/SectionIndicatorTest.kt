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
 * tab bar when a physical gamepad is connected. See #1187: the pill is tied to
 * controller presence, not to in-app InputMode, so keyboard + mouse users on
 * desktop never see it flicker in.
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

        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()
        onNodeWithContentDescription("Settings").assertExists()

        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }

    @Test
    fun sectionIndicatorAppearsWhenGamepadConnects() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        advanceQuick(harness)

        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Home").assertDoesNotExist()
    }

    @Test
    fun keyboardInputModeAloneDoesNotShowIndicator() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Keyboard nav (arrow keys, [, ]) flips InputMode to GAMEPAD to drive
        // focus behavior, but with no physical controller connected the nav
        // style must stay as the tab bar so it doesn't flicker against mouse use.
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }

    @Test
    fun sectionIndicatorDisappearsWhenGamepadDisconnects() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        advanceQuick(harness)
        onNodeWithContentDescription("Section indicator").assertExists()

        harness.gamepadPortManager.disconnectDevice(1)
        advanceQuick(harness)

        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()
    }

    @Test
    fun sectionIndicatorShowsActiveSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        harness.gamepadPortManager.connectDevice(1, "Test Controller")
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

        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        advance(harness)

        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        advanceQuick(harness)

        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Section: Explore, active").assertExists()
    }
}
