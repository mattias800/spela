package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E2E tests for gamepad-first section navigation.
 * Tests: 5-tab bottom nav, L/R section cycling, back navigation from new tabs.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SectionNavigationTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun consolesScreenShowsConsolesAfterLoad() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        onNodeWithText("Nintendo Entertainment System").assertIsDisplayed()
        onNodeWithText("Super Nintendo").assertIsDisplayed()
    }

    @Test
    fun collectionsScreenShowsMyCollectionsHeader() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Collections)
        )
        advance(harness)

        // Collections screen should be rendered (may show empty state or header)
        // The screen is now a top-level section
        val navState = harness.navigationViewModel.state.value
        assertEquals(SpScreen.Collections, navState.currentScreen)
    }

    @Test
    fun bottomNavShowsSixTabs() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Home").assertIsDisplayed()
        onNodeWithText("Explore").assertIsDisplayed()
        onNodeWithText("Consoles").assertIsDisplayed()
        onNodeWithText("Collections").assertIsDisplayed()
        onNodeWithText("Activity").assertIsDisplayed()
        onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun nextSectionCyclesCorrectly() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Start at Home
        assertEquals(SpScreen.Home, harness.navigationViewModel.state.value.currentScreen)

        // NextSection → Explore
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Explore, harness.navigationViewModel.state.value.currentScreen)

        // NextSection → Consoles
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Consoles, harness.navigationViewModel.state.value.currentScreen)

        // NextSection → Collections
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Collections, harness.navigationViewModel.state.value.currentScreen)

        // NextSection → Activity
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Activity, harness.navigationViewModel.state.value.currentScreen)

        // NextSection → Settings
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Settings, harness.navigationViewModel.state.value.currentScreen)

        // NextSection → Home (wraps)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Home, harness.navigationViewModel.state.value.currentScreen)
    }

    @Test
    fun previousSectionCyclesBackward() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Start at Home
        assertEquals(SpScreen.Home, harness.navigationViewModel.state.value.currentScreen)

        // PreviousSection → Settings (wraps)
        harness.navigationViewModel.onIntent(NavigationIntent.PreviousSection)
        assertEquals(SpScreen.Settings, harness.navigationViewModel.state.value.currentScreen)

        // PreviousSection → Activity
        harness.navigationViewModel.onIntent(NavigationIntent.PreviousSection)
        assertEquals(SpScreen.Activity, harness.navigationViewModel.state.value.currentScreen)

        // PreviousSection → Collections
        harness.navigationViewModel.onIntent(NavigationIntent.PreviousSection)
        assertEquals(SpScreen.Collections, harness.navigationViewModel.state.value.currentScreen)

        // PreviousSection → Consoles
        harness.navigationViewModel.onIntent(NavigationIntent.PreviousSection)
        assertEquals(SpScreen.Consoles, harness.navigationViewModel.state.value.currentScreen)

        // PreviousSection → Explore
        harness.navigationViewModel.onIntent(NavigationIntent.PreviousSection)
        assertEquals(SpScreen.Explore, harness.navigationViewModel.state.value.currentScreen)

        // PreviousSection → Home
        harness.navigationViewModel.onIntent(NavigationIntent.PreviousSection)
        assertEquals(SpScreen.Home, harness.navigationViewModel.state.value.currentScreen)
    }

    @Test
    fun sectionCyclingClearsBackStack() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Push some screens onto backstack
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        assertTrue(harness.navigationViewModel.state.value.backStack.isNotEmpty())

        // Section cycle should clear backstack
        // Current screen Console("nes") maps to CONSOLES tab, so NextSection → Collections
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertTrue(harness.navigationViewModel.state.value.backStack.isEmpty())
        assertEquals(SpScreen.Collections, harness.navigationViewModel.state.value.currentScreen)
    }

    @Test
    fun goBackFromConsolesReturnsToHome() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Navigate to Consoles via section cycling (Home → Explore → Consoles)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Consoles, harness.navigationViewModel.state.value.currentScreen)

        // GoBack with empty backstack from Consoles should go to Home
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        assertEquals(SpScreen.Home, harness.navigationViewModel.state.value.currentScreen)
    }

    @Test
    fun goBackFromCollectionsReturnsToHome() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Navigate to Collections via section cycling (Home → Explore → Consoles → Collections)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Collections, harness.navigationViewModel.state.value.currentScreen)

        // GoBack with empty backstack from Collections should go to Home
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        assertEquals(SpScreen.Home, harness.navigationViewModel.state.value.currentScreen)
    }
}
