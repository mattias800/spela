package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.FeaturedSeries
import com.spela.player.domain.model.GameSeriesLink
import com.spela.player.domain.model.SeriesConsole
import com.spela.player.domain.model.SeriesDetail
import com.spela.player.domain.model.SeriesGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for Phase 4: Franchise & Series Pages.
 *
 * Covers:
 * - Series shelf renders on Explore screen
 * - Series shelf cards show correct info
 * - Navigate to series detail screen
 * - Series detail shows games in timeline
 * - Ownership progress shows correct counts
 * - Console filter works
 * - Dimmed treatment for non-library games
 * - "Part of [Series]" link on game detail screen
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreSeriesTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleFeaturedSeries = listOf(
        FeaturedSeries(
            id = "s1",
            name = "Super Mario",
            libraryGames = 5,
            totalGames = 12,
            consoleCount = 3,
            heroUrl = null,
        ),
        FeaturedSeries(
            id = "s2",
            name = "The Legend of Zelda",
            libraryGames = 3,
            totalGames = 8,
            consoleCount = 4,
            heroUrl = null,
        ),
    )

    private val sampleSeriesGames = listOf(
        SeriesGame(
            igdbGameId = 1001,
            name = "Super Mario Bros.",
            inLibrary = true,
            localGameId = "game-1",
            coverUrl = null,
            releaseDate = "1985-09-13",
            rating = 85.0,
            consoleAbbreviation = "NES",
            consoleName = "NES",
            consoleColor = "#dc2626",
        ),
        SeriesGame(
            igdbGameId = 1002,
            name = "Super Mario World",
            inLibrary = true,
            localGameId = "game-2",
            coverUrl = null,
            releaseDate = "1990-11-21",
            rating = 94.0,
            consoleAbbreviation = "SNES",
            consoleName = "SNES",
            consoleColor = "#9333ea",
        ),
        SeriesGame(
            igdbGameId = 1003,
            name = "Super Mario 64",
            inLibrary = false,
            localGameId = null,
            coverUrl = null,
            releaseDate = "1996-06-23",
            rating = 96.0,
            consoleAbbreviation = "N64",
            consoleName = "N64",
            consoleColor = "#22c55e",
        ),
    )

    private val sampleSeriesConsoles = listOf(
        SeriesConsole(abbreviation = "NES", name = "Nintendo Entertainment System", color = "#dc2626", gameCount = 1),
        SeriesConsole(abbreviation = "SNES", name = "Super Nintendo", color = "#9333ea", gameCount = 1),
        SeriesConsole(abbreviation = "N64", name = "Nintendo 64", color = "#22c55e", gameCount = 1),
    )

    private val sampleSeriesDetail = SeriesDetail(
        id = "s1",
        name = "Super Mario",
        heroUrl = null,
        consoles = sampleSeriesConsoles,
        libraryGames = 2,
        totalGames = 3,
        games = sampleSeriesGames,
    )

    // --- Series shelf on Explore screen ---

    @Test
    fun seriesShelfRendersOnExploreScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredSeriesList = sampleFeaturedSeries

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_series_section").assertIsDisplayed()
        onNodeWithText("Browse by Series").assertIsDisplayed()
        onNodeWithTag("series_shelf").assertIsDisplayed()
    }

    @Test
    fun seriesCardsShowCorrectInfo() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredSeriesList = sampleFeaturedSeries

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithText("Super Mario").assertExists()
        onNodeWithText("5/12 games").assertExists()
        onNodeWithText("across 3 consoles").assertExists()
        onNodeWithText("The Legend of Zelda").assertExists()
        onNodeWithText("3/8 games").assertExists()
    }

    @Test
    fun seriesShelfHiddenWhenNoSeries() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredSeriesList = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_series_section").assertDoesNotExist()
    }

    // --- Navigation to series detail ---

    @Test
    fun navigationToSeriesDetailWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredSeriesList = sampleFeaturedSeries
        harness.exploreRepo.seriesDetails = mapOf("s1" to sampleSeriesDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("series_card_s1").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_series/s1", navState.currentScreen.route)
    }

    // --- Series detail screen ---

    @Test
    fun seriesDetailShowsGamesInTimeline() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.seriesDetails = mapOf("s1" to sampleSeriesDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreSeries("s1", "Super Mario"))
        )
        advance(harness)

        onNodeWithTag("explore_series_screen").assertIsDisplayed()
        // "Super Mario" appears in both the top bar and the hero banner
        onAllNodesWithText("Super Mario").fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "Expected at least one 'Super Mario' text node" }
        }
        onNodeWithText("Super Mario Bros.").assertExists()
        onNodeWithText("Super Mario World").assertExists()
        onNodeWithText("Super Mario 64").assertExists()
    }

    @Test
    fun ownershipProgressShowsCorrectCounts() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.seriesDetails = mapOf("s1" to sampleSeriesDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreSeries("s1", "Super Mario"))
        )
        advance(harness)

        onNodeWithTag("series_ownership_text").assertIsDisplayed()
        onNodeWithText("You own 2 of 3 games").assertExists()
    }

    @Test
    fun consoleFilterWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.seriesDetails = mapOf("s1" to sampleSeriesDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreSeries("s1", "Super Mario"))
        )
        advance(harness)

        // All 3 games visible initially
        onNodeWithTag("series_game_1001").assertExists()
        onNodeWithTag("series_game_1002").assertExists()
        onNodeWithTag("series_game_1003").assertExists()

        // Click NES filter
        onNodeWithTag("series_console_chip_NES").performClick()
        advanceQuick(harness)

        // Only NES game visible
        onNodeWithTag("series_game_1001").assertExists()
        onNodeWithTag("series_game_1002").assertDoesNotExist()
        onNodeWithTag("series_game_1003").assertDoesNotExist()

        // Click NES again to clear filter
        onNodeWithTag("series_console_chip_NES").performClick()
        advanceQuick(harness)

        // All games visible again
        onNodeWithTag("series_game_1001").assertExists()
        onNodeWithTag("series_game_1002").assertExists()
        onNodeWithTag("series_game_1003").assertExists()
    }

    @Test
    fun dimmedTreatmentForNonLibraryGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.seriesDetails = mapOf("s1" to sampleSeriesDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreSeries("s1", "Super Mario"))
        )
        advance(harness)

        // In-library game should exist
        val inLibraryNode = onNodeWithTag("series_game_1001")
        inLibraryNode.assertExists()

        // Not-in-library game should exist (dimmed via alpha)
        val notInLibraryNode = onNodeWithTag("series_game_1003")
        notInLibraryNode.assertExists()

        // The semantics should indicate non-library status
        notInLibraryNode.assertContentDescriptionContains("not in library", substring = true)
    }

    // --- "Part of [Series]" on game detail screen ---

    @Test
    fun seriesLinkShowsOnGameDetailScreen() = runComposeUiTest {
        val harness = createHarness()

        // Game "1" (Castlevania) already exists in FakeGameRepository
        // Add series links for it
        harness.exploreRepo.gameSeriesLinks = mapOf(
            "1" to listOf(
                GameSeriesLink(
                    id = "s1",
                    name = "Super Mario",
                    totalGames = 12,
                    libraryGames = 5,
                ),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Verify the series link chip is displayed
        onNodeWithTag("series_franchise_section").assertExists()
        onNodeWithText("Part of Super Mario (5/12)", substring = true).assertExists()
    }

    @Test
    fun seriesSectionHiddenWhenNoLinks() = runComposeUiTest {
        val harness = createHarness()

        // Game "1" (Castlevania) exists but no series/franchise links

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithTag("series_franchise_section").assertDoesNotExist()
    }
}
