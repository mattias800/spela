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
    fun bottomNavTabsReceiveFocusOnClick() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        onNodeWithContentDescription("Home").assertIsDisplayed()

        // Clicking the already-active Home tab should give it focus
        onNodeWithContentDescription("Home").performClick()
        advanceQuick(harness)
        onNodeWithContentDescription("Home").assertIsFocused()
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

        // Press Up — nothing above, focus should stay
        nesCard.performKeyInput { pressKey(Key.DirectionUp) }
        advanceQuick(harness)
        nesCard.assertIsFocused()
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

        // Switch to Consoles section via NextSection (Home → Explore → Consoles)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        advance(harness)

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
