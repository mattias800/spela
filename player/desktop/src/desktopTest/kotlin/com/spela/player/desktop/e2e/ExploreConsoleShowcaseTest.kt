package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import androidx.compose.ui.test.hasTestTag
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.ConsoleHighlight
import com.spela.player.domain.model.ConsoleShowcase
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
 * Desktop E2E tests for Phase 8: Console Showcase Pages.
 *
 * Covers:
 * - Console showcase screen renders with console name
 * - Essentials section shows games
 * - Hidden gems section shows games
 * - Genre breakdown shows genre chips
 * - Top developers section shows developer names
 * - Recently played section hidden when empty
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreConsoleShowcaseTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleConsole = Console(
        id = "snes",
        name = "Super Nintendo",
        abbreviation = "SNES",
        gameCount = 42,
        colorTheme = "#6B21A8",
    )

    private val sampleEssentials = listOf(
        Game(
            id = "game-ess-1",
            title = "Super Mario World",
            consoleId = "snes",
            consoleName = "SNES",
            genre = "Platformer",
            rating = 95.0,
        ),
        Game(
            id = "game-ess-2",
            title = "The Legend of Zelda: A Link to the Past",
            consoleId = "snes",
            consoleName = "SNES",
            genre = "Action RPG",
            rating = 94.0,
        ),
    )

    private val sampleHiddenGems = listOf(
        Game(
            id = "game-gem-1",
            title = "Terranigma",
            consoleId = "snes",
            consoleName = "SNES",
            genre = "Action RPG",
            rating = 88.0,
        ),
    )

    private val sampleGenres = listOf(
        GenreCount(name = "Platformer", gameCount = 12),
        GenreCount(name = "RPG", gameCount = 8),
        GenreCount(name = "Action", gameCount = 6),
    )

    private val sampleDevelopers = listOf(
        DeveloperSummary(
            name = "Nintendo",
            gameCount = 10,
            avgRating = 92.0,
            consoles = listOf("SNES"),
        ),
        DeveloperSummary(
            name = "Square",
            gameCount = 7,
            avgRating = 90.0,
            consoles = listOf("SNES"),
        ),
    )

    private val sampleRecentlyPlayed = listOf(
        Game(
            id = "game-recent-1",
            title = "Chrono Trigger",
            consoleId = "snes",
            consoleName = "SNES",
            genre = "RPG",
            rating = 96.0,
        ),
    )

    private val sampleShowcase = ConsoleShowcase(
        console = sampleConsole,
        essentials = sampleEssentials,
        hiddenGems = sampleHiddenGems,
        genreBreakdown = sampleGenres,
        topDevelopers = sampleDevelopers,
        recentlyPlayed = sampleRecentlyPlayed,
    )

    private val sampleShowcaseNoRecent = sampleShowcase.copy(
        recentlyPlayed = emptyList(),
    )

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

    // --- Console showcase screen renders ---

    @Test
    fun consoleShowcaseScreenRendersWithConsoleName() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleShowcases = mapOf("snes" to sampleShowcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreConsoleShowcase("snes"))
        )
        advanceFully(harness)

        onNodeWithTag("console_showcase_screen").assertIsDisplayed()
        onAllNodesWithText("Super Nintendo").fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "Expected at least one 'Super Nintendo' text node" }
        }
    }

    // --- Essentials section ---

    @Test
    fun essentialsSectionShowsGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleShowcases = mapOf("snes" to sampleShowcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreConsoleShowcase("snes"))
        )
        advanceFully(harness)

        onNodeWithTag("console_essentials_section").assertExists()
        onNodeWithText("Essentials").assertExists()
    }

    // --- Hidden gems section ---

    @Test
    fun hiddenGemsSectionShowsGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleShowcases = mapOf("snes" to sampleShowcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreConsoleShowcase("snes"))
        )
        advanceFully(harness)

        onNodeWithTag("console_hidden_gems_section").assertExists()
        onNodeWithText("Hidden Gems").assertExists()
    }

    // --- Genre breakdown ---

    @Test
    fun genreBreakdownShowsGenreChips() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleShowcases = mapOf("snes" to sampleShowcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreConsoleShowcase("snes"))
        )
        advanceFully(harness)

        // Scroll down to find the genre breakdown section (may be off-screen in LazyColumn)
        onNodeWithTag("console_showcase_list").performScrollToNode(
            hasTestTag("console_genre_breakdown_section")
        )
        advanceQuick(harness)

        onNodeWithTag("console_genre_breakdown_section").assertExists()
        onNodeWithText("Genre Breakdown").assertExists()
        onNodeWithTag("genre_chip_Platformer").assertExists()
        onNodeWithTag("genre_chip_RPG").assertExists()
        onNodeWithTag("genre_chip_Action").assertExists()
    }

    // --- Top developers ---

    @Test
    fun topDevelopersSectionShowsDeveloperNames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleShowcases = mapOf("snes" to sampleShowcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreConsoleShowcase("snes"))
        )
        advanceFully(harness)

        // Scroll down to find the top developers section (may be off-screen in LazyColumn)
        onNodeWithTag("console_showcase_list").performScrollToNode(
            hasTestTag("console_top_developers_section")
        )
        advanceQuick(harness)

        onNodeWithTag("console_top_developers_section").assertExists()
        onNodeWithText("Top Developers").assertExists()
        onNodeWithTag("developer_card_Nintendo").assertExists()
        onNodeWithTag("developer_card_Square").assertExists()
    }

    // --- Recently played hidden when empty ---

    @Test
    fun recentlyPlayedSectionHiddenWhenEmpty() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleShowcases = mapOf("snes" to sampleShowcaseNoRecent)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreConsoleShowcase("snes"))
        )
        advanceFully(harness)

        onNodeWithTag("console_showcase_screen").assertIsDisplayed()
        onNodeWithTag("console_recently_played_section").assertDoesNotExist()
    }

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

    // --- Navigation from highlights to showcase ---

    @Test
    fun navigationFromHighlightToShowcaseWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.consoleHighlightsList = sampleHighlights
        harness.exploreRepo.consoleShowcases = mapOf("snes" to sampleShowcase)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("console_card_snes").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_console/snes", navState.currentScreen.route)
    }
}
