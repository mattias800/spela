package com.spela.player.desktop.e2e

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for gamepad/D-pad navigation.
 * Verifies component focusability and directional focus navigation
 * for gaming handheld support.
 *
 * Note: Compose UI Test key injection requires a focused node as dispatch target,
 * so the GamepadHandler's Enter-fallback (for "nothing focused" state) cannot be
 * tested here. That path is covered by manual device testing on the Ayn Thor.
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
    fun dpadRightMovesFocusBetweenBottomNavTabs() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Establish focus on Home tab
        onNodeWithContentDescription("Home").performClick()
        advanceQuick(harness)
        onNodeWithContentDescription("Home").assertIsFocused()

        // D-pad Right → focus should move to Consoles tab
        onNodeWithContentDescription("Home").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        advanceQuick(harness)
        onNodeWithContentDescription("Consoles").assertIsFocused()
    }

    @Test
    fun dpadLeftMovesFocusBackBetweenBottomNavTabs() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Focus on Consoles tab
        onNodeWithContentDescription("Consoles").performClick()
        advanceQuick(harness)
        onNodeWithContentDescription("Consoles").assertIsFocused()

        // D-pad Left → focus should move back to Home tab
        onNodeWithContentDescription("Consoles").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        advanceQuick(harness)
        onNodeWithContentDescription("Home").assertIsFocused()
    }

    @Test
    fun dpadDownFromBottomNavMovesFocusUp() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Focus on Home tab, then press Up to move focus into the content area
        onNodeWithContentDescription("Home").performClick()
        advanceQuick(harness)
        onNodeWithContentDescription("Home").assertIsFocused()

        // Press Up — should move focus away from bottom nav into screen content
        onNodeWithContentDescription("Home").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        advanceQuick(harness)

        // Home tab should no longer be focused (focus moved to content)
        onNodeWithContentDescription("Home").assertIsNotFocused()
    }

    @Test
    fun consoleScreenGameCardsAreDisplayedWithFocusSupport() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("nes"))
        )
        advance(harness)

        // Game cards with .focusable() should render correctly
        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("Super Mario Bros.").assertIsDisplayed()
        onNodeWithText("Mega Man 2").assertIsDisplayed()
    }
}
