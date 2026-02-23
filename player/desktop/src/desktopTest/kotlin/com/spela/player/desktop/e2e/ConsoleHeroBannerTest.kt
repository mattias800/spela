package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the console hero banner on the ConsoleScreen.
 * Verifies the banner displays console name, game count, and metadata badges.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ConsoleHeroBannerTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun heroBannerShowsConsoleNameAndGameCount() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        // Navigate to NES console screen
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("nes"))
        )
        advance(harness)

        // Hero banner should show game count
        onNodeWithText("3 games", substring = true).assertIsDisplayed()
    }

    @Test
    fun heroBannerShowsSaveStatesBadge() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        // Navigate to NES console (saveStateSupport = true by default)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("nes"))
        )
        advance(harness)

        // Save states badge should be visible
        onNodeWithText("Save states").assertIsDisplayed()
    }

    @Test
    fun heroBannerAccessibilityDescription() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        // Navigate to NES console screen
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("nes"))
        )
        advance(harness)

        // Hero banner should have accessibility description with console name and game count
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
            .assertIsDisplayed()
    }
}
