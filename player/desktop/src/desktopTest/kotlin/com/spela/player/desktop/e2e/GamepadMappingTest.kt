package com.spela.player.desktop.e2e

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the desktop gamepad mapping editor (#1334; RetroArch-style
 * redesign #1377).
 *
 * Verifies the console-button-oriented editor: it opens from console settings,
 * lists the console's buttons with the physical position that triggers each
 * (brand-neutral positional names), and opening a button starts a hold-to-bind
 * capture prompt that can be cancelled. The hold/abort *timing* is covered by
 * GamepadMappingViewModelTest; this suite covers the rendering and interaction.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GamepadMappingTest {

    // RetroPad ids on the NES layout.
    private val NES_B = 0
    private val NES_A = 8

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.openGamepadEditor(harness: SpelaTestHarness, consoleId: String) {
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ConsoleSettings(consoleId)))
        advance(harness)
        // The entry is a LazyColumn item below the fold — scroll the list so it
        // composes before interacting.
        onNodeWithTag("console-settings-list")
            .performScrollToNode(hasTestTag("configure_gamepad_buttons"))
        advance(harness)
        onNodeWithTag("configure_gamepad_buttons").performClick()
        advance(harness)
        onNodeWithTag("gamepad_mapping_dialog").assertExists()
    }

    @Test
    fun editorListsConsoleButtonsWithBoundPositions() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        openGamepadEditor(harness, "nes")

        // The editor lists the CONSOLE's buttons (not the gamepad's positions).
        onNodeWithTag("mapping_output_$NES_B").assertExists()
        onNodeWithTag("mapping_output_$NES_A").assertExists()

        // Each shows which physical position triggers it, by canonical name.
        // NES default: B ← bottom face, A ← right face. Rows merge semantics, so
        // query the value Text unmerged.
        onNodeWithTag("mapping_bound_$NES_B", useUnmergedTree = true).assertTextEquals("Bottom button")
        onNodeWithTag("mapping_bound_$NES_A", useUnmergedTree = true).assertTextEquals("Right button")
    }

    @Test
    fun openingAConsoleButtonStartsHoldToBindPrompt() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        openGamepadEditor(harness, "nes")

        onNodeWithTag("mapping_output_$NES_A").performScrollTo().performClick()
        advanceQuick(harness) // stay under the 5s abort window

        onNodeWithTag("binding_prompt").assertExists()
        onNodeWithText("Assign A").assertExists()
        // Capture is now active so a held button would feed the binder.
        assert(harness.gamepadPortManager.bindCaptureActive.value)
    }

    @Test
    fun pressingAButtonInThePromptCapturesItWithoutClosing() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        openGamepadEditor(harness, "nes")

        onNodeWithTag("mapping_output_$NES_A").performScrollTo().performClick()
        advanceQuick(harness)
        onNodeWithTag("binding_prompt").assertExists()

        // Press (hold) the bottom face button. This used to just activate the
        // focused Cancel button and close the prompt (the #1377 bug the dialog's
        // onPreviewKeyEvent fixes). runCurrent (no virtual-time advance) processes
        // the capture without elapsing the 2s commit hold.
        onNodeWithTag("binding_prompt").performKeyInput { keyDown(Key.ButtonA) }
        harness.testDispatcher.scheduler.runCurrent()
        waitForIdle()

        onNodeWithTag("binding_prompt").assertExists() // did NOT close
        onNodeWithTag("binding_held", useUnmergedTree = true).assertTextEquals("Bottom button")
    }

    @Test
    fun cancellingBindingReturnsToTheButtonList() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        openGamepadEditor(harness, "nes")

        onNodeWithTag("mapping_output_$NES_A").performScrollTo().performClick()
        advanceQuick(harness)
        onNodeWithTag("binding_prompt").assertExists()

        onNodeWithTag("binding_cancel").performClick()
        advanceQuick(harness)

        // Back to the list; capture released.
        onNodeWithTag("binding_prompt").assertDoesNotExist()
        onNodeWithTag("mapping_output_$NES_A").assertExists()
        assert(!harness.gamepadPortManager.bindCaptureActive.value)
    }
}
