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

    private fun ComposeUiTest.searchInputNode(): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("global_search_input")))

    private fun ComposeUiTest.showGlobalSearch(harness: SpelaTestHarness) {
        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)
    }

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

    @Test
    fun searchScreenInitialHintAndWhitespaceStates() = runComposeUiTest {
        val harness = createHarness()

        showGlobalSearch(harness)

        onNodeWithTag("global_search_screen").assertIsDisplayed()
        onNodeWithTag("search_placeholder").assertIsDisplayed()
        onNodeWithTag("global_search_advanced_filters").assertIsDisplayed()
        onNodeWithTag("quick_results_section").assertDoesNotExist()

        searchInputNode().performTextInput("a")
        advanceQuick(harness)

        onNodeWithTag("search_hint").assertIsDisplayed()
        onNodeWithTag("quick_results_section").assertDoesNotExist()

        searchInputNode().performTextClearance()
        searchInputNode().performTextInput("   ")
        advanceQuick(harness)

        onNodeWithTag("search_hint").assertIsDisplayed()
    }

    @Test
    fun searchScreenShowsLoadingSkeletonWhileFetching() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.delayMs = 10_000
        harness.searchRepo.searchResult = fullSearchResult()

        showGlobalSearch(harness)

        searchInputNode().performTextInput("mario")
        advanceQuick(harness)

        onNodeWithTag("search_loading").assertIsDisplayed()
    }

    @Test
    fun searchScreenShowsResultsCategoriesQuickResultsAndGameData() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        showGlobalSearch(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        assertEquals("mario", harness.searchRepo.lastQuery)
        assertEquals(true, harness.searchRepo.callCount > 0)

        val resultsList = onNodeWithTag("search_results_list")
        resultsList.assertIsDisplayed()
        onNodeWithTag("quick_results_section").assertIsDisplayed()

        onNodeWithTag("search_result_game_g1").assertExists()
        onNodeWithTag("search_result_game_g2").assertExists()
        onNodeWithTag("search_result_game_g1")
            .assertContentDescriptionContains("Super Mario World", substring = true)
        onNodeWithTag("search_result_game_g1")
            .assertContentDescriptionContains("SNES", substring = true)

        onNodeWithTag("quick_result_game_g1").assertExists()
        onNodeWithTag("quick_result_game_g2").assertExists()
        onNodeWithTag("quick_result_console_snes").assertExists()
        onNodeWithTag("quick_result_developer_Nintendo").assertExists()
        onNodeWithTag("quick_result_publisher_Konami").assertExists()

        val suggestions = harness.globalSearchViewModel.state.value.suggestions
        assertEquals(5, suggestions.size)
        assertEquals("Game", suggestions[0].type)
        assertEquals("Game", suggestions[1].type)
        assertEquals("Console", suggestions[2].type)
        assertEquals("Developer", suggestions[3].type)
        assertEquals("Publisher", suggestions[4].type)
        onAllNodesWithText("Game").assertCountEquals(2)
        onAllNodesWithText("Console").assertCountEquals(1)
        onAllNodesWithText("Developer").assertCountEquals(1)
        onAllNodesWithText("Publisher").assertCountEquals(1)

        resultsList.performScrollToNode(hasTestTag("search_result_console_snes"))
        onNodeWithTag("search_result_console_snes").assertExists()
        onNodeWithTag("search_result_console_snes")
            .assertContentDescriptionContains("Super Nintendo", substring = true)
        onNodeWithTag("search_result_console_snes")
            .assertContentDescriptionContains("15 games", substring = true)

        resultsList.performScrollToNode(hasTestTag("search_result_developer_Nintendo"))
        onNodeWithTag("search_result_developer_Nintendo").assertExists()

        resultsList.performScrollToNode(hasTestTag("search_result_publisher_Konami"))
        onNodeWithTag("search_result_publisher_Konami").assertExists()

        resultsList.performScrollToNode(hasTestTag("search_result_collection_col1"))
        onNodeWithTag("search_result_collection_col1").assertExists()

        resultsList.performScrollToNode(hasTestTag("search_result_series_ser1"))
        onNodeWithTag("search_result_series_ser1").assertExists()

        resultsList.performScrollToNode(hasTestTag("search_result_franchise_fr1"))
        onNodeWithTag("search_result_franchise_fr1").assertExists()
    }

    @Test
    fun searchScreenShowsNoResultsWithoutQuickResults() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = GlobalSearchResult()

        showGlobalSearch(harness)

        searchInputNode().performTextInput("zzzzzznotfound")
        advanceFully(harness)

        onNodeWithTag("search_no_results").assertIsDisplayed()
        onNodeWithTag("quick_results_section").assertDoesNotExist()
    }

    @Test
    fun searchScreenSetsErrorOnFailure() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.shouldFail = true
        harness.searchRepo.errorMessage = "Network error"

        showGlobalSearch(harness)

        harness.globalSearchViewModel.updateQuery("mario")

        mainClock.autoAdvance = false
        advanceHarnessSchedulerByOnUiThread(harness, 2_000)
        mainClock.advanceTimeBy(2_000)
        mainClock.autoAdvance = true

        onNodeWithText("Network error").assertIsDisplayed()
        onNodeWithText("Dismiss").assertIsDisplayed()

        val state = harness.globalSearchViewModel.state.value
        assertEquals("Network error", state.error)
    }

    @Test
    fun clearingSearchReturnsToRecentSearchesWhenRecentsExist() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        showGlobalSearch(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)
        onNodeWithTag("search_results_list").assertIsDisplayed()

        onNodeWithTag("search_clear_button").performClick()
        advanceQuick(harness)

        onNodeWithTag("search_results_list").assertDoesNotExist()
        onNodeWithTag("recent_searches_section").assertIsDisplayed()
    }

    @Test
    fun tappingGameResultNavigatesToGameDetail() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        showGlobalSearch(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        onNodeWithTag("search_result_game_g1").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("game/g1", navState.currentScreen.route)
    }

    @Test
    fun tappingConsoleResultNavigatesToConsoleScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        showGlobalSearch(harness)

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
    fun tappingDeveloperResultNavigatesToDeveloperExplore() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        showGlobalSearch(harness)

        searchInputNode().performTextInput("nint")
        advanceFully(harness)

        onNodeWithTag("search_results_list")
            .performScrollToNode(hasTestTag("search_result_developer_Nintendo"))
        onNodeWithTag("search_result_developer_Nintendo").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_developer/Nintendo", navState.currentScreen.route)
    }

    @Test
    fun tappingCollectionResultNavigatesToCollectionDetail() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        showGlobalSearch(harness)

        searchInputNode().performTextInput("platform")
        advanceFully(harness)

        onNodeWithTag("search_results_list")
            .performScrollToNode(hasTestTag("search_result_collection_col1"))
        onNodeWithTag("search_result_collection_col1").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("collection/col1", navState.currentScreen.route)
    }

    @Test
    fun tappingSeriesResultNavigatesToSeriesExplore() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        showGlobalSearch(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        onNodeWithTag("search_results_list")
            .performScrollToNode(hasTestTag("search_result_series_ser1"))
        onNodeWithTag("search_result_series_ser1").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_series/ser1", navState.currentScreen.route)
    }

    @Test
    fun searchEntryPointsNavigateToGlobalSearch() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredSeriesList = listOf(
            FeaturedSeries(
                id = "s1", name = "Mario", libraryGames = 1, totalGames = 3, consoleCount = 1, heroUrl = null,
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        advance(harness)

        onNodeWithContentDescription("Search").performClick()
        advance(harness)
        assertEquals("global_search", harness.navigationViewModel.state.value.currentScreen.route)

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithContentDescription("Search games, consoles, developers").performClick()
        advance(harness)
        assertEquals("global_search", harness.navigationViewModel.state.value.currentScreen.route)
    }

    @Test
    fun tappingQuickResultGameNavigatesToGameDetail() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        showGlobalSearch(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        onNodeWithTag("quick_result_game_g1").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("game/g1", navState.currentScreen.route)
    }

    @Test
    fun tappingQuickResultConsoleNavigatesToConsoleScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = fullSearchResult()

        showGlobalSearch(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        onNodeWithTag("quick_result_console_snes").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("console/snes", navState.currentScreen.route)
    }

    @Test
    fun categoriesWithNoResultsAreNotShown() = runComposeUiTest {
        val harness = createHarness()
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

        showGlobalSearch(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        onNodeWithTag("search_result_game_g1").assertExists()
        onNodeWithTag("search_result_console_snes").assertDoesNotExist()
        onNodeWithTag("search_result_developer_Nintendo").assertDoesNotExist()
        onNodeWithTag("search_result_publisher_Konami").assertDoesNotExist()
        onNodeWithTag("search_result_collection_col1").assertDoesNotExist()
        onNodeWithTag("search_result_series_ser1").assertDoesNotExist()
        onNodeWithTag("search_result_franchise_fr1").assertDoesNotExist()
    }
}
