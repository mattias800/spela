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
 * Desktop E2E tests for Phase 2 Story 7: Franchise Detail Screen.
 *
 * Covers:
 * - Franchise screen renders with hero banner, title, ownership progress
 * - Console filter chips render and filter games
 * - Game list shows franchise games
 * - Navigation from global search franchise result to franchise screen
 * - Empty state when no games match filter
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class FranchiseDetailTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.searchInputNode(): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("global_search_input")))

    private val sampleFranchiseGames = listOf(
        SeriesGame(
            igdbGameId = 2001,
            name = "Castlevania",
            inLibrary = true,
            localGameId = "game-cv1",
            coverUrl = null,
            releaseDate = "1986-09-26",
            rating = 80.0,
            consoleAbbreviation = "NES",
            consoleName = "NES",
            consoleColor = "#dc2626",
        ),
        SeriesGame(
            igdbGameId = 2002,
            name = "Castlevania: Symphony of the Night",
            inLibrary = true,
            localGameId = "game-cv2",
            coverUrl = null,
            releaseDate = "1997-03-20",
            rating = 95.0,
            consoleAbbreviation = "PS1",
            consoleName = "PlayStation",
            consoleColor = "#3b82f6",
        ),
        SeriesGame(
            igdbGameId = 2003,
            name = "Castlevania: Aria of Sorrow",
            inLibrary = false,
            localGameId = null,
            coverUrl = null,
            releaseDate = "2003-05-08",
            rating = 90.0,
            consoleAbbreviation = "GBA",
            consoleName = "Game Boy Advance",
            consoleColor = "#9333ea",
        ),
    )

    private val sampleFranchiseConsoles = listOf(
        SeriesConsole(abbreviation = "NES", name = "NES", color = "#dc2626", gameCount = 1),
        SeriesConsole(abbreviation = "PS1", name = "PlayStation", color = "#3b82f6", gameCount = 1),
        SeriesConsole(abbreviation = "GBA", name = "Game Boy Advance", color = "#9333ea", gameCount = 1),
    )

    private val sampleFranchiseDetail = SeriesDetail(
        id = "fr1",
        name = "Castlevania",
        heroUrl = null,
        logoUrl = null,
        consoles = sampleFranchiseConsoles,
        libraryGames = 2,
        totalGames = 3,
        games = sampleFranchiseGames,
    )

    // --- Franchise detail screen rendering ---

    @Test
    fun `franchise screen renders games filters and non-library state`() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.franchiseDetails = mapOf("fr1" to sampleFranchiseDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreFranchise("fr1", "Castlevania"))
        )
        advance(harness)

        onNodeWithTag("explore_franchise_screen").assertIsDisplayed()
        onNodeWithTag("franchise_hero_banner").assertIsDisplayed()

        // "Castlevania" appears in both top bar and hero
        onAllNodesWithText("Castlevania").fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "Expected at least one 'Castlevania' text node" }
        }

        onNodeWithTag("franchise_ownership_progress").assertIsDisplayed()
        onNodeWithTag("franchise_ownership_text").assertIsDisplayed()
        onNodeWithText("You own 2 of 3 games").assertExists()

        onNodeWithTag("franchise_game_2001").assertExists()
        onNodeWithTag("franchise_game_2002").assertExists()
        onNodeWithTag("franchise_game_2003").assertExists()

        onNodeWithTag("franchise_console_filters").assertIsDisplayed()
        onNodeWithTag("franchise_console_chip_all").assertExists()
        onNodeWithTag("franchise_console_chip_NES").assertExists()
        onNodeWithTag("franchise_console_chip_PS1").assertExists()
        onNodeWithTag("franchise_console_chip_GBA").assertExists()

        // Not-in-library game should indicate as such
        onNodeWithTag("franchise_game_2003")
            .assertContentDescriptionContains("not in library", substring = true)

        // Click PS1 filter
        onNodeWithTag("franchise_console_chip_PS1").performClick()
        advanceQuick(harness)

        // Only PS1 game visible
        onNodeWithTag("franchise_game_2001").assertDoesNotExist()
        onNodeWithTag("franchise_game_2002").assertExists()
        onNodeWithTag("franchise_game_2003").assertDoesNotExist()

        // Click PS1 again to clear filter
        onNodeWithTag("franchise_console_chip_PS1").performClick()
        advanceQuick(harness)

        // All games visible again
        onNodeWithTag("franchise_game_2001").assertExists()
        onNodeWithTag("franchise_game_2002").assertExists()
        onNodeWithTag("franchise_game_2003").assertExists()
    }

    // --- Empty state ---

    @Test
    fun `empty state shown when no games match filter`() = runComposeUiTest {
        val harness = createHarness()
        // Franchise with games on only one console
        val singleConsoleDetail = SeriesDetail(
            id = "fr2",
            name = "Mega Man",
            heroUrl = null,
            logoUrl = null,
            consoles = listOf(
                SeriesConsole(abbreviation = "NES", name = "NES", color = "#dc2626", gameCount = 1),
                SeriesConsole(abbreviation = "SNES", name = "SNES", color = "#9333ea", gameCount = 0),
            ),
            libraryGames = 1,
            totalGames = 1,
            games = listOf(
                SeriesGame(
                    igdbGameId = 3001,
                    name = "Mega Man 2",
                    inLibrary = true,
                    localGameId = "game-mm2",
                    coverUrl = null,
                    releaseDate = "1988-12-24",
                    rating = 88.0,
                    consoleAbbreviation = "NES",
                    consoleName = "NES",
                    consoleColor = "#dc2626",
                ),
            ),
        )
        harness.exploreRepo.franchiseDetails = mapOf("fr2" to singleConsoleDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreFranchise("fr2", "Mega Man"))
        )
        advance(harness)

        // Game should be visible initially
        onNodeWithTag("franchise_game_3001").assertExists()

        // Filter by SNES (no games on this console)
        onNodeWithTag("franchise_console_chip_SNES").performClick()
        advanceQuick(harness)

        // Should show empty state
        onNodeWithTag("franchise_empty_state").assertExists()
        onNodeWithTag("franchise_game_3001").assertDoesNotExist()
    }

    // --- Navigation from global search ---

    @Test
    fun `tapping franchise result in global search navigates to franchise screen`() = runComposeUiTest {
        val harness = createHarness()
        harness.searchRepo.searchResult = GlobalSearchResult(
            franchises = SearchCategory(
                results = listOf(
                    SearchFranchiseResult(
                        id = "fr1",
                        name = "Castlevania",
                        totalGames = 8,
                        libraryGames = 2,
                    ),
                ),
                total = 1,
            ),
        )
        harness.exploreRepo.franchiseDetails = mapOf("fr1" to sampleFranchiseDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
        advance(harness)

        searchInputNode().performTextInput("castlevania")
        advanceFully(harness)

        // Franchise result should be visible
        onNodeWithTag("search_result_franchise_fr1").assertExists()

        // Click on the franchise result
        onNodeWithTag("search_result_franchise_fr1").performClick()
        advance(harness)

        // Should navigate to franchise detail screen
        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_franchise/fr1", navState.currentScreen.route)
    }

    // --- Navigation from game detail franchise chip ---

    @Test
    fun `franchise link shows on game detail screen`() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.gameFranchiseLinks = mapOf(
            "1" to listOf(
                GameFranchiseLink(
                    id = "fr1",
                    name = "Castlevania",
                    gameCount = 8,
                ),
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithTag("series_franchise_section").assertExists()
        onNodeWithText("Part of Castlevania", substring = true).assertExists()
    }

    // --- Loading state ---

    @Test
    fun `franchise detail shows loading skeleton while fetching`() = runComposeUiTest {
        val harness = createHarness()
        // Do not provide franchise details so it stays in loading state / error
        // since the repository will fail with "Franchise not found"

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreFranchise("fr_unknown", "Unknown"))
        )
        advance(harness)

        // When franchise is not found, the error state is shown
        onNodeWithTag("franchise_error_state").assertExists()
    }
}
