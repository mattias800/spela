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

    @Test
    fun scrollPositionPreservedOnBackNavigation() = runComposeUiTest {
        val harness = createHarnessWithManyConsoles()

        setContent { harness.App() }
        advance(harness)

        // Console 1 should be visible at the top
        onNodeWithText("Console 1").assertIsDisplayed()

        // Scroll down to Console 40 — far enough that Console 1 leaves
        // the viewport on any reasonable desktop test window.
        onNodeWithTag("consoles-list").performScrollToNode(hasText("Console 40"))
        advanceQuick(harness)

        // Console 1 should no longer be visible after scrolling
        onNodeWithText("Console 1").assertIsNotDisplayed()

        // Navigate to a console detail screen
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("console40"))
        )
        advance(harness)

        // Navigate back
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        // Console 1 should still NOT be visible — scroll position was preserved
        onNodeWithText("Console 1").assertIsNotDisplayed()
        // Console 40 should still be visible
        onNodeWithText("Console 40").assertIsDisplayed()
    }

    @Test
    fun scrollPositionResetsOnForwardNavigation() = runComposeUiTest {
        val harness = createHarnessWithManyConsoles()

        setContent { harness.App() }
        advance(harness)

        // Navigate forward to consoles — should start at the top
        onNodeWithText("Console 1").assertIsDisplayed()
    }
}
