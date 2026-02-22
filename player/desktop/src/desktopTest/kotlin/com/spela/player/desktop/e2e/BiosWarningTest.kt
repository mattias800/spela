package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.BiosConsoleStatus
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Desktop E2E tests for BIOS warning UX (AC 5.3).
 * Tests: BIOS warning chip on game detail, missing BIOS dialog,
 * "Try Anyway" behavior, and console list warning indicator.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class BiosWarningTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun gameDetailShowsBiosWarningChipWhenBiosMissing() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Configure missing BIOS for the NES console (game "1" is NES)
        harness.biosRepo.consolesWithMissingBios = mapOf(
            "nes" to BiosConsoleStatus(
                consoleId = "nes",
                consoleName = "NES",
                biosRequired = true,
                status = "missing",
                missingFiles = listOf(
                    BiosMissingFile("disksys.rom", "Famicom Disk System BIOS", true),
                ),
            ),
        )

        setContent { harness.App() }

        navigateToGameDetail(harness, gameId = "1")

        // The BIOS warning chip should be displayed
        onNodeWithText("BIOS Required").assertIsDisplayed()
        onNodeWithContentDescription("Missing BIOS files: disksys.rom").assertIsDisplayed()
    }

    @Test
    fun biosWarningChipTapShowsMissingFilesList() = runComposeUiTest {
        val harness = createLoggedInHarness()

        harness.biosRepo.consolesWithMissingBios = mapOf(
            "nes" to BiosConsoleStatus(
                consoleId = "nes",
                consoleName = "NES",
                biosRequired = true,
                status = "missing",
                missingFiles = listOf(
                    BiosMissingFile("disksys.rom", "Famicom Disk System BIOS", true),
                    BiosMissingFile("nes.pal", "NES PAL BIOS", false),
                ),
            ),
        )

        setContent { harness.App() }

        navigateToGameDetail(harness, gameId = "1")

        // Chip should be visible
        onNodeWithText("BIOS Required").assertIsDisplayed()

        // Info panel should NOT be visible yet
        onAllNodesWithText("Missing BIOS files:").assertCountEquals(0)

        // Tap the chip
        onNodeWithText("BIOS Required").performClick()
        waitForIdle()

        // Info panel should now be visible listing the missing files
        onNodeWithContentDescription("Missing BIOS files info").assertIsDisplayed()
        onNodeWithText("Missing BIOS files:").assertIsDisplayed()
        onNodeWithText("disksys.rom").assertIsDisplayed()
        onNodeWithText("nes.pal").assertIsDisplayed()

        // Tap the chip again to toggle off
        onNodeWithText("BIOS Required").performClick()
        waitForIdle()

        // Info panel should be hidden again
        onAllNodesWithContentDescription("Missing BIOS files info").assertCountEquals(0)
    }

    @Test
    fun gameDetailDoesNotShowBiosWarningChipWhenBiosPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // No missing BIOS files configured
        setContent { harness.App() }

        navigateToGameDetail(harness, gameId = "1")

        // The BIOS warning chip should NOT be displayed
        onAllNodesWithText("BIOS Required").assertCountEquals(0)
    }

    @Test
    fun missingBiosDialogShowsWhenLaunchingGameWithMissingBios() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Pre-cache game so Play button is available
        harness.downloadRepo.preCacheGame("1")

        // Configure pre-launch BIOS check to return missing files
        harness.biosRepo.preSetPreLaunchMissingFiles(
            listOf(
                BiosMissingFile("disksys.rom", "Famicom Disk System BIOS", true),
                BiosMissingFile("nes.pal", "NES PAL BIOS", true),
            ),
        )

        setContent { harness.App() }

        navigateToGameDetail(harness, gameId = "1")

        // Tap Play to trigger the game launch
        onNode(hasContentDescription("Play Castlevania")).performClick()
        advance(harness)

        // The Missing BIOS dialog should appear
        onNodeWithText("Missing BIOS Files").assertIsDisplayed()
        onNodeWithText("Go Back").assertIsDisplayed()
        onNodeWithText("Try Anyway").assertIsDisplayed()
    }

    @Test
    fun missingBiosDialogGoBackDismissesAndExitsOverlay() = runComposeUiTest {
        val harness = createLoggedInHarness()

        harness.downloadRepo.preCacheGame("1")
        harness.biosRepo.preSetPreLaunchMissingFiles(
            listOf(
                BiosMissingFile("disksys.rom", "Famicom Disk System BIOS", true),
            ),
        )

        setContent { harness.App() }

        navigateToGameDetail(harness, gameId = "1")

        // Launch game
        onNode(hasContentDescription("Play Castlevania")).performClick()
        advance(harness)

        // Verify dialog is shown
        onNodeWithText("Missing BIOS Files").assertIsDisplayed()

        // Tap "Go Back"
        onNodeWithText("Go Back").performClick()
        advance(harness)

        // Dialog should be dismissed and we should be back at game detail
        onAllNodesWithText("Missing BIOS Files").assertCountEquals(0)
        onNodeWithText("Castlevania").assertIsDisplayed()
    }

    @Test
    fun missingBiosDialogTryAnywayDismissesAndRetries() = runComposeUiTest {
        val harness = createLoggedInHarness()

        harness.downloadRepo.preCacheGame("1")
        // Configure BIOS check to return missing files
        harness.biosRepo.preSetPreLaunchMissingFiles(
            listOf(
                BiosMissingFile("disksys.rom", "Famicom Disk System BIOS", true),
            ),
        )

        setContent { harness.App() }

        navigateToGameDetail(harness, gameId = "1")

        // Launch game
        onNode(hasContentDescription("Play Castlevania")).performClick()
        advance(harness)

        // Verify dialog is shown
        onNodeWithText("Missing BIOS Files").assertIsDisplayed()

        // Tap "Try Anyway" — this should dismiss dialog and retry with skipBiosCheck=true
        onNodeWithText("Try Anyway").performClick()
        advanceFully(harness)

        // Dialog should be dismissed — the game launch was retried with BIOS check skipped
        onAllNodesWithText("Missing BIOS Files").assertCountEquals(0)
    }

    @Test
    fun consoleGameListShowsBiosWarningBannerWhenBiosMissing() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Configure missing BIOS for NES console
        harness.biosRepo.consolesWithMissingBios = mapOf(
            "nes" to BiosConsoleStatus(
                consoleId = "nes",
                consoleName = "NES",
                biosRequired = true,
                status = "missing",
                missingFiles = listOf(
                    BiosMissingFile("disksys.rom", "Famicom Disk System BIOS", true),
                ),
            ),
        )

        setContent { harness.App() }

        // Navigate to the NES console game list
        advance(harness)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("nes"))
        )
        advanceFully(harness)

        // The BIOS warning banner should be displayed
        onNodeWithContentDescription(
            "Missing BIOS files for Nintendo Entertainment System. Contact your server admin to upload the required firmware files."
        ).assertIsDisplayed()
    }

    @Test
    fun consoleGameListDoesNotShowBiosWarningBannerWhenBiosPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // No missing BIOS configured
        setContent { harness.App() }

        // Navigate to the NES console game list
        advance(harness)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("nes"))
        )
        advanceFully(harness)

        // The BIOS warning banner should NOT be displayed
        onAllNodesWithContentDescription(
            "Missing BIOS files for Nintendo Entertainment System. Contact your server admin to upload the required firmware files."
        ).assertCountEquals(0)
    }

    @Test
    fun consoleListShowsWarningIconWhenBiosMissing() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Configure missing BIOS for SNES console
        harness.biosRepo.consolesWithMissingBios = mapOf(
            "snes" to BiosConsoleStatus(
                consoleId = "snes",
                consoleName = "SNES",
                biosRequired = true,
                status = "missing",
                missingFiles = listOf(
                    BiosMissingFile("snes_bios.bin", "SNES BIOS", true),
                ),
            ),
        )

        setContent { harness.App() }

        // Navigate to Library (consoles tab)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Library)
        )
        advanceFully(harness)

        // SNES console card should have BIOS missing in its content description
        onNodeWithContentDescription("Super Nintendo, 2 games, BIOS missing").assertIsDisplayed()

        // NES console card should NOT have the BIOS missing indicator
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games").assertIsDisplayed()
    }

    @Test
    fun consoleListNoWarningIconWhenAllBiosPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // No missing BIOS configured
        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Library)
        )
        advanceFully(harness)

        // Both console cards should NOT mention BIOS missing
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games").assertIsDisplayed()
        onNodeWithContentDescription("Super Nintendo, 2 games").assertIsDisplayed()
    }
}
