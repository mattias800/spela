package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameFilters
import com.spela.player.domain.model.SavedSearch
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for Phase 13: Advanced Search & Multi-Faceted Filtering.
 *
 * Covers:
 * - Search chip on Explore screen navigates to search
 * - Filter panel renders with all filter sections
 * - Saved searches render and can be applied
 * - Filter selections update state correctly
 * - Search results grid renders games
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreSearchTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleGames = listOf(
        Game(
            id = "g1",
            title = "Super Mario World",
            consoleId = "snes",
            consoleName = "SNES",
            coverUrl = null,
            rating = 94.0,
            genre = "Platform",
            developer = "Nintendo",
            publisher = "Nintendo",
        ),
        Game(
            id = "g2",
            title = "Castlevania",
            consoleId = "nes",
            consoleName = "NES",
            coverUrl = null,
            rating = 80.0,
            genre = "Action",
            developer = "Konami",
            publisher = "Konami",
        ),
    )

    private val sampleSavedSearches = listOf(
        SavedSearch(
            id = "ss1",
            name = "My SNES RPGs",
            filters = mapOf("consoles" to "SNES", "genres" to "RPG"),
            createdAt = "2026-03-10T00:00:00Z",
        ),
        SavedSearch(
            id = "ss2",
            name = "High Rated NES",
            filters = mapOf("consoles" to "NES", "ratingMin" to "80"),
            createdAt = "2026-03-09T00:00:00Z",
        ),
    )

    // --- Search bar on Explore screen ---

    @Test
    fun searchBarRendersOnExploreScreen() = runComposeUiTest {
        val harness = createHarness()
        // Need at least one section so explore screen is not empty
        harness.exploreRepo.featuredSeriesList = listOf(
            com.spela.player.domain.model.FeaturedSeries(
                id = "s1", name = "Mario", libraryGames = 1, totalGames = 3, consoleCount = 1, heroUrl = null,
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_search_bar").assertExists()
    }

    @Test
    fun searchBarNavigatesToGlobalSearchScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredSeriesList = listOf(
            com.spela.player.domain.model.FeaturedSeries(
                id = "s1", name = "Mario", libraryGames = 1, totalGames = 3, consoleCount = 1, heroUrl = null,
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_search_bar").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("global_search", navState.currentScreen.route)
    }

    // --- Search screen renders ---

    @Test
    fun searchScreenShowsEmptyState() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        onNodeWithTag("explore_search_screen").assertIsDisplayed()
        onNodeWithTag("search_empty_state").assertIsDisplayed()
    }

    // --- Filter panel ---

    @Test
    fun filterPanelTogglesVisibility() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        // Filter panel should not be visible initially
        onNodeWithTag("game_filter_panel").assertDoesNotExist()

        // Toggle filter panel
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advanceQuick(harness)

        onNodeWithTag("game_filter_panel").assertIsDisplayed()

        // Toggle again to hide
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advanceQuick(harness)

        onNodeWithTag("game_filter_panel").assertDoesNotExist()
    }

    @Test
    fun filterPanelRendersAllSections() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        // Open filter panel
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advanceQuick(harness)

        // Verify all filter sections exist
        onNodeWithTag("filter_search_field").assertExists()
        onNodeWithTag("filter_consoles_chips").assertExists()
        onNodeWithTag("filter_genres_chips").assertExists()
        onNodeWithTag("filter_developer_field").assertExists()
        onNodeWithTag("filter_publisher_field").assertExists()
        onNodeWithTag("filter_year_min_field").assertExists()
        onNodeWithTag("filter_year_max_field").assertExists()
        onNodeWithTag("filter_rating_min_field").assertExists()
        onNodeWithTag("filter_rating_max_field").assertExists()
        onNodeWithTag("filter_play_status_chips").assertExists()
        onNodeWithTag("filter_sort_chips").assertExists()
        onNodeWithTag("apply_filters_button").assertExists()
        onNodeWithTag("clear_filters_button").assertExists()
    }

    @Test
    fun consoleChipSelectionWorks() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        // Open filter panel
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advanceQuick(harness)

        // Click SNES chip
        onNodeWithTag("filter_console_chip_SNES").performClick()
        advanceQuick(harness)

        // Verify filter state updated
        val filters = harness.exploreViewModel.gameSearchState.value.filters
        assertEquals(listOf("SNES"), filters.consoles)

        // Click NES chip too
        onNodeWithTag("filter_console_chip_NES").performClick()
        advanceQuick(harness)

        val filters2 = harness.exploreViewModel.gameSearchState.value.filters
        assertEquals(listOf("SNES", "NES"), filters2.consoles)

        // Toggle SNES off
        onNodeWithTag("filter_console_chip_SNES").performClick()
        advanceQuick(harness)

        val filters3 = harness.exploreViewModel.gameSearchState.value.filters
        assertEquals(listOf("NES"), filters3.consoles)
    }

    @Test
    fun genreChipSelectionWorks() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        // Open filter panel
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advanceQuick(harness)

        // Click RPG chip
        onNodeWithTag("filter_genre_chip_RPG").performClick()
        advanceQuick(harness)

        val filters = harness.exploreViewModel.gameSearchState.value.filters
        assertEquals(listOf("RPG"), filters.genres)
    }

    @Test
    fun playStatusChipSelectionWorks() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        // Open filter panel
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advanceQuick(harness)

        // Select "unplayed"
        onNodeWithTag("filter_play_status_chip_unplayed").performClick()
        advanceQuick(harness)

        val filters = harness.exploreViewModel.gameSearchState.value.filters
        assertEquals("unplayed", filters.playStatus)

        // Clicking same chip deselects
        onNodeWithTag("filter_play_status_chip_unplayed").performClick()
        advanceQuick(harness)

        val filters2 = harness.exploreViewModel.gameSearchState.value.filters
        assertEquals("", filters2.playStatus)
    }

    // --- Search results ---

    @Test
    fun applyFiltersShowsResults() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.filteredGames = sampleGames

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        // Open filter panel and set a filter
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advanceQuick(harness)

        onNodeWithTag("filter_console_chip_SNES").performClick()
        advanceQuick(harness)

        // Scroll to and click the apply button (it may be below the visible scroll area)
        onNodeWithTag("apply_filters_button").performScrollTo()
        onNodeWithTag("apply_filters_button").performClick()
        advanceFully(harness)

        // Filter panel should auto-collapse after applying
        onNodeWithTag("game_filter_panel").assertDoesNotExist()

        // Results should be visible
        val searchState = harness.exploreViewModel.gameSearchState.value
        assertEquals(false, searchState.isLoading)
        assertEquals(2, searchState.results.size)
        onNodeWithTag("search_results_grid").assertIsDisplayed()
        onNodeWithTag("search_results_count").assertIsDisplayed()
        onNodeWithTag("search_result_game_g1").assertExists()
        onNodeWithTag("search_result_game_g2").assertExists()
    }

    // --- Saved searches ---

    @Test
    fun savedSearchesRender() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.savedSearchesList = sampleSavedSearches.toMutableList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        // Open filter panel
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advance(harness)

        // Saved searches should be visible
        onNodeWithTag("saved_searches_title").assertIsDisplayed()
        onNodeWithTag("saved_searches_list").assertIsDisplayed()
        onNodeWithTag("saved_search_chip_ss1").assertExists()
        onNodeWithTag("saved_search_chip_ss2").assertExists()
    }

    @Test
    fun savedSearchCanBeApplied() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.savedSearchesList = sampleSavedSearches.toMutableList()
        harness.exploreRepo.filteredGames = sampleGames

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        // Open filter panel
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advance(harness)

        // Click saved search chip
        onNodeWithTag("saved_search_chip_ss1").performClick()
        advance(harness)

        // Filters should be applied from saved search
        val filters = harness.exploreViewModel.gameSearchState.value.filters
        assertEquals(listOf("SNES"), filters.consoles)
        assertEquals(listOf("RPG"), filters.genres)
    }

    @Test
    fun savedSearchCanBeDeleted() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.savedSearchesList = sampleSavedSearches.toMutableList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        // Open filter panel
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advance(harness)

        // Delete saved search
        onNodeWithTag("delete_saved_search_ss1").performClick()
        advance(harness)

        // Should be removed
        onNodeWithTag("saved_search_chip_ss1").assertDoesNotExist()
        onNodeWithTag("saved_search_chip_ss2").assertExists()
    }

    // --- Clear filters ---

    @Test
    fun clearFiltersResetsState() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.filteredGames = sampleGames

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ExploreSearch))
        advance(harness)

        // Open filter panel and set filters
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advanceQuick(harness)

        onNodeWithTag("filter_console_chip_SNES").performClick()
        advanceQuick(harness)

        // Scroll to and click apply button (may be below visible scroll area)
        onNodeWithTag("apply_filters_button").performScrollTo()
        onNodeWithTag("apply_filters_button").performClick()
        advanceFully(harness)

        // Results should be visible (filter panel auto-collapses on apply)
        val searchState = harness.exploreViewModel.gameSearchState.value
        assertEquals(false, searchState.isLoading)
        assertEquals(2, searchState.results.size)
        onNodeWithTag("search_results_grid").assertIsDisplayed()

        // Re-open filter panel to access clear button
        onNodeWithTag("toggle_filter_panel_button").performClick()
        advanceQuick(harness)

        // Scroll to and click clear button
        onNodeWithTag("clear_filters_button").performScrollTo()
        onNodeWithTag("clear_filters_button").performClick()
        advanceQuick(harness)

        // Filters should be reset
        val filters = harness.exploreViewModel.gameSearchState.value.filters
        assertEquals(true, filters.isEmpty)
        assertEquals(emptyList(), harness.exploreViewModel.gameSearchState.value.results)
    }
}
