package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for demo console (Amiga Demos) specific display on the Game Detail screen:
 * - "Group" label instead of "Developer"
 * - "Type" label instead of "Genre"
 * - "Party" info displayed when partyInfo is present
 * - Save-related sections hidden (sessions, time to beat, challenges)
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GameDetailDemoConsoleTest {

    private val demoConsole = Console(
        id = "ademo",
        name = "Amiga Demos",
        abbreviation = "ADEMO",
        gameCount = 1,
        colorTheme = "#8b5cf6",
        saveStateSupport = false,
    )

    private val demoGame = Game(
        id = "demo-1",
        title = "State of the Art",
        consoleId = "ademo",
        consoleName = "Amiga Demos",
        description = "A legendary Amiga demo by Spaceballs.",
        developer = "Spaceballs",
        genre = "Demo",
        releaseDate = "1992",
        partyInfo = "The Party 1992, 1st place",
        fileSize = 524288,
        fileName = "stateoftheart.adf",
        scrapeAttempts = 1,
        timeToBeatNormally = 300,
    )

    private fun createHarnessWithDemoGame(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.gameRepo.consoles = harness.gameRepo.consoles + demoConsole
        harness.gameRepo.games = harness.gameRepo.games + demoGame
        return harness
    }

    private fun ComposeUiTest.navigateToDemoGame(harness: SpelaTestHarness) =
        navigateToGameDetail(harness, "demo-1")

    /**
     * Scroll the game detail LazyColumn until the node matching [matcher] is visible.
     */
    private fun ComposeUiTest.scrollToSection(matcher: SemanticsMatcher) {
        onNodeWithTag("game_detail_content").performScrollToNode(matcher)
    }

    // --- Demo-specific metadata labels ---

    @Test
    fun demoConsoleShowsGroupInsteadOfDeveloper() = runComposeUiTest {
        val harness = createHarnessWithDemoGame()
        setContent { harness.App() }
        navigateToDemoGame(harness)

        onNodeWithText("State of the Art").assertIsDisplayed()
        // Should show "Group" label for developer field
        onNodeWithText("Group").assertIsDisplayed()
        onNodeWithText("Spaceballs").assertIsDisplayed()
        // "Developer" label should not be present
        onNodeWithText("Developer").assertDoesNotExist()
    }

    @Test
    fun demoConsoleShowsTypeInsteadOfGenre() = runComposeUiTest {
        val harness = createHarnessWithDemoGame()
        setContent { harness.App() }
        navigateToDemoGame(harness)

        onNodeWithText("State of the Art").assertIsDisplayed()
        // Should show "Type" label for genre field
        onNodeWithText("Type").assertIsDisplayed()
        onNodeWithText("Demo").assertIsDisplayed()
        // "Genre" label should not be present
        onNodeWithText("Genre").assertDoesNotExist()
    }

    @Test
    fun demoConsoleShowsPartyInfoWhenPresent() = runComposeUiTest {
        val harness = createHarnessWithDemoGame()
        setContent { harness.App() }
        navigateToDemoGame(harness)

        onNodeWithText("State of the Art").assertIsDisplayed()
        // Should show "Party" label with partyInfo value
        onNodeWithText("Party").assertIsDisplayed()
        onNodeWithText("The Party 1992, 1st place").assertIsDisplayed()
    }

    @Test
    fun demoConsoleHidesPartyInfoWhenEmpty() = runComposeUiTest {
        val harness = createHarnessWithDemoGame()
        // Replace game with one that has no partyInfo
        val gameNoParty = demoGame.copy(id = "demo-2", partyInfo = "")
        harness.gameRepo.games = harness.gameRepo.games + gameNoParty
        setContent { harness.App() }
        navigateToGameDetail(harness, "demo-2")

        onNodeWithText("State of the Art").assertIsDisplayed()
        // "Party" label should not be present
        onNodeWithText("Party").assertDoesNotExist()
    }

    // --- Hidden sections for demo consoles ---

    @Test
    fun demoConsoleHidesSessionsSection() = runComposeUiTest {
        val harness = createHarnessWithDemoGame()
        setContent { harness.App() }
        navigateToDemoGame(harness)

        onNodeWithText("State of the Art").assertIsDisplayed()
        // Sessions section should not be rendered for demo consoles
        onNodeWithTag("sessions_section").assertDoesNotExist()
    }

    @Test
    fun demoConsoleHidesTimeToBeatSection() = runComposeUiTest {
        val harness = createHarnessWithDemoGame()
        setContent { harness.App() }
        navigateToDemoGame(harness)

        onNodeWithText("State of the Art").assertIsDisplayed()
        // Time to beat section should not be rendered for demo consoles
        // even though the game has timeToBeatNormally = 300
        onNodeWithTag("time_to_beat_section").assertDoesNotExist()
    }

    @Test
    fun demoConsoleHidesChallengesSection() = runComposeUiTest {
        val harness = createHarnessWithDemoGame()
        setContent { harness.App() }
        navigateToDemoGame(harness)

        onNodeWithText("State of the Art").assertIsDisplayed()
        // Challenges buttons should not be rendered for demo consoles
        onNodeWithTag("view_challenges_button").assertDoesNotExist()
        onNodeWithTag("create_challenge_button").assertDoesNotExist()
    }

    // --- Regular consoles still show all sections ---

    @Test
    fun regularConsoleShowsDeveloperLabel() = runComposeUiTest {
        val harness = createHarnessWithDemoGame()
        setContent { harness.App() }
        // Navigate to regular game (Castlevania, id=1)
        navigateToGameDetail(harness, "1")

        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("Developer").assertIsDisplayed()
        // "Group" should not be present for regular consoles
        onNodeWithText("Group").assertDoesNotExist()
    }

    @Test
    fun regularConsoleShowsGenreLabel() = runComposeUiTest {
        val harness = createHarnessWithDemoGame()
        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("Genre").assertIsDisplayed()
        onNodeWithText("Action").assertIsDisplayed()
        // "Type" should not be present for regular consoles
        onNodeWithText("Type").assertDoesNotExist()
    }
}
