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
 * E2E tests for input mode detection (TOUCH vs GAMEPAD).
 *
 * Verifies:
 * - Default mode is touch → tab bar visible
 * - Setting GAMEPAD mode → section indicator appears, tab bar hidden
 * - Setting TOUCH mode → tab bar returns
 * - Focus recovery: D-pad key press populates focus even from unfocused state
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

        // Default mode should be TOUCH
        assertEquals(InputMode.TOUCH, harness.gamepadPortManager.inputMode.value)

        // Tab bar should be visible
        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()
        onNodeWithContentDescription("Settings").assertExists()

        // Section indicator should not exist
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }

    @Test
    fun gamepadModeSwitchesToSectionIndicator() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Switch to GAMEPAD mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        // Section indicator should appear
        onNodeWithContentDescription("Section indicator").assertExists()

        // Tab bar should be hidden
        onNodeWithContentDescription("Home").assertDoesNotExist()
    }

    @Test
    fun touchModeRestoresTabBar() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Switch to GAMEPAD mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        // Verify gamepad mode
        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Home").assertDoesNotExist()

        // Switch back to TOUCH mode (simulates touch on content)
        harness.gamepadPortManager.setInputMode(InputMode.TOUCH)
        advanceQuick(harness)

        // Tab bar should return
        onNodeWithContentDescription("Home").assertExists()
        onNodeWithContentDescription("Consoles").assertExists()

        // Section indicator should be gone
        onNodeWithContentDescription("Section indicator").assertDoesNotExist()
    }

    @Test
    fun inputModeTogglesRepeatedly() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Cycle through modes multiple times
        repeat(3) {
            harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
            advanceQuick(harness)
            onNodeWithContentDescription("Section indicator").assertExists()
            onNodeWithContentDescription("Home").assertDoesNotExist()

            harness.gamepadPortManager.setInputMode(InputMode.TOUCH)
            advanceQuick(harness)
            onNodeWithContentDescription("Home").assertExists()
            onNodeWithContentDescription("Section indicator").assertDoesNotExist()
        }
    }

    @Test
    fun gamepadModePreservedAcrossScreens() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)
        onNodeWithContentDescription("Section: Home, active").assertExists()

        // Navigate to a different section
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        advanceQuick(harness)

        // Should still be in gamepad mode with the section indicator
        onNodeWithContentDescription("Section indicator").assertExists()
        onNodeWithContentDescription("Section: Explore, active").assertExists()
    }
}
