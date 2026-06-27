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

    private fun missingBiosStatus(
        consoleId: String = "nes",
        consoleName: String = "NES",
        files: List<BiosMissingFile> = listOf(
            BiosMissingFile("disksys.rom", "Famicom Disk System BIOS", true),
        ),
    ) = BiosConsoleStatus(
        consoleId = consoleId,
        consoleName = consoleName,
        biosRequired = true,
        status = "missing",
        missingFiles = files,
    )

    @Test
    fun gameDetailMissingBiosShowsChipDetailsAndPlayGate() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.biosRepo.consolesWithMissingBios = mapOf(
            "nes" to missingBiosStatus(
                files = listOf(
                    BiosMissingFile("disksys.rom", "Famicom Disk System BIOS", true),
                    BiosMissingFile("nes.pal", "NES PAL BIOS", false),
                ),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, gameId = "1")

        onNodeWithTag("game_detail_play_button").assertIsDisplayed()
        onNodeWithText("BIOS Required").assertIsDisplayed()
        onNodeWithContentDescription("Missing BIOS files: disksys.rom, nes.pal").assertIsDisplayed()
        onAllNodesWithText("Missing BIOS files:").assertCountEquals(0)

        onNodeWithText("BIOS Required").performClick()
        waitForIdle()

        onNodeWithContentDescription("Missing BIOS files info").assertIsDisplayed()
        onNodeWithText("Missing BIOS files:").assertIsDisplayed()
        onNodeWithText("disksys.rom").assertIsDisplayed()
        onNodeWithText("nes.pal").assertIsDisplayed()

        onNodeWithText("BIOS Required").performClick()
        waitForIdle()

        onAllNodesWithContentDescription("Missing BIOS files info").assertCountEquals(0)
    }

    @Test
    fun gameDetailWithoutMissingBiosShowsPlayableButtonOnly() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, gameId = "1")

        onNodeWithTag("game_detail_play_button").assertIsDisplayed()
        onAllNodesWithText("BIOS Required").assertCountEquals(0)
    }

    @Test
    fun missingBiosDialogCanGoBackThenTryAnyway() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.biosRepo.preSetPreLaunchMissingFiles(
            listOf(
                BiosMissingFile("disksys.rom", "Famicom Disk System BIOS", true),
                BiosMissingFile("nes.pal", "NES PAL BIOS", true),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, gameId = "1")

        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        onNodeWithText("Missing BIOS Files").assertIsDisplayed()
        onNodeWithText("Go Back").assertIsDisplayed()
        onNodeWithText("Try Anyway").assertIsDisplayed()

        onNodeWithText("Go Back").performClick()
        advance(harness)

        onAllNodesWithText("Missing BIOS Files").assertCountEquals(0)
        onNodeWithText("Castlevania").assertIsDisplayed()

        // The next launch attempt shows the same pre-launch warning; Try
        // Anyway dismisses it and continues through the retry path. The
        // EmulationViewModel unit tests cover the bypass flag details.
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        onNodeWithText("Missing BIOS Files").assertIsDisplayed()

        onNodeWithText("Try Anyway").performClick()
        advanceFully(harness)

        onAllNodesWithText("Missing BIOS Files").assertCountEquals(0)
    }

    @Test
    fun consoleGameListShowsBiosWarningBannerWhenBiosMissing() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.biosRepo.consolesWithMissingBios = mapOf(
            "nes" to missingBiosStatus(),
        )

        setContent { harness.App() }

        advance(harness)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("nes"))
        )
        advanceFully(harness)

        onNodeWithContentDescription(
            "Missing BIOS files for Nintendo Entertainment System. Contact your server admin to upload the required firmware files."
        ).assertIsDisplayed()
    }

    @Test
    fun consoleGameListDoesNotShowBiosWarningBannerWhenBiosPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        advance(harness)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("nes"))
        )
        advanceFully(harness)

        onAllNodesWithContentDescription(
            "Missing BIOS files for Nintendo Entertainment System. Contact your server admin to upload the required firmware files."
        ).assertCountEquals(0)
    }

    @Test
    fun consoleListShowsWarningIconWhenBiosMissing() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.biosRepo.consolesWithMissingBios = mapOf(
            "snes" to missingBiosStatus(
                consoleId = "snes",
                consoleName = "SNES",
                files = listOf(BiosMissingFile("snes_bios.bin", "SNES BIOS", true)),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advanceFully(harness)

        onNodeWithContentDescription("Super Nintendo, 2 games, BIOS missing").assertIsDisplayed()
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games").assertIsDisplayed()
    }

    @Test
    fun consoleListNoWarningIconWhenAllBiosPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advanceFully(harness)

        onNodeWithContentDescription("Nintendo Entertainment System, 3 games").assertIsDisplayed()
        onNodeWithContentDescription("Super Nintendo, 2 games").assertIsDisplayed()
    }
}
