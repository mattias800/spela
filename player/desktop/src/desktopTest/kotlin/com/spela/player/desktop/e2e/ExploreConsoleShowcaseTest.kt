package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ConsoleHighlight
import com.spela.player.domain.model.ConsoleShowcase
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.DeveloperSummary
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GenreCount
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for console highlights on the Explore screen.
 *
 * The ExploreConsoleShowcase screen has been removed; tapping a console
 * highlight now navigates to the regular Console detail page instead.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreConsoleShowcaseTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleHighlights = listOf(
        ConsoleHighlight(
            id = "snes",
            name = "Super Nintendo",
            colorTheme = "#6B21A8",
            iconUrl = "",
            logoUrl = "",
            gameCount = 42,
            topGame = null,
        ),
        ConsoleHighlight(
            id = "nes",
            name = "Nintendo Entertainment System",
            colorTheme = "#DC2626",
            iconUrl = "",
            logoUrl = "",
            gameCount = 30,
            topGame = null,
        ),
    )

    private val sampleConsole = Console(
        id = "snes",
        name = "Super Nintendo",
        abbreviation = "SNES",
        gameCount = 42,
        colorTheme = "#6B21A8",
    )

    private val sampleShowcase = ConsoleShowcase(
        console = sampleConsole,
        essentials = emptyList(),
        hiddenGems = emptyList(),
        launchGames = emptyList(),
        genreBreakdown = emptyList(),
        topDevelopers = emptyList(),
        recentlyPlayed = emptyList(),
        recentlyAdded = emptyList(),
    )

    // --- Console highlights on Explore screen ---

    @Test
    fun consoleHighlightsRendersOnExploreScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleHighlightsList = sampleHighlights

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_consoles_section").assertExists()
        onNodeWithText("Browse by Console").assertExists()
    }

    // --- Navigation from highlights to console detail ---

    @Test
    fun navigationFromHighlightToConsoleDetailWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleHighlightsList = sampleHighlights
        harness.exploreRepo.consoleShowcases = mapOf("snes" to sampleShowcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("console_card_snes").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("console/snes", navState.currentScreen.route)
    }

    // --- Launch games shelf ---

    private fun sampleLaunchGame(id: String, title: String) = Game(
        id = id,
        title = title,
        consoleId = "snes",
        consoleName = "Super Nintendo",
    )

    @Test
    fun consoleLaunchGamesSectionRendersWhenPopulated() = runComposeUiTest {
        val harness = createHarness()
        val showcase = sampleShowcase.copy(
            launchGames = listOf(
                sampleLaunchGame("1", "Super Mario World"),
                sampleLaunchGame("2", "F-Zero"),
            ),
        )
        harness.exploreRepo.consoleShowcases = mapOf("snes" to showcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("snes"))
        )
        advance(harness)

        onNodeWithTag("console_launch_games_section").assertExists()
        onNodeWithText("Launch Games").assertExists()
        onNodeWithText("Super Mario World").assertExists()
        onNodeWithText("F-Zero").assertExists()
    }

    @Test
    fun consoleLaunchGamesSectionHiddenWhenEmpty() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleShowcases = mapOf("snes" to sampleShowcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("snes"))
        )
        advance(harness)

        onNodeWithTag("console_launch_games_section").assertDoesNotExist()
    }

    // --- Recently Added shelf ---

    private fun sampleAddedGame(id: String, title: String) = Game(
        id = id,
        title = title,
        consoleId = "snes",
        consoleName = "Super Nintendo",
    )

    @Test
    fun consoleRecentlyAddedSectionRendersWhenPopulated() = runComposeUiTest {
        val harness = createHarness()
        // Use distinctive titles that don't collide with the fake gameRepo's
        // default SNES game list — the console screen also renders those in
        // an inline grid, so shared titles would satisfy the assertion from
        // either location.
        val showcase = sampleShowcase.copy(
            recentlyAdded = listOf(
                sampleAddedGame("ra-1", "Recently Added Alpha"),
                sampleAddedGame("ra-2", "Recently Added Beta"),
            ),
        )
        harness.exploreRepo.consoleShowcases = mapOf("snes" to showcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("snes"))
        )
        advance(harness)

        onNodeWithTag("console_recently_added_section").assertExists()
        onNodeWithText("Recently Added").assertExists()
        onNodeWithText("Recently Added Alpha").assertExists()
        onNodeWithText("Recently Added Beta").assertExists()
    }

    @Test
    fun consoleRecentlyAddedSectionHiddenWhenEmpty() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleShowcases = mapOf("snes" to sampleShowcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("snes"))
        )
        advance(harness)

        onNodeWithTag("console_recently_added_section").assertDoesNotExist()
    }
}
