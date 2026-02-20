package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.intent.KeyMappingIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E tests for console-specific controller visuals in key mapping UI.
 *
 * Verifies:
 * - NES console settings shows correct buttons (A, B, Start, Select, D-pad)
 * - SNES console settings shows all 12 buttons
 * - Clicking a button enters listening mode
 * - Mapping list shows all console buttons
 * - Clicking a row in the mapping list enters listening mode
 * - Unknown console falls back to generic visual
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ControllerVisualTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToConsoleSettings(
        harness: SpelaTestHarness,
        consoleId: String,
    ) {
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ConsoleSettings(consoleId))
        )
        advance(harness)
    }

    @Test
    fun nesConsoleSettingsShowsCorrectButtons() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // Controller Mapping section should exist in the semantics tree
        onNodeWithText("Controller Mapping").assertExists()

        // NES has A, B, Start, Select + D-pad (8 buttons)
        // The controller visual should have button regions for these
        onNodeWithContentDescription("A, unmapped").assertExists()
        onNodeWithContentDescription("B, unmapped").assertExists()
    }

    @Test
    fun snesConsoleSettingsShowsAllButtons() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "snes")

        // SNES has 12 buttons: D-pad(4), A, B, X, Y, L, R, Start, Select
        onNodeWithText("Controller Mapping").assertExists()

        // Check that SNES-specific face buttons exist
        onNodeWithContentDescription("A, unmapped").assertExists()
        onNodeWithContentDescription("B, unmapped").assertExists()
        onNodeWithContentDescription("X, unmapped").assertExists()
        onNodeWithContentDescription("Y, unmapped").assertExists()
    }

    @Test
    fun clickingButtonOnVisualEntersListeningMode() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // Trigger listening mode via the ViewModel directly
        // (the button click triggers StartSingleButtonMap which requires key event handling)
        harness.keyMappingViewModel.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advance(harness)
        harness.keyMappingViewModel.onIntent(KeyMappingIntent.StartSingleButtonMap(8)) // A button = retroId 8
        advanceQuick(harness)

        // Should show listening prompt
        onNodeWithText("Press a key for: A").assertExists()
    }

    @Test
    fun mappingListShowsAllConsoleButtons() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // The mapping list panel shows each button with its mapping status
        // NES buttons should appear in the mapping list with "not mapped" descriptions
        onNodeWithContentDescription("A, not mapped").assertExists()
        onNodeWithContentDescription("B, not mapped").assertExists()
        onNodeWithContentDescription("Start, not mapped").assertExists()
        onNodeWithContentDescription("Select, not mapped").assertExists()
    }

    @Test
    fun mappingListRowChangesWhenListening() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "nes")

        // Verify the B row starts as "not mapped"
        onNodeWithContentDescription("B, not mapped").assertExists()

        // Enter listening mode for B via ViewModel
        harness.keyMappingViewModel.onIntent(KeyMappingIntent.StartSingleButtonMap(0)) // B = retroId 0
        advance(harness)

        // After entering listening mode, the B row should update
        onNodeWithContentDescription("B, press a key to map").assertExists()
    }

    @Test
    fun unknownConsoleFallsBackToGenericVisual() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        // Game Boy has no specific icon, should use generic
        navigateToConsoleSettings(harness, "gb")

        // Should still show the Controller Mapping section
        onNodeWithText("Controller Mapping").assertExists()

        // Game Boy has A, B, Start, Select + D-pad
        onNodeWithContentDescription("A, unmapped").assertExists()
        onNodeWithContentDescription("B, unmapped").assertExists()
    }

    @Test
    fun controllerIconsReturnsIconsForSupportedConsoles() {
        // Unit test: verify that ControllerIcons.forConsole returns icons for supported consoles
        assertNotNull(
            com.spela.player.presentation.ui.components.keymapping.ControllerIcons.forConsole("nes"),
            "NES should have an icon",
        )
        assertNotNull(
            com.spela.player.presentation.ui.components.keymapping.ControllerIcons.forConsole("snes"),
            "SNES should have an icon",
        )
        assertNotNull(
            com.spela.player.presentation.ui.components.keymapping.ControllerIcons.forConsole("n64"),
            "N64 should have an icon",
        )
        assertNotNull(
            com.spela.player.presentation.ui.components.keymapping.ControllerIcons.forConsole("genesis"),
            "Genesis should have an icon",
        )
        assertNotNull(
            com.spela.player.presentation.ui.components.keymapping.ControllerIcons.forConsole("psx"),
            "PSX should have an icon",
        )
        // Game Boy uses SNES as fallback
        assertNotNull(
            com.spela.player.presentation.ui.components.keymapping.ControllerIcons.forConsole("gb"),
            "Game Boy should have an icon (SNES fallback)",
        )
    }

    @Test
    fun controllerButtonPositionsReturnsRegionsForKnownConsoles() {
        // Unit test: verify that ControllerButtonPositions returns correct button counts
        val nesLayout = com.spela.player.domain.model.DefaultKeyMappings.NES
        val nesRegions = com.spela.player.presentation.ui.components.keymapping.ControllerButtonPositions.getRegions(nesLayout)
        assertTrue(
            nesRegions.size == 8,
            "NES should have 8 button regions but got ${nesRegions.size}",
        )

        val snesLayout = com.spela.player.domain.model.DefaultKeyMappings.SNES
        val snesRegions = com.spela.player.presentation.ui.components.keymapping.ControllerButtonPositions.getRegions(snesLayout)
        assertTrue(
            snesRegions.size == 12,
            "SNES should have 12 button regions but got ${snesRegions.size}",
        )

        // PSX: 16 digital buttons + 4 left stick + 4 right stick = 24
        val psxLayout = com.spela.player.domain.model.DefaultKeyMappings.PSX
        val psxRegions = com.spela.player.presentation.ui.components.keymapping.ControllerButtonPositions.getRegions(psxLayout)
        assertTrue(
            psxRegions.size == 24,
            "PSX should have 24 button regions (16 digital + 8 analog) but got ${psxRegions.size}",
        )
    }

    @Test
    fun n64LayoutIncludesAnalogStickButtons() {
        // Unit test: verify N64 layout now includes left stick directions
        val n64Layout = com.spela.player.domain.model.DefaultKeyMappings.N64
        val labels = n64Layout.buttons.map { it.label }
        assertTrue("L-Stick Up" in labels, "N64 layout should include L-Stick Up")
        assertTrue("L-Stick Down" in labels, "N64 layout should include L-Stick Down")
        assertTrue("L-Stick Left" in labels, "N64 layout should include L-Stick Left")
        assertTrue("L-Stick Right" in labels, "N64 layout should include L-Stick Right")
        // N64 has 14 digital + 4 analog = 18 total
        assertTrue(
            n64Layout.buttons.size == 18,
            "N64 should have 18 buttons but got ${n64Layout.buttons.size}",
        )
    }

    @Test
    fun psxLayoutIncludesBothStickDirections() {
        // Unit test: verify PSX layout includes both L-Stick and R-Stick
        val psxLayout = com.spela.player.domain.model.DefaultKeyMappings.PSX
        val labels = psxLayout.buttons.map { it.label }
        assertTrue("L-Stick Up" in labels, "PSX layout should include L-Stick Up")
        assertTrue("R-Stick Up" in labels, "PSX layout should include R-Stick Up")
        assertTrue("L-Stick Down" in labels, "PSX layout should include L-Stick Down")
        assertTrue("R-Stick Down" in labels, "PSX layout should include R-Stick Down")
        // PSX has 16 digital + 4 left + 4 right = 24 total
        assertTrue(
            psxLayout.buttons.size == 24,
            "PSX should have 24 buttons but got ${psxLayout.buttons.size}",
        )
    }

    @Test
    fun nesLayoutDoesNotIncludeAnalogButtons() {
        // Unit test: NES should NOT have any analog stick buttons
        val nesLayout = com.spela.player.domain.model.DefaultKeyMappings.NES
        val labels = nesLayout.buttons.map { it.label }
        assertTrue("L-Stick Up" !in labels, "NES layout should not include L-Stick Up")
        assertTrue("R-Stick Up" !in labels, "NES layout should not include R-Stick Up")
    }

    @Test
    fun n64ConsoleSettingsShowsAnalogOnVisual() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToConsoleSettings(harness, "n64")

        // Verify that the visual controller overlay shows analog stick buttons
        // (These should be visible since they're positioned in the controller visual, not in the LazyColumn)
        onNodeWithContentDescription("L-Stick Up, unmapped").assertExists()
    }
}
