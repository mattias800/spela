package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Desktop E2E tests for Phase 2 Story 8: "See More" / "Show Less".
 *
 * Covers:
 * - "See all X" text shown when total > displayed count
 * - "See all" not shown when total <= displayed count
 * - Tapping "See all" expands the category
 * - "Show less" shown after expanding
 * - Tapping "Show less" collapses back
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SearchSeeMoreTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.searchInputNode(): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("global_search_input")))

    /**
     * Search result where games total (10) > displayed (2), so "See all 10" should show.
     * Consoles has total == displayed count (1), so "See all" should NOT show.
     */
    private fun searchResultWithMoreGames() = GlobalSearchResult(
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
                    title = "Super Mario Bros.",
                    consoleName = "NES",
                    consoleId = "nes",
                    developer = "Nintendo",
                    coverUrl = null,
                ),
            ),
            total = 10, // More than the 2 displayed
        ),
        consoles = SearchCategory(
            results = listOf(
                SearchConsoleResult(id = "snes", name = "Super Nintendo", gameCount = 15),
            ),
            total = 1, // Same as displayed count
        ),
    )

    /**
     * Expanded search result with limit=50 — more games returned.
     */
    private fun expandedSearchResult() = GlobalSearchResult(
        games = SearchCategory(
            results = listOf(
                SearchGameResult(id = "g1", title = "Super Mario World", consoleName = "SNES", consoleId = "snes", coverUrl = null),
                SearchGameResult(id = "g2", title = "Super Mario Bros.", consoleName = "NES", consoleId = "nes", coverUrl = null),
                SearchGameResult(id = "g3", title = "Super Mario 64", consoleName = "N64", consoleId = "n64", coverUrl = null),
                SearchGameResult(id = "g4", title = "Super Mario Sunshine", consoleName = "GCN", consoleId = "gcn", coverUrl = null),
                SearchGameResult(id = "g5", title = "Super Mario Galaxy", consoleName = "Wii", consoleId = "wii", coverUrl = null),
                SearchGameResult(id = "g6", title = "Super Mario Land", consoleName = "GB", consoleId = "gb", coverUrl = null),
                SearchGameResult(id = "g7", title = "Super Mario RPG", consoleName = "SNES", consoleId = "snes", coverUrl = null),
            ),
            total = 10,
        ),
        consoles = SearchCategory(
            results = listOf(
                SearchConsoleResult(id = "snes", name = "Super Nintendo", gameCount = 15),
            ),
            total = 1,
        ),
    )

    @Test
    fun `see all visibility follows total versus displayed count`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = searchResultWithMoreGames()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        // "See all 10" should be visible for Games (total 10 > displayed 2)
        onNodeWithTag("search_section_see_all_Games").assertExists()
        // Consoles has total == 1 == displayed count, so "See all" should NOT show
        onNodeWithTag("search_section_see_all_Consoles").assertDoesNotExist()
    }

    @Test
    fun `see all expands category and show less collapses it`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = searchResultWithMoreGames()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("mario")
        advanceFully(harness)

        // Initially only 2 game results
        onNodeWithTag("search_result_game_g1").assertExists()
        onNodeWithTag("search_result_game_g2").assertExists()
        onNodeWithTag("search_result_game_g3").assertDoesNotExist()

        // Tap "See all 10"
        onNodeWithTag("search_section_see_all_Games").performClick()
        harness.searchRepo.searchResult = expandedSearchResult()
        advanceFully(harness)

        // Verify the category is in expanded state on the ViewModel
        val expandedState = harness.globalSearchViewModel.state.value
        assertTrue(expandedState.expandedCategories.contains("Games"))
        onNodeWithTag("search_result_game_g3").assertExists()
        onNodeWithTag("search_section_show_less_Games").assertExists()
        onNodeWithTag("search_section_see_all_Games").assertDoesNotExist()

        // Click "Show less"
        onNodeWithTag("search_section_show_less_Games").performClick()
        advanceQuick(harness)

        // Category should be collapsed
        val collapsedState = harness.globalSearchViewModel.state.value
        assertTrue(!collapsedState.expandedCategories.contains("Games"))

        // "See all" should reappear
        onNodeWithTag("search_section_see_all_Games").assertExists()
        onNodeWithTag("search_section_show_less_Games").assertDoesNotExist()
    }
}
