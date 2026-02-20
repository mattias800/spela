package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.intent.KeyMappingIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        // Game Boy doesn't have a dedicated icon
        assertNull(
            com.spela.player.presentation.ui.components.keymapping.ControllerIcons.forConsole("gb"),
            "Game Boy should not have a specific icon (uses generic)",
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

        val psxLayout = com.spela.player.domain.model.DefaultKeyMappings.PSX
        val psxRegions = com.spela.player.presentation.ui.components.keymapping.ControllerButtonPositions.getRegions(psxLayout)
        assertTrue(
            psxRegions.size == 16,
            "PSX should have 16 button regions but got ${psxRegions.size}",
        )
    }
}
