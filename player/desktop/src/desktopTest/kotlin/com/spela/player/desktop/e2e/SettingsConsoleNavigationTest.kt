package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.SettingsIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the console settings navigation refactor.
 *
 * Verifies:
 * - SettingsScreen shows a "Console settings" section with a list of consoles
 * - Clicking a console navigates to ConsoleSettingsScreen
 * - Video Filter section has no per-console scope tab
 * - Controls section has no per-console scope tab
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SettingsConsoleNavigationTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToSettings(harness: SpelaTestHarness) {
        // Pre-trigger LoadSettings so consoles are loaded before navigation;
        // the LaunchedEffect in SettingsScreen also calls it, but this ensures
        // the data is available on first composition.
        harness.settingsViewModel.onIntent(SettingsIntent.LoadSettings)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Settings)
        )
        advanceFully(harness)
    }

    @Test
    fun settingsScreenShowsConsoleSettingsSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Verify consoles were loaded in the ViewModel state
        val consoles = harness.settingsViewModel.state.value.consoles
        assertEquals(2, consoles.size, "Should have 2 consoles loaded")

        // Scroll to the first console row — if it's in the list, the section exists
        onNodeWithTag("settings_list")
            .performScrollToKey("console_nes")
        onNodeWithText("Nintendo Entertainment System").assertIsDisplayed()
    }

    @Test
    fun consoleSettingsSectionListsAllConsoles() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Scroll to console items — the fake game repo has NES and SNES
        onNodeWithTag("settings_list")
            .performScrollToKey("console_nes")
        onNodeWithText("Nintendo Entertainment System").assertIsDisplayed()

        onNodeWithTag("settings_list")
            .performScrollToKey("console_snes")
        onNodeWithText("Super Nintendo").assertIsDisplayed()
    }

    @Test
    fun clickingConsoleNavigatesToConsoleSettings() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Scroll to and click a console
        onNodeWithTag("settings_list")
            .performScrollToKey("console_nes")
        onNodeWithText("Nintendo Entertainment System").performClick()
        advance(harness)

        // Should navigate to ConsoleSettingsScreen — verify by checking
        // the navigation state shows ConsoleSettings
        val currentScreen = harness.navigationViewModel.state.value.currentScreen
        assertEquals(
            SpScreen.ConsoleSettings("nes"),
            currentScreen,
            "Clicking NES should navigate to ConsoleSettings(\"nes\")",
        )
    }

    @Test
    fun clickingSnesConsoleNavigatesToSnesConsoleSettings() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        onNodeWithTag("settings_list")
            .performScrollToKey("console_snes")
        onNodeWithText("Super Nintendo").performClick()
        advance(harness)

        val currentScreen = harness.navigationViewModel.state.value.currentScreen
        assertEquals(
            SpScreen.ConsoleSettings("snes"),
            currentScreen,
            "Clicking SNES should navigate to ConsoleSettings(\"snes\")",
        )
    }

    @Test
    fun videoFilterSectionHasNoPerConsoleTab() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Scroll to Video Filter section
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Video Filter"))
        onNodeWithText("Video Filter").assertIsDisplayed()

        // There should be no scope tabs — "Default" tab or "Per Console" tab
        onAllNodesWithText("Default").assertCountEquals(0)
        onAllNodesWithText("Per Console").assertCountEquals(0)
        onAllNodesWithText("PER_CONSOLE").assertCountEquals(0)
        onAllNodesWithText("Per-Console").assertCountEquals(0)
    }

    @Test
    fun controlsSectionHasNoPerConsoleTab() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Scroll to Controls section
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Controls"))
        onNodeWithText("Controls").assertIsDisplayed()

        // No scope tabs should be present for controls
        // (The "Default" text might exist for other reasons like the theme,
        //  so we check specifically for scope-tab-like nodes)
        onAllNodesWithText("Per Console").assertCountEquals(0)
        onAllNodesWithText("PER_CONSOLE").assertCountEquals(0)
        onAllNodesWithText("Per-Console").assertCountEquals(0)
    }

    @Test
    fun consoleSettingsRowsHaveArrowIcon() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Scroll to NES console row
        onNodeWithTag("settings_list")
            .performScrollToKey("console_nes")

        // Each console row has an arrow icon with content description
        onNodeWithContentDescription("Open Nintendo Entertainment System settings")
            .assertExists()
    }
}
