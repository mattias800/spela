package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the desktop positional gamepad mapping editor (#1334, Phase 3c).
 *
 * Verifies the brand-neutral position→action editor: it opens from console
 * settings, shows the default mapping, lets the user reassign a position to a
 * different console action, and resets to defaults — all positional, no brand
 * glyphs.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GamepadMappingTest {

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
    fun editorShowsDefaultMappingPositionally() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        openGamepadEditor(harness, "nes")

        // NES default: bottom button = B, right button = A. Brand-neutral labels.
        // Rows are clickable (semantics merged), so query the value Text unmerged.
        onNodeWithTag("gamepad_action_SOUTH", useUnmergedTree = true).assertTextEquals("B")
        onNodeWithTag("gamepad_action_EAST", useUnmergedTree = true).assertTextEquals("A")
        // West has no NES action by default.
        onNodeWithTag("gamepad_action_WEST", useUnmergedTree = true).assertTextEquals("—")
    }

    @Test
    fun reassigningAPositionPersistsInTheRow() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        openGamepadEditor(harness, "nes")

        // Guiding example: make the bottom button act as NES A.
        onNodeWithTag("gamepad_pos_SOUTH").performScrollTo().performClick()
        advance(harness)
        onNodeWithTag("gamepad_action_picker").assertExists()
        // The picker's "A" option sets a contentDescription; the row value
        // Texts don't, so this targets the option unambiguously.
        onNodeWithContentDescription("A").performClick()
        advance(harness)

        onNodeWithTag("gamepad_action_picker").assertDoesNotExist()
        onNodeWithTag("gamepad_action_SOUTH", useUnmergedTree = true).assertTextEquals("A")
    }

    @Test
    fun resetToDefaultsRestoresMapping() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        openGamepadEditor(harness, "nes")

        // Reassign, then reset.
        onNodeWithTag("gamepad_pos_SOUTH").performScrollTo().performClick()
        advance(harness)
        onNodeWithContentDescription("A").performClick()
        advance(harness)
        onNodeWithTag("gamepad_action_SOUTH", useUnmergedTree = true).assertTextEquals("A")

        onNodeWithTag("gamepad_mapping_reset").performClick()
        advance(harness)
        onNodeWithTag("gamepad_action_SOUTH", useUnmergedTree = true).assertTextEquals("B")
    }
}
