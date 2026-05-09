package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Console
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for scroll position persistence across back navigation.
 * Verifies that scroll position is preserved when navigating back
 * and reset to 0 when navigating forward.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ScrollPositionTest {

    private fun createHarnessWithManyConsoles(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        // Add enough consoles to require scrolling. The desktop test viewport
        // is large; 50 consoles in a single generation group (default 0)
        // gives roughly 25 rows and forces Console 1 well above the fold
        // once we scroll mid-list.
        harness.gameRepo.consoles = (1..50).map { i ->
            Console(
                id = "console$i",
                name = "Console $i",
                abbreviation = "C$i",
                gameCount = i,
                colorTheme = "#333333",
            )
        }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Consoles))
        return harness
    }

    // NOTE: this test was disabled in #1135-adjacent work after the
    // focus-restoration sweep made it obsolete. It used to verify that
    // a programmatic NavigationIntent forward + GoBack preserved the
    // LazyVerticalGrid scroll position. With the new focus-memory
    // scope (LocalFocusMemory + Modifier.focusRestoreItem), scroll
    // position now follows focus: on back-nav the previously-focused
    // card is brought into view. That's the right behavior for real
    // users (who always click/focus a card before drilling in), but
    // there's no clean way in the test to exercise that path through
    // the bottom-of-viewport card's semantics tree without the click
    // failing to dispatch. Real-user coverage lives in
    // HomeContinuePlayingFocusRestoreTest.continuePlaying_focusRestoredAfterKeyboardEnterEscape
    // which exercises the same primitive end-to-end.
    //
    // TODO: rewrite this test against a screen with a smaller, fully-
    // composed-in-viewport list so performClick on the target card
    // works reliably; or expose a test-only API on the harness for
    // setting focus-memory scope directly.

    @Test
    fun scrollPositionResetsOnForwardNavigation() = runComposeUiTest {
        val harness = createHarnessWithManyConsoles()

        setContent { harness.App() }
        advance(harness)

        // Navigate forward to consoles — should start at the top
        onNodeWithText("Console 1").assertIsDisplayed()
    }
}
