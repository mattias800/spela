package com.spela.player.desktop.e2e

import androidx.compose.ui.input.key.Key
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

    /** Activates the input tester (#1448): a tap (or confirm) toggles capture on. */
    private fun ComposeUiTest.activateInputTester(harness: SpelaTestHarness) {
        onNodeWithTag("input_tester").performScrollTo().performClick()
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

        // The tester activates on a tap/confirm (#1448): start capturing for the
        // device under test, then feed a press.
        activateInputTester(harness)
        harness.gamepadPortManager.reportPositionInput(500, GamepadPosition.SOUTH, pressed = true)
        advanceQuick(harness)

        // The schematic shows the pressed position. SOUTH lights up, EAST stays
        // inactive. The tester is one clickable node now (#1448), so the per-pip
        // semantics live in the unmerged tree.
        onNodeWithTag("schematic_SOUTH", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Active"))
        onNodeWithTag("schematic_EAST", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Inactive"))

        // Once active, the D-pad is captured (not used to navigate away) so it can
        // be tested too (#1448): a press lights its chip.
        harness.gamepadPortManager.reportPositionInput(500, GamepadPosition.DPAD_UP, pressed = true)
        advanceQuick(harness)
        onNodeWithTag("schematic_DPAD_UP", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Active"))

        // Analog stick deflection lights the matching stick well (#1448): pushing
        // the left stick activates L3's indicator while R3 stays at rest.
        harness.gamepadPortManager.reportTestSticks(500, leftX = 1f, leftY = 0f, rightX = 0f, rightY = 0f)
        advanceQuick(harness)
        onNodeWithTag("schematic_L3", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Active"))
        onNodeWithTag("schematic_R3", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Inactive"))
    }

    /**
     * The tester stops only after the confirm button is *held* for the full
     * duration and then released (#1448): a brief press keeps it running (so the
     * confirm button is testable and an accidental press doesn't exit), while a
     * full hold followed by release ends capture.
     */
    @Test
    fun controllerDetailTesterStopsOnlyAfterHoldingConfirm() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gamepadPortManager.connectDevice(500, "Test Pad", ControllerStyle.Xbox)

        setContent { harness.App() }
        navigateToSettings(harness)
        openControlsCategory(harness)
        onNodeWithTag("controller_row_500").performClick()
        advanceQuick(harness)

        // Activate the tester (focus + confirm), so the confirm button is captured.
        activateInputTester(harness)
        assertEquals(500, harness.gamepadPortManager.testCaptureDeviceId.value)

        // Fine-grained clock control so the 2s hold doesn't auto-complete.
        fun settle(ms: Long) {
            mainClock.autoAdvance = false
            harness.testDispatcher.scheduler.runCurrent()
            mainClock.advanceTimeBy(ms)
            harness.testDispatcher.scheduler.runCurrent()
            waitForIdle()
        }

        // A brief hold then release (well under 2s) must NOT stop the tester.
        harness.gamepadPortManager.reportTestConfirmHeld(500, held = true)
        settle(300)
        harness.gamepadPortManager.reportTestConfirmHeld(500, held = false)
        settle(100)
        assertEquals(
            500,
            harness.gamepadPortManager.testCaptureDeviceId.value,
            "A brief confirm press must not stop the tester",
        )

        // Holding past the full duration then releasing stops it.
        harness.gamepadPortManager.reportTestConfirmHeld(500, held = true)
        settle(2_400)
        // Still active before release — stopping happens on release after a full hold.
        assertEquals(500, harness.gamepadPortManager.testCaptureDeviceId.value)
        harness.gamepadPortManager.reportTestConfirmHeld(500, held = false)
        advanceQuick(harness)
        assertEquals(
            null,
            harness.gamepadPortManager.testCaptureDeviceId.value,
            "Releasing after a full hold stops the tester",
        )
    }

    /**
     * Gamepad-only activation (#1448): with the tester focused, a confirm-button
     * press (resolved to Enter / DPAD center by the convention layer) activates it —
     * handled by the tester's key handler, since the clickable's keyboard activation
     * is suppressed in touch input mode.
     */
    @Test
    fun controllerDetailTesterActivatesOnConfirmKey() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gamepadPortManager.connectDevice(500, "Test Pad", ControllerStyle.Xbox)

        setContent { harness.App() }
        navigateToSettings(harness)
        openControlsCategory(harness)
        onNodeWithTag("controller_row_500").performClick()
        advanceQuick(harness)

        onNodeWithTag("input_tester").performScrollTo().requestFocus()
        advanceQuick(harness)
        onNodeWithTag("input_tester").performKeyInput { pressKey(Key.Enter) }
        advanceQuick(harness)
        assertEquals(
            500,
            harness.gamepadPortManager.testCaptureDeviceId.value,
            "Confirm press on the focused tester activates it",
        )
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
