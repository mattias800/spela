package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the "More from Developer" section on the Game Detail screen.
 *
 * This section shows other games by the same developer that are in the local
 * library. All cards are tappable (only library games are shown). The section
 * title includes the developer name.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class DeveloperGamesTest {

    private fun createHarnessOnGameDetail(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.scrollToSection(matcher: SemanticsMatcher) {
        onNodeWithTag("game_detail_content").performScrollToNode(matcher)
    }

    // --- Section visibility ---

    @Test
    fun developerGamesSectionShownWhenDeveloperHasOtherGames() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        // Game "1" is Castlevania by Konami
        harness.gameRepo.developerGamesMap = mapOf(
            "1" to listOf(
                DeveloperGame(id = "10", title = "Contra", consoleName = "NES"),
                DeveloperGame(id = "11", title = "Gradius", consoleName = "NES"),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithText("Castlevania").assertIsDisplayed()
        scrollToSection(hasText("More from Konami"))
        onNodeWithText("More from Konami").assertIsDisplayed()
    }

    @Test
    fun developerGamesSectionHiddenWhenNoOtherGames() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameRepo.developerGamesMap = emptyMap()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("More from Konami").assertDoesNotExist()
        onNodeWithText("More from Developer").assertDoesNotExist()
    }

    // --- Developer name in title ---

    @Test
    fun developerNameAppearsInSectionTitle() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        // Game "2" is Super Mario Bros. by Nintendo
        harness.gameRepo.developerGamesMap = mapOf(
            "2" to listOf(
                DeveloperGame(id = "20", title = "Donkey Kong", consoleName = "NES"),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "2")

        onNodeWithText("Super Mario Bros.").assertIsDisplayed()
        scrollToSection(hasText("More from Nintendo"))
        onNodeWithText("More from Nintendo").assertIsDisplayed()
    }

    // --- Card content ---

    @Test
    fun developerGameCardsShowGameNameAndConsole() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameRepo.developerGamesMap = mapOf(
            "1" to listOf(
                DeveloperGame(id = "10", title = "Contra", consoleName = "NES"),
                DeveloperGame(id = "11", title = "Gradius", consoleName = "SNES"),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSection(hasText("More from Konami"))

        // Game names visible
        onNodeWithText("Contra").assertExists()
        onNodeWithText("Gradius").assertExists()

        // Semantic content description includes console name
        onNodeWithContentDescription("Contra on NES").assertExists()
        onNodeWithContentDescription("Gradius on SNES").assertExists()
    }

    // --- Current game exclusion ---

    @Test
    fun currentGameExcludedFromDeveloperGamesRow() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        // Viewing Castlevania (id "1") by Konami — the row should NOT contain Castlevania itself.
        // The server excludes the current game; the fake mirrors this by only listing other games.
        harness.gameRepo.developerGamesMap = mapOf(
            "1" to listOf(
                DeveloperGame(id = "10", title = "Contra", consoleName = "NES"),
                DeveloperGame(id = "11", title = "Gradius", consoleName = "NES"),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSection(hasText("More from Konami"))

        // Other Konami games are visible
        onNodeWithContentDescription("Contra on NES").assertExists()
        onNodeWithContentDescription("Gradius on NES").assertExists()

        // The current game (Castlevania) should NOT appear as a developer game card.
        // Note: "Castlevania" text exists in the page title, but the developer games
        // row should not contain a card with the Castlevania content description.
        onNodeWithContentDescription("Castlevania on NES").assertDoesNotExist()
    }

    // --- Clickability ---

    @Test
    fun allDeveloperGameCardsAreTappable() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameRepo.developerGamesMap = mapOf(
            "1" to listOf(
                DeveloperGame(id = "10", title = "Contra", consoleName = "NES"),
                DeveloperGame(id = "11", title = "Gradius", consoleName = "NES"),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSection(hasText("More from Konami"))

        // All developer game cards are library games, so all should be clickable
        onNodeWithContentDescription("Contra on NES").assertHasClickAction()
        onNodeWithContentDescription("Gradius on NES").assertHasClickAction()
    }
}
