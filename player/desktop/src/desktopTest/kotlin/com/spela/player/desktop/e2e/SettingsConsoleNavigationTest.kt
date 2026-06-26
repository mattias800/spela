package com.spela.player.desktop.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.spela.player.domain.model.ControllerStyle
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.viewmodel.GamepadConfigIntent
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

    private fun ComposeUiTest.openControlsCategory(harness: SpelaTestHarness) {
        onNodeWithContentDescription("Controls").performClick()
        advanceQuick(harness)
    }

    /**
     * The Controls category lists connected controllers (#1359). With none
     * connected, the heading shows plus an empty-state hint.
     */
    @Test
    fun controlsCategoryShowsEmptyControllerList() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)
        openControlsCategory(harness)

        onNodeWithContentDescription("Controllers heading").assertExists()
        onNodeWithTag("controller_list_empty").assertExists()
    }

    /**
     * A connected controller appears as a row in the list (#1359).
     */
    @Test
    fun controlsCategoryListsConnectedController() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gamepadPortManager.connectDevice(500, "Test Pad", ControllerStyle.Xbox)

        setContent { harness.App() }
        navigateToSettings(harness)
        openControlsCategory(harness)

        onNodeWithTag("controller_row_500").assertExists()
    }

    /**
     * The per-controller detail tester (#1355/#1359): with this controller under
     * test, a simulated press of a canonical position lights up its chip — and
     * only that position. Driven via GamepadPortManager (the same signal the
     * desktop poller / Android key dispatch feed on-device).
     */
    @Test
    fun controllerDetailInputTesterHighlightsPressedPosition() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gamepadPortManager.connectDevice(500, "Test Pad", ControllerStyle.Xbox)

        setContent { harness.App() }
        navigateToSettings(harness)
        openControlsCategory(harness)

        // Drill into the controller's detail — now a real navigation page (#1372).
        onNodeWithTag("controller_row_500").performClick()
        advanceQuick(harness)
        assertEquals(
            SpScreen.ControllerDetail(500),
            harness.navigationViewModel.state.value.currentScreen,
        )
        onNodeWithTag("controller_detail_title").assertExists()

        // On-device the tester captures when its element is focused; here we
        // activate it directly for the selected controller.
        harness.gamepadConfigViewModel.onIntent(GamepadConfigIntent.SetInputTestActive(500, true))
        harness.gamepadPortManager.reportPositionInput(500, GamepadPosition.SOUTH, pressed = true)
        advanceQuick(harness)

        // The position-label chips were removed (#1448); the schematic now shows
        // the pressed position. SOUTH lights up, EAST stays inactive.
        onNodeWithTag("schematic_SOUTH")
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Active"))
        onNodeWithTag("schematic_EAST")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Inactive"))
    }

    /**
     * Assigning a player number already held by another controller raises a
     * conflict prompt; confirming moves the slot and clears the old controller (#1359).
     */
    @Test
    fun assigningOccupiedSlotShowsConflictThenSwitches() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gamepadPortManager.connectDevice(500, "Pad A", ControllerStyle.Xbox) // P1
        harness.gamepadPortManager.connectDevice(600, "Pad B", ControllerStyle.PlayStation) // P2

        setContent { harness.App() }
        navigateToSettings(harness)
        openControlsCategory(harness)

        // In Pad B's detail (a real navigation page, #1372), try to take P1 (held by Pad A).
        onNodeWithTag("controller_row_600").performClick()
        advanceQuick(harness)
        assertEquals(
            SpScreen.ControllerDetail(600),
            harness.navigationViewModel.state.value.currentScreen,
        )
        onNodeWithTag("controller_detail_change_player").performScrollTo().performClick()
        advanceQuick(harness)
        onNodeWithTag("slot_chip_0").performClick()
        advanceQuick(harness)

        onNodeWithText("Switch player?").assertExists()
        onNodeWithTag("dialog_confirm").performClick()
        advanceQuick(harness)

        assertEquals(0, harness.gamepadPortManager.getPort(600))
        assertEquals(-1, harness.gamepadPortManager.getPort(500))
    }

    /**
     * Clearing a controller's player number unassigns it (#1359).
     */
    @Test
    fun clearingPlayerUnassignsController() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gamepadPortManager.connectDevice(500, "Pad A", ControllerStyle.Xbox) // P1

        setContent { harness.App() }
        navigateToSettings(harness)
        openControlsCategory(harness)

        onNodeWithTag("controller_row_500").performClick()
        advanceQuick(harness)
        assertEquals(
            SpScreen.ControllerDetail(500),
            harness.navigationViewModel.state.value.currentScreen,
        )
        onNodeWithTag("controller_detail_clear_player").performScrollTo().performClick()
        advanceQuick(harness)

        assertEquals(-1, harness.gamepadPortManager.getPort(500))
    }

    @Test
    fun backFromConsoleSettingsReturnsToPerConsoleNotGeneral() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)
        navigateToControlsCategory(harness)

        // Into a console's settings...
        onNodeWithText("Nintendo Entertainment System").performClick()
        advance(harness)
        assertEquals(
            SpScreen.ConsoleSettings("nes"),
            harness.navigationViewModel.state.value.currentScreen,
        )

        // ...then Back. We should return to the Per-Console list, not General.
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        assertEquals(
            SpScreen.Settings,
            harness.navigationViewModel.state.value.currentScreen,
        )
        // The console rows are only rendered in the Per-Console category content;
        // before the rememberSaveable fix this reset to General and the rows were gone.
        onNodeWithText("Nintendo Entertainment System").assertIsDisplayed()
    }

    /**
     * Back from a console's settings restores focus to that console row (#1382),
     * so D-pad navigation continues from there. Uses SNES (not the default first
     * console) to prove restoration, not just default focus.
     */
    @Test
    fun backFromConsoleSettingsRestoresFocusToThatConsole() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        navigateToSettings(harness)
        navigateToControlsCategory(harness)
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)

        // Give the SNES row focus (saves it to the content pane's focus memory),
        // then drill into its settings via the nav intent. We focus+navigate rather
        // than click because in gamepad mode the section-pill overlay can intercept
        // a pointer click on a scrolled-up row.
        onNodeWithTag("console_settings_row_snes").performScrollTo().requestFocus()
        advanceQuick(harness)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ConsoleSettings("snes")),
        )
        advance(harness)
        assertEquals(
            SpScreen.ConsoleSettings("snes"),
            harness.navigationViewModel.state.value.currentScreen,
        )

        // Back → focus restored to the SNES row.
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        onNodeWithTag("console_settings_row_snes").assertIsFocused()
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
