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
 * E2E tests for the console settings navigation in Settings > Controls.
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
        harness.settingsViewModel.onIntent(SettingsIntent.LoadSettings)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Settings)
        )
        advanceFully(harness)
    }

    private fun ComposeUiTest.navigateToControlsCategory(harness: SpelaTestHarness) {
        // The per-console list lives under the "Per-Console" category,
        // not "Controls" — it was moved when the settings screen was
        // split into categorised sub-pages.
        onNodeWithContentDescription("Per-Console").performClick()
        advanceQuick(harness)
    }

    @Test
    fun settingsScreenShowsConsoleSettingsSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)
        navigateToControlsCategory(harness)

        val consoles = harness.settingsViewModel.state.value.consoles
        assertEquals(2, consoles.size, "Should have 2 consoles loaded")

        onNodeWithText("Nintendo Entertainment System").assertIsDisplayed()
    }

    @Test
    fun consoleSettingsSectionListsAllConsoles() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)
        navigateToControlsCategory(harness)

        onNodeWithText("Nintendo Entertainment System").assertIsDisplayed()
        onNodeWithText("Super Nintendo").assertIsDisplayed()
    }

    @Test
    fun clickingConsoleNavigatesToConsoleSettings() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)
        navigateToControlsCategory(harness)

        onNodeWithText("Nintendo Entertainment System").performClick()
        advance(harness)

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
        navigateToControlsCategory(harness)

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
    fun videoFilterSectionUnderEmulation() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Video Filter is now under Emulation category
        onNodeWithContentDescription("Emulation").performClick()
        advanceQuick(harness)

        onNodeWithText("Video Filter").assertIsDisplayed()

        // No scope tabs
        onAllNodesWithText("Per Console").assertCountEquals(0)
        onAllNodesWithText("PER_CONSOLE").assertCountEquals(0)
    }

    @Test
    fun controlsSectionHasNoPerConsoleTab() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)
        navigateToControlsCategory(harness)

        onAllNodesWithText("Per Console").assertCountEquals(0)
        onAllNodesWithText("PER_CONSOLE").assertCountEquals(0)
    }

    @Test
    fun consoleSettingsRowsHaveArrowIcon() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)
        navigateToControlsCategory(harness)

        onNodeWithContentDescription("Open Nintendo Entertainment System settings")
            .assertExists()
    }
}
