package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for scroll position restoration on back navigation.
 * When navigating forward then back, the scroll position should be
 * preserved so the user returns to where they were.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ScrollRestorationTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun consoleListScrollPositionRestoredOnBack() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Navigate to console list (10 consoles — requires scrolling)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        // NES should be visible at the top
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
            .assertIsDisplayed()

        // Scroll to Sega Saturn (last item, definitely off-screen)
        val saturnCard = onNodeWithContentDescription("Sega Saturn, 1 games")
        saturnCard.performScrollTo()
        advanceQuick(harness)
        saturnCard.assertIsDisplayed()

        // NES should now be off-screen
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
            .assertIsNotDisplayed()

        // Navigate forward to Saturn console detail
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("sat"))
        )
        advance(harness)

        // Navigate back
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        // Saturn should still be visible (scroll position restored)
        onNodeWithContentDescription("Sega Saturn, 1 games")
            .assertIsDisplayed()
    }
}
