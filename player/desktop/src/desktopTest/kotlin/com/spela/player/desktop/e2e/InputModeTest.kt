package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.gamepad.InputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the in-app InputMode flag (TOUCH vs GAMEPAD).
 *
 * InputMode reflects the control method currently in use and DOES drive the nav
 * style (see `resolveGamepadNavStyle`): GAMEPAD -> section pill, TOUCH -> tab
 * bar, regardless of controller connection. These tests verify the flag
 * transitions cleanly and that it drives which navigation widget is rendered.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class InputModeTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun defaultModeIsTouchWithTabBar() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        assertEquals(InputMode.TOUCH, harness.gamepadPortManager.inputMode.value)

        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()
        onNodeWithContentDescription("Settings").assertExists()

        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }

    @Test
    fun inputModeDrivesNavStyle() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // GAMEPAD usage shows the section pill (and hides the tab bar)...
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Home").assertDoesNotExist()

        // ...switching back to TOUCH (keyboard/mouse) restores the tab bar.
        harness.gamepadPortManager.setInputMode(InputMode.TOUCH)
        advanceQuick(harness)

        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }

    @Test
    fun inputModeStateTogglesCleanly() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        repeat(3) {
            harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
            advanceQuick(harness)
            assertEquals(InputMode.GAMEPAD, harness.gamepadPortManager.inputMode.value)

            harness.gamepadPortManager.setInputMode(InputMode.TOUCH)
            advanceQuick(harness)
            assertEquals(InputMode.TOUCH, harness.gamepadPortManager.inputMode.value)
        }
    }

    @Test
    fun pillIsRetainedAcrossSectionsInGamepadMode() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)
        onNodeWithContentDescription("Section: Home, active").assertExists()

        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        advanceQuick(harness)

        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Section: Explore, active").assertExists()
    }
}
