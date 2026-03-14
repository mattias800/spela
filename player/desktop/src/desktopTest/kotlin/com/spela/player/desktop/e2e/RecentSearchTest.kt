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
 * Desktop E2E tests for Phase 2 Story 6: Recent Searches.
 *
 * Covers:
 * - Recent searches section shown when query is empty and recents exist
 * - Tapping a recent search fills query and triggers search
 * - Remove button removes individual recent search
 * - "Clear all" clears all recent searches
 * - Recent searches hidden when no recents exist
 * - Recent searches hidden when query has text
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class RecentSearchTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    /**
     * Populate recent searches on the fake repo and force the ViewModel to reload.
     *
     * The GlobalSearchViewModel reads recent searches in its init block (before
     * the test can set up data). Calling clearSearch() re-reads from the repo.
     */
    private fun SpelaTestHarness.setRecentSearches(queries: List<String>) {
        searchRepo.storedRecentSearches = queries.toMutableList()
        globalSearchViewModel.clearSearch()
    }

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
            ),
            total = 1,
        ),
    )

    // --- Recent searches section visibility ---

    @Test
    fun `recent searches section shown when query is empty and recents exist`() = runComposeUiTest {
        val harness = createHarness()
        harness.setRecentSearches(listOf("mario", "zelda", "metroid"))

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        onNodeWithTag("recent_searches_section").assertIsDisplayed()
        onNodeWithTag("recent_search_item_mario").assertIsDisplayed()
        onNodeWithTag("recent_search_item_zelda").assertIsDisplayed()
        onNodeWithTag("recent_search_item_metroid").assertIsDisplayed()
    }

    @Test
    fun `recent searches section hidden when no recents exist`() = runComposeUiTest {
        val harness = createHarness()
        // No recent searches set — default is empty

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        onNodeWithTag("recent_searches_section").assertDoesNotExist()
        // Should show placeholder instead
        onNodeWithTag("search_placeholder").assertIsDisplayed()
    }

    @Test
    fun `recent searches section hidden when query has text`() = runComposeUiTest {
        val harness = createHarness()
        harness.setRecentSearches(listOf("mario", "zelda"))
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        // Initially, recent searches should be visible
        onNodeWithTag("recent_searches_section").assertIsDisplayed()

        // Type a query
        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        // Recent searches section should be hidden
        onNodeWithTag("recent_searches_section").assertDoesNotExist()
    }

    // --- Interacting with recent searches ---

    @Test
    fun `tapping a recent search fills query and triggers search`() = runComposeUiTest {
        val harness = createHarness()
        harness.setRecentSearches(listOf("mario", "zelda"))
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        // Tap the "mario" recent search
        onNodeWithTag("recent_search_item_mario").performClick()
        advanceFully(harness)

        // The query should be updated
        val state = harness.globalSearchViewModel.state.value
        assertEquals("mario", state.query)

        // Search should have been triggered
        assertEquals("mario", harness.searchRepo.lastQuery)
        assert(harness.searchRepo.callCount > 0) { "Search should have been called" }

        // Results should be displayed
        onNodeWithTag("search_results_list").assertIsDisplayed()
    }

    @Test
    fun `remove button removes individual recent search`() = runComposeUiTest {
        val harness = createHarness()
        harness.setRecentSearches(listOf("mario", "zelda", "metroid"))

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        // All three should be visible
        onNodeWithTag("recent_search_item_mario").assertIsDisplayed()
        onNodeWithTag("recent_search_item_zelda").assertIsDisplayed()
        onNodeWithTag("recent_search_item_metroid").assertIsDisplayed()

        // Remove "zelda"
        onNodeWithTag("recent_search_remove_zelda").performClick()
        advanceQuick(harness)

        // "zelda" should be gone, others remain
        onNodeWithTag("recent_search_item_mario").assertIsDisplayed()
        onNodeWithTag("recent_search_item_zelda").assertDoesNotExist()
        onNodeWithTag("recent_search_item_metroid").assertIsDisplayed()

        // Verify the underlying store was updated
        assertEquals(listOf("mario", "metroid"), harness.searchRepo.storedRecentSearches)
    }

    @Test
    fun `clear all clears all recent searches`() = runComposeUiTest {
        val harness = createHarness()
        harness.setRecentSearches(listOf("mario", "zelda", "metroid"))

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        // Recent searches should be visible
        onNodeWithTag("recent_searches_section").assertIsDisplayed()

        // Tap "Clear all"
        onNodeWithTag("recent_searches_clear_all").performClick()
        advanceQuick(harness)

        // Recent searches section should be hidden
        onNodeWithTag("recent_searches_section").assertDoesNotExist()

        // Should show placeholder since query is empty and no recents
        onNodeWithTag("search_placeholder").assertIsDisplayed()

        // Verify underlying store is empty
        assertEquals(emptyList<String>(), harness.searchRepo.storedRecentSearches)
    }

    @Test
    fun `recent searches show up to 5 items`() = runComposeUiTest {
        val harness = createHarness()
        harness.setRecentSearches(listOf("mario", "zelda", "metroid", "kirby", "pokemon"))

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        onNodeWithTag("recent_search_item_mario").assertExists()
        onNodeWithTag("recent_search_item_zelda").assertExists()
        onNodeWithTag("recent_search_item_metroid").assertExists()
        onNodeWithTag("recent_search_item_kirby").assertExists()
        onNodeWithTag("recent_search_item_pokemon").assertExists()
    }

    @Test
    fun `recent searches reappear after clearing search`() = runComposeUiTest {
        val harness = createHarness()
        harness.setRecentSearches(listOf("mario"))
        harness.searchRepo.searchResult = fullSearchResult()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        // Recent searches visible initially
        onNodeWithTag("recent_searches_section").assertIsDisplayed()

        // Type a query to trigger search
        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        // Recent searches gone, results shown
        onNodeWithTag("recent_searches_section").assertDoesNotExist()
        onNodeWithTag("search_results_list").assertIsDisplayed()

        // Clear the search via the clear button
        onNodeWithTag("search_clear_button").performClick()
        advanceQuick(harness)

        // Recent searches should reappear (now with "mario" still in the list)
        onNodeWithTag("recent_searches_section").assertIsDisplayed()
    }
}
