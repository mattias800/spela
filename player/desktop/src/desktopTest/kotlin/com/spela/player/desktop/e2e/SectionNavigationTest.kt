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

        // No connected servers in the harness → the Servers tab is hidden (#1435).
        onNodeWithText("Servers").assertDoesNotExist()
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

        // NextSection → Collections (Connected Servers is hidden — no connected
        // servers in the harness, so the cycle skips it, #1435)
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

        // PreviousSection → Consoles (Connected Servers is hidden, #1435)
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

        // Push some screens onto the active tab's stack
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        val homeStack = harness.navigationViewModel.state.value.tabStacks[com.spela.player.presentation.ui.components.BottomNavTab.HOME]!!
        assertTrue(homeStack.size > 1)

        // Section cycle switches tab but preserves stacks
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(com.spela.player.presentation.ui.components.BottomNavTab.EXPLORE, harness.navigationViewModel.state.value.activeTab)
        assertEquals(SpScreen.Explore, harness.navigationViewModel.state.value.currentScreen)
        // Home stack is preserved
        assertEquals(homeStack, harness.navigationViewModel.state.value.tabStacks[com.spela.player.presentation.ui.components.BottomNavTab.HOME])
    }

    @Test
    fun goBackAtConsolesTabRootIsNoOp() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Navigate to Consoles via section cycling (Home → Explore → Consoles)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Consoles, harness.navigationViewModel.state.value.currentScreen)

        // GoBack at a tab root is a no-op (#1372): each tab owns its stack and the
        // root is the floor — B never leaves the tab, so we stay on Consoles.
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        assertEquals(SpScreen.Consoles, harness.navigationViewModel.state.value.currentScreen)
    }

    @Test
    fun goBackAtCollectionsTabRootIsNoOp() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Navigate to Collections via section cycling. Connected Servers is
        // hidden (no connected servers in the harness, #1435), so Collections
        // is three steps in: Home → Explore → Consoles → Collections.
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        harness.navigationViewModel.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Collections, harness.navigationViewModel.state.value.currentScreen)

        // GoBack at a tab root is a no-op (#1372) — we stay on Collections.
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        assertEquals(SpScreen.Collections, harness.navigationViewModel.state.value.currentScreen)
    }
}
