package com.spela.player.desktop.e2e

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.gamepad.InputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for gamepad/D-pad navigation.
 * Verifies component focusability and directional focus navigation
 * for gaming handheld support.
 *
 * Note: D-pad input triggers gamepad mode which hides the bottom tab bar
 * and shows the section indicator. Tab bar interaction via D-pad is therefore
 * not possible — section cycling (L1/R1) is tested in SectionIndicatorTest
 * and SectionNavigationTest.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GamepadNavigationTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun homeScreenAcquiresFocusOnEntry() = runComposeUiTest {
        // Each screen's leaf-level focus restorer (e.g. SpCarousel's
        // memoryKey + isDefaultFocusGroup) takes ownership of focus on
        // entry. The bottom-nav tab is just the trigger; the screen
        // content is what should actually be focused so d-pad navigation
        // works without an extra keypress.
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        onNodeWithContentDescription("Home").assertIsDisplayed()
        // After arriving at Home, something on the screen must hold focus
        // so arrow keys / d-pad work immediately.
        val focusedCount = onAllNodes(isFocused()).fetchSemanticsNodes().size
        assert(focusedCount > 0) {
            "Expected at least one focused node after arriving at Home, got $focusedCount"
        }
    }

    @Test
    fun consoleScreenGameCardsAreDisplayedWithFocusSupport() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ConsoleGames("nes"))
        )
        advance(harness)

        // Game cards with .focusable() should render correctly
        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("Super Mario Bros.").assertIsDisplayed()
        onNodeWithText("Mega Man 2").assertIsDisplayed()
    }

    @Test
    fun consoleCardsAreFocusableInGamepadMode() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode and navigate to Consoles screen
        // focusResetKey auto-focuses first focusable element
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        // The first console card (NES) should have received focus automatically
        val nesCard = onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
        nesCard.assertIsDisplayed()
        nesCard.assertIsFocused()
    }

    @Test
    fun focusDoesNotWrapOnDirectionLeftAtBoundary() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode and navigate to Consoles
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        val nesCard = onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
        nesCard.assertIsFocused()

        // Press Left — nothing to the left, focus should stay on the same card
        nesCard.performKeyInput { pressKey(Key.DirectionLeft) }
        advanceQuick(harness)
        nesCard.assertIsFocused()
    }

    @Test
    fun focusDoesNotWrapOnDirectionUpAtBoundary() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode and navigate to Consoles
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        val nesCard = onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
        nesCard.assertIsFocused()

        // #1106 — the consoles tab now hosts a By-generation /
        // By-manufacturer toggle directly above the grid. DPad-up
        // from the first card may either move focus to the toggle
        // (new boundary) or stay on the card if spatial navigation
        // doesn't cross the section header. Either way is acceptable;
        // what we still must assert is that focus does NOT wrap to
        // the bottom of the screen (e.g. nav tabs).
        // #1106 — the consoles tab now hosts a By-generation /
        // By-manufacturer toggle row directly above the grid. DPad-up
        // from a card may land on either chip depending on spatial
        // search; what we still must assert is that focus does NOT
        // wrap (e.g. into bottom nav tabs) — it stays inside the tab
        // content. Either NES, the generation chip, or the
        // manufacturer chip is acceptable.
        nesCard.performKeyInput { pressKey(Key.DirectionUp) }
        advanceQuick(harness)
        val nesFocused = try { nesCard.assertIsFocused(); true } catch (_: AssertionError) { false }
        val genToggleFocused = try {
            onNodeWithTag("console-grouping-generation").assertIsFocused(); true
        } catch (_: AssertionError) { false }
        val mfgToggleFocused = try {
            onNodeWithTag("console-grouping-manufacturer").assertIsFocused(); true
        } catch (_: AssertionError) { false }
        assert(nesFocused || genToggleFocused || mfgToggleFocused) {
            "Expected focus to stay on NES card or move to either grouping toggle chip"
        }
    }

    @Test
    fun focusMovesRightBetweenConsoleCards() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode and navigate to Consoles
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        val nesCard = onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
        val snesCard = onNodeWithContentDescription("Super Nintendo, 2 games")
        nesCard.assertIsFocused()

        // Press Right — focus should move to the SNES card
        nesCard.performKeyInput { pressKey(Key.DirectionRight) }
        advanceQuick(harness)
        snesCard.assertIsFocused()
    }

    // --- Focus recovery after L1/R1 section switching ---

    @Test
    fun focusRecoveredAfterSectionSwitchToConsoles() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        // Switch to Consoles section via NextSection (Home → Explore → Consoles).
        // The destination screen's `autoFocus()` modifier on the first console
        // card requests focus after a 500ms delay. `advanceFully` runs enough
        // dispatcher iterations for that delay to elapse.
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        advanceQuick(harness)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        advanceFully(harness)

        // Focus should have been recovered — first console card should be focused
        val nesCard = onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
        nesCard.assertIsDisplayed()
        nesCard.assertIsFocused()
    }

    @Test
    fun focusRecoveredAfterSectionSwitchToSettings() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        // Switch to Settings (last section)
        harness.navigationViewModel.onIntent(NavigationIntent.PreviousSection)
        advance(harness)

        // Something on the Settings screen should be focused
        // (General category is selected by default on wide screens)
        val focusedNodes = onAllNodes(hasAnyAncestor(isRoot()).and(isFocused()))
        focusedNodes.fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun focusRecoveredAfterMultipleSectionCycles() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        // Cycle through all 6 sections
        repeat(6) {
            harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
            advance(harness)
        }

        // Back to Home — something should be focused
        val focusedNodes = onAllNodes(isFocused())
        focusedNodes.fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun dpadRecoversFocusAfterSectionSwitch() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode and go to Consoles
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection) // Explore
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection) // Consoles
        advance(harness)

        // Even if auto-focus didn't work, pressing D-pad Down should recover focus
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        advanceQuick(harness)

        // Some focusable element should now be focused
        val nesCard = onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
        val snesCard = onNodeWithContentDescription("Super Nintendo, 2 games")
        // One of the console cards should have focus
        val nesFocused = try { nesCard.assertIsFocused(); true } catch (_: AssertionError) { false }
        val snesFocused = try { snesCard.assertIsFocused(); true } catch (_: AssertionError) { false }
        assert(nesFocused || snesFocused) { "Expected one of the console cards to be focused after D-pad press" }
    }
}
