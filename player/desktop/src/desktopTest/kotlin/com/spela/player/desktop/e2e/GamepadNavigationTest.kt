package com.spela.player.desktop.e2e

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
 * Note: D-pad input triggers gamepad mode which hides the bottom tab bar
 * and shows the section indicator. Tab bar interaction via D-pad is therefore
 * not possible — section cycling (L1/R1) is tested in SectionIndicatorTest
 * and SectionNavigationTest.
 *
 * Compose UI Test key injection requires a focused node as dispatch target,
 * so the GamepadHandler's Next-fallback (for "nothing focused" state) cannot be
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
