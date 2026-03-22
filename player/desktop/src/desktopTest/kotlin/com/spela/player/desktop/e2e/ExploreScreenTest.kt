package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for the Explore screen.
 *
 * Covers:
 * - Screen renders with featured games and shelf rows
 * - Hero carousel displays featured games
 * - Shelf rows display games
 * - Empty state when no data
 * - Navigation to game detail from carousel
 * - Navigation to game detail from shelf
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreScreenTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleFeatured = listOf(
        FeaturedGame(
            gameId = "1",
            title = "Super Mario World",
            heroUrl = null,
            logoUrl = null,
            consoleName = "Super Nintendo",
            consoleAbbreviation = "SNES",
            consoleColor = "#7B2D8E",
            rating = 92.5,
            genre = "Platformer",
            isFavorite = false,
            isPlayLater = false,
        ),
        FeaturedGame(
            gameId = "2",
            title = "Chrono Trigger",
            heroUrl = null,
            logoUrl = null,
            consoleName = "Super Nintendo",
            consoleAbbreviation = "SNES",
            consoleColor = "#7B2D8E",
            rating = 95.0,
            genre = "RPG",
            isFavorite = true,
            isPlayLater = false,
        ),
    )

    private val sampleGames = listOf(
        Game(
            id = "10",
            title = "Mega Man X",
            consoleName = "SNES",
            consoleId = "snes",
            coverUrl = null,
            rating = 88.0,
        ),
        Game(
            id = "11",
            title = "Final Fantasy VI",
            consoleName = "SNES",
            consoleId = "snes",
            coverUrl = null,
            rating = 94.0,
        ),
        Game(
            id = "12",
            title = "Donkey Kong Country",
            consoleName = "SNES",
            consoleId = "snes",
            coverUrl = null,
            rating = 85.0,
        ),
    )

    private val sampleRows = listOf(
        ExploreRow(
            id = "recently-added",
            title = "Recently Added",
            games = sampleGames,
        ),
        ExploreRow(
            id = "top-rated",
            title = "Top Rated",
            games = listOf(
                Game(
                    id = "20",
                    title = "A Link to the Past",
                    consoleName = "SNES",
                    consoleId = "snes",
                    coverUrl = null,
                    rating = 96.0,
                ),
            ),
        ),
    )

    @Test
    fun exploreScreenRenders() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredGames = sampleFeatured
        harness.exploreRepo.exploreRows = sampleRows

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
    }

    @Test
    fun heroCarouselDisplaysFeaturedGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredGames = sampleFeatured
        harness.exploreRepo.exploreRows = sampleRows

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("hero_carousel").assertIsDisplayed()
        // The first featured game should be visible
        onNodeWithText("Super Mario World").assertExists()
    }

    @Test
    fun heroCarouselShowsConsoleChipAndGenre() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredGames = sampleFeatured
        harness.exploreRepo.exploreRows = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        // Console name chip and genre chip should be visible for the active slide
        onNodeWithText("Super Nintendo").assertExists()
        onNodeWithText("Platformer").assertExists()
    }

    @Test
    fun heroCarouselShowsViewGameButton() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredGames = sampleFeatured
        harness.exploreRepo.exploreRows = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithText("View Game").assertExists()
    }

    @Test
    fun shelfRowsDisplayGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredGames = emptyList()
        harness.exploreRepo.exploreRows = sampleRows

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        // Row titles
        onNodeWithText("Recently Added").assertIsDisplayed()
        onNodeWithText("Top Rated").assertIsDisplayed()

        // Game titles from the rows
        onNodeWithText("Mega Man X").assertExists()
        onNodeWithText("Final Fantasy VI").assertExists()
        onNodeWithText("A Link to the Past").assertExists()
    }

    @Test
    fun emptyStateWhenNoGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredGames = emptyList()
        harness.exploreRepo.exploreRows = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_empty_state").assertIsDisplayed()
        onNodeWithText("Nothing to explore yet").assertIsDisplayed()
    }

    @Test
    fun navigationToGameDetailFromShelf() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredGames = emptyList()
        harness.exploreRepo.exploreRows = sampleRows

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        // Click a game card in the shelf
        onNodeWithTag("explore_game_card_10").performClick()
        advance(harness)

        // Should navigate to game detail
        val navState = harness.navigationViewModel.state.value
        assertEquals(SpScreen.GameDetail("10"), navState.currentScreen)
    }

    @Test
    fun navigationToGameDetailFromCarousel() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredGames = sampleFeatured
        harness.exploreRepo.exploreRows = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        // Click the "View Game" button on the hero carousel
        onNodeWithText("View Game").performClick()
        advance(harness)

        // Should navigate to game detail for the first featured game
        val navState = harness.navigationViewModel.state.value
        assertEquals(SpScreen.GameDetail("1"), navState.currentScreen)
    }

    @Test
    fun shelfRowsShowGameConsoleNames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.featuredGames = emptyList()
        harness.exploreRepo.exploreRows = sampleRows

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        // Console name should appear as subtitle under game cards
        onAllNodesWithText("SNES").assertCountEquals(4) // 3 games in first row + 1 in second row
    }
}
