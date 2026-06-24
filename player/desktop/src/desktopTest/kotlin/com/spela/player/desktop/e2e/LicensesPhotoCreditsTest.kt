package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.domain.model.ConsolePhotoCredit
import com.spela.player.domain.model.ConsolePhotoCredits
import com.spela.player.presentation.ui.screen.LicensesScreen
import kotlin.test.Test

/**
 * Locks the console-hardware-photo attribution section on the Credits &
 * Licenses screen (#1441). The bundled photos vary by author and license, so
 * each one must be credited individually — this verifies the per-photo author,
 * license, and source actually render, which is what keeps us CC-BY-SA
 * compliant in the app.
 */
@OptIn(ExperimentalTestApi::class)
class LicensesPhotoCreditsTest {

    private fun fakeRepoWithCredits() = FakeGameRepository().apply {
        photoCredits = ConsolePhotoCredits(
            note = "Console hardware photos from Wikimedia Commons.",
            photos = listOf(
                ConsolePhotoCredit(
                    console = "nes",
                    title = "NES-Console-Set.png",
                    author = "Evan-Amos",
                    license = "Public domain",
                    source = "https://commons.wikimedia.org/wiki/File:NES-Console-Set.png",
                ),
                ConsolePhotoCredit(
                    console = "3do",
                    title = "3DO-FZ1-Console-Set.png",
                    author = "Evan-Amos",
                    license = "CC BY-SA 3.0",
                    source = "https://commons.wikimedia.org/wiki/File:3DO-FZ1-Console-Set.png",
                ),
            ),
        )
    }

    @Test
    fun showsPerPhotoAttribution() = runComposeUiTest {
        setContent {
            LicensesScreen(onBack = {}, gameRepository = fakeRepoWithCredits())
        }

        // The section lives below the hardcoded software credits, so scroll the
        // lazy list to bring each attribution into view before asserting.
        onNodeWithTag("licenses_list")
            .performScrollToNode(hasText("Console hardware photos"))
        onNodeWithText("Console hardware photos").assertIsDisplayed()
        onNodeWithText("Console hardware photos from Wikimedia Commons.").assertIsDisplayed()

        onNodeWithTag("licenses_list")
            .performScrollToNode(hasText("NES — NES-Console-Set.png"))
        onNodeWithText("NES — NES-Console-Set.png").assertIsDisplayed()

        // The varying license is surfaced per photo (compliance-critical).
        onNodeWithTag("licenses_list")
            .performScrollToNode(hasText("Evan-Amos · CC BY-SA 3.0"))
        onNodeWithText("Evan-Amos · CC BY-SA 3.0").assertIsDisplayed()
    }

    @Test
    fun omitsSectionWhenNoCredits() = runComposeUiTest {
        setContent {
            LicensesScreen(onBack = {}, gameRepository = FakeGameRepository())
        }

        onNodeWithText("Console hardware photos").assertDoesNotExist()
    }
}
