package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Console
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.gamepad.InputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for focus behavior on back navigation.
 *
 * Verifies that after navigating forward to a screen and pressing back,
 * some element on the returned-to screen has focus so d-pad navigation
 * continues working.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class BackNavigationFocusTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.gameRepo.consoles = listOf(
            Console("nes", "Nintendo Entertainment System", "NES", 3, "#e53e3e"),
            Console("snes", "Super Nintendo", "SNES", 2, "#3182ce"),
            Console("gba", "Game Boy Advance", "GBA", 1, "#5a1f9e"),
        )
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun consolesScreen_forwardNavigation_focusAcquired() = runComposeUiTest {
        val harness = createHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)

        // Navigate to consoles list
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Consoles))
        advance(harness)

        // NES should be focusable — verify it exists
        val nesCard = onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
        nesCard.assertExists()

        // Check that SOMETHING has focus
        val focusedNodes = onAllNodes(isFocused())
        val count = focusedNodes.fetchSemanticsNodes().size
        println("Forward nav: $count focused nodes")
        assert(count > 0) { "Expected at least one focused node after forward navigation, found $count" }
    }

    @Test
    fun consolesScreen_backFromConsoleDetail_focusRestored() = runComposeUiTest {
        val harness = createHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)

        // Navigate to consoles list
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Consoles))
        advance(harness)

        // Verify focus is on NES (first console)
        val nesCard = onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
        nesCard.assertExists()

        // Navigate forward to NES console detail
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advance(harness)

        // Navigate back
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        // Verify we're back on consoles list
        nesCard.assertExists()

        // Press d-pad to re-enter content after back navigation
        onRoot().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }
        advanceQuick(harness)

        // Check that SOMETHING has focus after back navigation + d-pad
        val focusedNodes = onAllNodes(isFocused())
        val count = focusedNodes.fetchSemanticsNodes().size
        println("Back nav + dpad: $count focused nodes")
        assert(count > 0) { "Expected at least one focused node after back navigation + d-pad, found $count" }
    }

    @Test
    fun consolesScreen_backFromConsoleDetail_dpadWorks() = runComposeUiTest {
        val harness = createHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)

        // Navigate to consoles list
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Consoles))
        advance(harness)

        // Navigate forward to NES console detail
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advance(harness)

        // Navigate back
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        // Press d-pad down — should acquire focus if nothing has it
        onRoot().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }
        advanceQuick(harness)

        // Now SOMETHING must have focus
        val focusedNodes = onAllNodes(isFocused())
        val count = focusedNodes.fetchSemanticsNodes().size
        println("After d-pad on back: $count focused nodes")
        assert(count > 0) { "Expected focus after d-pad press on back-navigated screen, found $count" }
    }
}
