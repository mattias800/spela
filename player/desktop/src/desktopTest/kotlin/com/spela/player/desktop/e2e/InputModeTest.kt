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
 * Post #1187 InputMode no longer drives the nav style — that's tied to physical
 * controller presence. InputMode still exists to tell screens whether the user
 * is doing pointer-style or focus-style navigation (auto-focus, etc.), so these
 * tests verify that the flag transitions cleanly and crucially that flipping
 * InputMode by itself does NOT change which navigation widget is rendered.
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
    fun inputModeFlipsDoNotChangeNavStyle() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // No physical controller is connected — toggling InputMode (as keyboard
        // navigation would) must leave the tab bar visible.
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()

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
    fun pillIsRetainedAcrossScreensWhenControllerStaysConnected() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        harness.gamepadPortManager.connectDevice(1, "Test Controller")
        advanceQuick(harness)
        onNodeWithContentDescription("Section: Home, active").assertExists()

        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        advanceQuick(harness)

        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Section: Explore, active").assertExists()
    }
}
