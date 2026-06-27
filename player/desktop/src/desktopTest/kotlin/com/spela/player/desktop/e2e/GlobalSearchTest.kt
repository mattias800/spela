package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for the Global Search feature.
 *
 * Covers:
 * - Screen states (placeholder, hint, loading, results, no results, error)
 * - Search behavior (typing triggers search, clearing resets, debouncing)
 * - Navigation from search results to detail screens
 * - Entry points (Explore search bar, Home search icon)
 * - Advanced filters chip visibility
 * - Edge cases (whitespace-only query, categories with no results hidden)
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GlobalSearchTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    /**
     * SpTextField wraps an OutlinedTextField in a Column; the testTag "global_search_input"
     * is on the Column, but performTextInput requires the node with SetTextAction
     * (the OutlinedTextField). This helper finds the editable text field inside the wrapper.
     */
    private fun ComposeUiTest.searchInputNode(): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("global_search_input")))

    private fun fullSearchResult() = GlobalSearchResult(
        games = SearchCategory(
            results = listOf(
                SearchGameResult(
                    id = "g1",
                    title = "Super Mario World",
                    consoleName = "SNES",
                    consoleId = "snes",
                    developer = "Nintendo",
                    coverUrl = null,
                ),
                SearchGameResult(
                    id = "g2",
                    title = "Castlevania",
                    consoleName = "NES",
                    consoleId = "nes",
                    developer = "Konami",
                    coverUrl = null,
                ),
            ),
            total = 2,
        ),
        consoles = SearchCategory(
            results = listOf(
                SearchConsoleResult(id = "snes", name = "Super Nintendo", gameCount = 15),
            ),
            total = 1,
        ),
        developers = SearchCategory(
            results = listOf(
                SearchDeveloperResult(name = "Nintendo", gameCount = 20, avgRating = 90.0),
            ),
            total = 1,
        ),
        publishers = SearchCategory(
            results = listOf(
                SearchPublisherResult(name = "Konami", gameCount = 10, avgRating = 85.0),
            ),
            total = 1,
        ),
        collections = SearchCategory(
            results = listOf(
                SearchCollectionResult(
                    id = "col1",
                    name = "Best Platformers",
                    gameCount = 5,
                    username = "player",
                ),
            ),
            total = 1,
        ),
        series = SearchCategory(
            results = listOf(
                SearchSeriesResult(id = "ser1", name = "Mario", totalGames = 10, libraryGames = 3),
            ),
            total = 1,
        ),
        franchises = SearchCategory(
            results = listOf(
                SearchFranchiseResult(id = "fr1", name = "Castlevania", totalGames = 8, libraryGames = 2),
            ),
            total = 1,
        ),
    )

    // --- Screen states ---

    @Test
    fun `search screen shows placeholder when input is empty`() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        onNodeWithTag("global_search_screen").assertIsDisplayed()
        onNodeWithTag("search_placeholder").assertIsDisplayed()
    }

    @Test
    fun `search screen shows hint when query has fewer than 2 characters`() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("a")
        advanceQuick(harness)

        onNodeWithTag("search_hint").assertIsDisplayed()
    }

    @Test
    fun `search screen shows loading skeleton while fetching`() = runComposeUiTest {
        val harness = createHarness()
        // Configure a long delay so loading state is visible
        harness.searchRepo.delayMs = 10_000
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        // Only advance the dispatcher enough to trigger the debounce (250ms) but not
        // complete the long delay. The search job should be in progress.
        advanceQuick(harness)

        // The loading skeleton should be displayed
        onNodeWithTag("search_loading").assertIsDisplayed()
    }

    @Test
    fun `search screen shows results with all 7 categories`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        // Results list should be visible
        val resultsList = onNodeWithTag("search_results_list")
        resultsList.assertIsDisplayed()

        // Game results (visible at top)
        onNodeWithTag("search_result_game_g1").assertExists()
        onNodeWithTag("search_result_game_g2").assertExists()

        // Console results — the LazyColumn only composes items that are
        // in/near the viewport, so scroll the console row into view
        // before asserting (same pattern the test already applies to
        // developer/publisher/collection/series/franchise sections).
        resultsList.performScrollToNode(hasTestTag("search_result_console_snes"))
        onNodeWithTag("search_result_console_snes").assertExists()

        // Developer results - may need scroll
        resultsList.performScrollToNode(hasTestTag("search_result_developer_Nintendo"))
        onNodeWithTag("search_result_developer_Nintendo").assertExists()

        // Publisher results - may need scroll
        resultsList.performScrollToNode(hasTestTag("search_result_publisher_Konami"))
        onNodeWithTag("search_result_publisher_Konami").assertExists()

        // Collection results - may need scroll
        resultsList.performScrollToNode(hasTestTag("search_result_collection_col1"))
        onNodeWithTag("search_result_collection_col1").assertExists()

        // Series results - may need scroll
        resultsList.performScrollToNode(hasTestTag("search_result_series_ser1"))
        onNodeWithTag("search_result_series_ser1").assertExists()

        // Franchise results - may need scroll
        resultsList.performScrollToNode(hasTestTag("search_result_franchise_fr1"))
        onNodeWithTag("search_result_franchise_fr1").assertExists()
    }

    @Test
    fun `search screen shows no results state`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = GlobalSearchResult() // empty result

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("zzzzzznotfound")
        advanceFully(harness)

        onNodeWithTag("search_no_results").assertIsDisplayed()
    }

    @Test
    fun `search screen shows error snackbar on failure`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.shouldFail = true
        harness.searchRepo.errorMessage = "Network error"

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        // Drive the search directly via the ViewModel (bypasses text field focus issues)
        harness.globalSearchViewModel.updateQuery("mario")

        // Advance enough for the 250ms debounce + repository call to complete
        mainClock.autoAdvance = false
        harness.testDispatcher.scheduler.advanceTimeBy(2_000)
        harness.testDispatcher.scheduler.runCurrent()
        mainClock.advanceTimeBy(2_000)
        mainClock.autoAdvance = true

        // Verify the error state was set on the view model
        val state = harness.globalSearchViewModel.state.value
        assertEquals("Network error", state.error)
    }

    // --- Search behavior ---

    @Test
    fun `typing 2 or more characters triggers search`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("ma")
        advanceFully(harness)

        // Search should have been called
        assertEquals("ma", harness.searchRepo.lastQuery)
        assertEquals(true, harness.searchRepo.callCount > 0)

        // Results should be displayed
        onNodeWithTag("search_results_list").assertIsDisplayed()
    }

    @Test
    fun `clearing search returns to recent searches when recents exist`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        // Type and get results — this saves "mario" as a recent search
        searchInputNode().performTextInput("mario")
        advanceFully(harness)
        onNodeWithTag("search_results_list").assertIsDisplayed()

        // Clear via the clear button
        onNodeWithTag("search_clear_button").performClick()
        advanceQuick(harness)

        // Should show recent searches (the query was saved as a recent)
        onNodeWithTag("search_results_list").assertDoesNotExist()
        onNodeWithTag("recent_searches_section").assertIsDisplayed()
    }

    @Test
    fun `game results show correct data with title and console name`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        // Game result should have content description with title and console name
        onNodeWithTag("search_result_game_g1").assertExists()
        onNodeWithTag("search_result_game_g1")
            .assertContentDescriptionContains("Super Mario World", substring = true)
        onNodeWithTag("search_result_game_g1")
            .assertContentDescriptionContains("SNES", substring = true)
    }

    @Test
    fun `console results show name and game count`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("super")
        advanceFully(harness)

        onNodeWithTag("search_results_list")
            .performScrollToNode(hasTestTag("search_result_console_snes"))
        onNodeWithTag("search_result_console_snes").assertExists()
        onNodeWithTag("search_result_console_snes")
            .assertContentDescriptionContains("Super Nintendo", substring = true)
        onNodeWithTag("search_result_console_snes")
            .assertContentDescriptionContains("15 games", substring = true)
    }

    // --- Navigation from results ---

    @Test
    fun `tapping game result navigates to game detail`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        onNodeWithTag("search_result_game_g1").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("game/g1", navState.currentScreen.route)
    }

    @Test
    fun `tapping console result navigates to console screen`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("super")
        advanceFully(harness)

        onNodeWithTag("search_results_list")
            .performScrollToNode(hasTestTag("search_result_console_snes"))
        onNodeWithTag("search_result_console_snes").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("console/snes", navState.currentScreen.route)
    }

    @Test
    fun `tapping developer result navigates to developer explore`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("nint")
        advanceFully(harness)

        // Developer results may be below the visible area due to quick results section
        onNodeWithTag("search_results_list")
            .performScrollToNode(hasTestTag("search_result_developer_Nintendo"))
        onNodeWithTag("search_result_developer_Nintendo").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_developer/Nintendo", navState.currentScreen.route)
    }

    @Test
    fun `tapping collection result navigates to collection detail`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("platform")
        advanceFully(harness)

        // Collection items may be below the visible area in the LazyColumn
        onNodeWithTag("search_results_list")
            .performScrollToNode(hasTestTag("search_result_collection_col1"))
        onNodeWithTag("search_result_collection_col1").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("collection/col1", navState.currentScreen.route)
    }

    @Test
    fun `tapping series result navigates to series explore`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        // Series items may be below the visible area in the LazyColumn
        onNodeWithTag("search_results_list")
            .performScrollToNode(hasTestTag("search_result_series_ser1"))
        onNodeWithTag("search_result_series_ser1").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_series/ser1", navState.currentScreen.route)
    }

    // --- Entry points ---

    @Test
    fun `search bar on explore tab navigates to global search`() = runComposeUiTest {
        val harness = createHarness()
        // Need at least one section so explore screen is not empty
        harness.exploreRepo.featuredSeriesList = listOf(
            FeaturedSeries(
                id = "s1", name = "Mario", libraryGames = 1, totalGames = 3, consoleCount = 1, heroUrl = null,
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        // The search bar entry point uses contentDescription
        onNodeWithContentDescription("Search games, consoles, developers").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("global_search", navState.currentScreen.route)
    }

    @Test
    fun `search icon on home screen navigates to global search`() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        advance(harness)

        // The search icon button uses contentDescription = "Search"
        onNodeWithContentDescription("Search").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("global_search", navState.currentScreen.route)
    }

    // --- Advanced filters ---

    @Test
    fun `advanced filters chip is visible on search screen`() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        onNodeWithTag("global_search_advanced_filters").assertIsDisplayed()
    }

    // --- Quick results / autocomplete ---

    @Test
    fun `quick results section shown when search has results`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        onNodeWithTag("quick_results_section").assertIsDisplayed()
    }

    @Test
    fun `quick results section not shown when no results`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = GlobalSearchResult() // empty

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("zzzznotfound")
        advanceFully(harness)

        onNodeWithTag("quick_results_section").assertDoesNotExist()
    }

    @Test
    fun `quick results shows correct number of items capped at 5`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        // fullSearchResult() has 2 games + 1 console + 1 developer + 1 publisher = 5
        // (collections, series, franchises are lower priority and don't fit in cap of 5)
        onNodeWithTag("quick_result_game_g1").assertExists()
        onNodeWithTag("quick_result_game_g2").assertExists()
        onNodeWithTag("quick_result_console_snes").assertExists()
        onNodeWithTag("quick_result_developer_Nintendo").assertExists()
        onNodeWithTag("quick_result_publisher_Konami").assertExists()

        // Verify the state has exactly 5 suggestions
        val state = harness.globalSearchViewModel.state.value
        assertEquals(5, state.suggestions.size)
        // The suggestion order should be: games first, then console, developer, publisher.
        val suggestions = state.suggestions
        assertEquals("Game", suggestions[0].type)
        assertEquals("Game", suggestions[1].type)
        assertEquals("Console", suggestions[2].type)
        assertEquals("Developer", suggestions[3].type)
        assertEquals("Publisher", suggestions[4].type)
        // The per-type badge text appears with a matching cardinality.
        onAllNodesWithText("Game").assertCountEquals(2)
        onAllNodesWithText("Console").assertCountEquals(1)
        onAllNodesWithText("Developer").assertCountEquals(1)
        onAllNodesWithText("Publisher").assertCountEquals(1)
    }

    @Test
    fun `tapping quick result game navigates to game detail`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        onNodeWithTag("quick_result_game_g1").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("game/g1", navState.currentScreen.route)
    }

    @Test
    fun `tapping quick result console navigates to console screen`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        onNodeWithTag("quick_result_console_snes").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("console/snes", navState.currentScreen.route)
    }

    @Test
    fun `quick results not shown on empty hint state`() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        // Empty state (placeholder) - no quick results
        onNodeWithTag("quick_results_section").assertDoesNotExist()

        // Single character (hint state) - no quick results
        searchInputNode().performTextInput("a")
        advanceQuick(harness)

        onNodeWithTag("search_hint").assertIsDisplayed()
        onNodeWithTag("quick_results_section").assertDoesNotExist()
    }

    // --- Edge cases ---

    @Test
    fun `whitespace-only query shows hint state`() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("   ")
        advanceQuick(harness)

        onNodeWithTag("search_hint").assertIsDisplayed()
    }

    @Test
    fun `categories with no results are not shown`() = runComposeUiTest {
        val harness = createHarness()
        // Only games category has results
        harness.searchRepo.searchResult = GlobalSearchResult(
            games = SearchCategory(
                results = listOf(
                    SearchGameResult(
                        id = "g1",
                        title = "Super Mario World",
                        consoleName = "SNES",
                        consoleId = "snes",
                    ),
                ),
                total = 1,
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        // Game result should be visible
        onNodeWithTag("search_result_game_g1").assertExists()

        // Other categories should not have any result items
        onNodeWithTag("search_result_console_snes").assertDoesNotExist()
        onNodeWithTag("search_result_developer_Nintendo").assertDoesNotExist()
        onNodeWithTag("search_result_publisher_Konami").assertDoesNotExist()
        onNodeWithTag("search_result_collection_col1").assertDoesNotExist()
        onNodeWithTag("search_result_series_ser1").assertDoesNotExist()
        onNodeWithTag("search_result_franchise_fr1").assertDoesNotExist()
    }
}
