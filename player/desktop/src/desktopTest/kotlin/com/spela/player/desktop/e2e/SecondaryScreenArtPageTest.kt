package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.ui.feature.ingame.SecondaryArtPage
import com.spela.player.presentation.ui.feature.ingame.SecondaryScreenContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the SecondaryArtPage game info card.
 *
 * Tests cover:
 * - Game info card rendering when metadata is present
 * - Card hidden when no metadata is available
 * - Description text display
 * - Rating display with star
 * - Player count display
 * - Integration test via full harness for metadata propagation
 * - Edge cases: empty string metadata, hero URL branch, session timer
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SecondaryScreenArtPageTest {

    @Test
    fun artPageShowsGameInfoCard() = runComposeUiTest {
        setContent {
            SecondaryArtPage(
                heroUrl = null,
                gameTitle = "Super Metroid",
                sessionElapsedSeconds = 3600,
                consoleId = "snes",
                consoleColorTheme = null,
                gameDeveloper = "Nintendo R&D1",
                gamePublisher = "Nintendo",
                gameReleaseDate = "1994",
                gameGenre = "Action",
                gameRating = 4.5,
                gamePlayers = 1,
            )
        }

        onNodeWithContentDescription("Game information").assertExists()
        onNodeWithText("Nintendo R&D1").assertExists()
        onNodeWithText("Nintendo").assertExists()
        onNodeWithText("1994").assertExists()
        onNodeWithText("Action").assertExists()
    }

    @Test
    fun artPageHidesCardWhenNoMetadata() = runComposeUiTest {
        setContent {
            SecondaryArtPage(
                heroUrl = null,
                gameTitle = "Unknown ROM",
                sessionElapsedSeconds = 0,
                consoleId = "nes",
                consoleColorTheme = null,
            )
        }

        onNodeWithContentDescription("Game information").assertDoesNotExist()
    }

    @Test
    fun artPageShowsDescription() = runComposeUiTest {
        setContent {
            SecondaryArtPage(
                heroUrl = null,
                gameTitle = "Chrono Trigger",
                sessionElapsedSeconds = 120,
                consoleId = "snes",
                consoleColorTheme = null,
                gameDescription = "A classic JRPG about time travel and saving the world.",
            )
        }

        onNodeWithContentDescription("Game information").assertExists()
        onNodeWithText("A classic JRPG about time travel and saving the world.").assertExists()
    }

    @Test
    fun artPageShowsRating() = runComposeUiTest {
        setContent {
            SecondaryArtPage(
                heroUrl = null,
                gameTitle = "Final Fantasy VI",
                sessionElapsedSeconds = 60,
                consoleId = "snes",
                consoleColorTheme = null,
                gameRating = 4.8,
            )
        }

        onNodeWithContentDescription("Game information").assertExists()
        onNodeWithText("4.8").assertExists()
        // Star character
        onNodeWithText("\u2605 ").assertExists()
    }

    @Test
    fun artPageShowsPlayerCount() = runComposeUiTest {
        setContent {
            SecondaryArtPage(
                heroUrl = null,
                gameTitle = "Street Fighter II",
                sessionElapsedSeconds = 300,
                consoleId = "snes",
                consoleColorTheme = null,
                gamePlayers = 2,
            )
        }

        onNodeWithContentDescription("Game information").assertExists()
        onNodeWithText("1-2 Players").assertExists()
    }

    @Test
    fun artPageShowsSinglePlayer() = runComposeUiTest {
        setContent {
            SecondaryArtPage(
                heroUrl = null,
                gameTitle = "Mega Man X",
                sessionElapsedSeconds = 180,
                consoleId = "snes",
                consoleColorTheme = null,
                gamePlayers = 1,
            )
        }

        onNodeWithContentDescription("Game information").assertExists()
        onNodeWithText("1 Player").assertExists()
    }

    // -- Helpers ---------------------------------------------------------------

    private fun createHarnessWithNesGame(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        return harness
    }

    private fun ComposeUiTest.advanceSeconds(harness: SpelaTestHarness, seconds: Int) {
        mainClock.autoAdvance = false
        repeat(seconds) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
        }
        mainClock.autoAdvance = true
    }

    private fun ComposeUiTest.startGameAndRenderSecondary(
        harness: SpelaTestHarness,
        gameId: String = "1",
    ) {
        harness.emulationViewModel.onIntent(EmulationIntent.StartGame(gameId = gameId))
        mainClock.autoAdvance = false
        repeat(4) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
        }
        mainClock.autoAdvance = true

        setContent {
            SecondaryScreenContent(
                viewModel = harness.emulationViewModel,
                controller = harness.libretroController,
            )
        }
        advanceSeconds(harness, 1)
    }

    // -- Integration test via full harness ------------------------------------

    @Test
    fun artPageShowsGameInfoViaHarness() = runComposeUiTest {
        val harness = createHarnessWithNesGame()
        startGameAndRenderSecondary(harness)

        // The fake game repo returns game "1" as Castlevania with developer+publisher "Konami"
        // The art page is the default page (page 0) so it should be visible
        onNodeWithContentDescription("Game information").assertExists()
        // Developer and publisher are both "Konami", producing two text nodes
        onAllNodesWithText("Konami").assertCountEquals(2)
        // Also verify the genre to confirm metadata propagation
        onNodeWithText("Action").assertExists()
    }

    // -- Edge case: empty string metadata -------------------------------------

    @Test
    fun artPageHandlesEmptyStringMetadata() = runComposeUiTest {
        setContent {
            SecondaryArtPage(
                heroUrl = null,
                gameTitle = "Unknown ROM",
                sessionElapsedSeconds = 0,
                consoleId = "nes",
                consoleColorTheme = null,
                gameDeveloper = "",
                gamePublisher = "",
            )
        }

        // Empty strings should be treated as no data — card should NOT exist
        onNodeWithContentDescription("Game information").assertDoesNotExist()
    }

    // -- Hero URL branch shows title ------------------------------------------

    @Test
    fun artPageWithHeroUrlShowsTitle() = runComposeUiTest {
        setContent {
            SecondaryArtPage(
                heroUrl = "https://example.com/hero.jpg",
                gameTitle = "Super Metroid",
                sessionElapsedSeconds = 120,
                consoleId = "snes",
                consoleColorTheme = null,
            )
        }

        // The image will fail to load in tests but the title text should still be present
        onNodeWithText("Super Metroid").assertExists()
    }

    // -- Session timer rendering ----------------------------------------------

    @Test
    fun artPageShowsSessionTimer() = runComposeUiTest {
        setContent {
            SecondaryArtPage(
                heroUrl = "https://example.com/hero.jpg",
                gameTitle = "Test Game",
                sessionElapsedSeconds = 3661, // 1h 1m 1s
                consoleId = "nes",
                consoleColorTheme = null,
            )
        }

        // formatSessionDuration(3661) => "1:01:01"
        // Timer is shown in the title row below the hero image
        onNodeWithText("1:01:01").assertExists()
    }
}
