package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.DeveloperDetail
import com.spela.player.domain.model.DeveloperSpotlight
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for Phase 7: Developer & Publisher Spotlight Pages.
 *
 * Covers:
 * - Developer spotlight section renders on Explore screen
 * - Developer spotlight displays developer name and stats
 * - Navigate to developer detail screen
 * - Developer detail shows games
 * - Developer detail console filter works
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreDeveloperTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleGames = listOf(
        Game(
            id = "game-dev-1",
            title = "Final Fantasy VI",
            consoleId = "snes",
            consoleName = "SNES",
            genre = "RPG",
            rating = 92.0,
        ),
        Game(
            id = "game-dev-2",
            title = "Chrono Trigger",
            consoleId = "snes",
            consoleName = "SNES",
            genre = "RPG",
            rating = 95.0,
        ),
        Game(
            id = "game-dev-3",
            title = "Kingdom Hearts",
            consoleId = "ps2",
            consoleName = "PS2",
            genre = "Action RPG",
            rating = 85.0,
        ),
    )

    private val sampleSpotlight = DeveloperSpotlight(
        name = "Square",
        gameCount = 24,
        avgRating = 88.5,
        consoles = listOf("SNES", "PS1", "PS2"),
        topGames = sampleGames.take(2),
        heroUrl = null,
    )

    private val sampleDeveloperDetail = DeveloperDetail(
        name = "Square",
        gameCount = 3,
        avgRating = 90.7,
        consoles = listOf("SNES", "PS2"),
        games = sampleGames,
    )

    // --- Developer spotlight on Explore screen ---

    @Test
    fun developerSpotlightRendersOnExploreScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerSpotlightData = sampleSpotlight

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_developer_spotlight_section").assertExists()
        onNodeWithText("Developer Spotlight").assertExists()
    }

    @Test
    fun developerSpotlightDisplaysNameAndStats() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerSpotlightData = sampleSpotlight

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("developer_spotlight").assertExists()
        onNodeWithText("Square").assertExists()
        onNodeWithText("24 games").assertExists()
    }

    @Test
    fun developerSpotlightHiddenWhenNoData() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerSpotlightData = null

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_developer_spotlight_section").assertDoesNotExist()
    }

    // --- Navigation to developer detail ---

    @Test
    fun navigationToDeveloperDetailWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerSpotlightData = sampleSpotlight
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("developer_spotlight_card").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_developer/Square", navState.currentScreen.route)
    }

    // --- Developer detail screen ---

    @Test
    fun developerDetailShowsGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        onNodeWithTag("developer_detail_screen").assertIsDisplayed()
        onAllNodesWithText("Square").fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "Expected at least one 'Square' text node" }
        }
        onNodeWithText("Final Fantasy VI").assertExists()
        onNodeWithText("Chrono Trigger").assertExists()
        onNodeWithText("Kingdom Hearts").assertExists()
    }

    @Test
    fun developerDetailConsoleFilterWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        // All 3 games visible initially
        onNodeWithTag("developer_game_game-dev-1").assertExists()
        onNodeWithTag("developer_game_game-dev-2").assertExists()
        onNodeWithTag("developer_game_game-dev-3").assertExists()

        // Click SNES filter
        onNodeWithTag("developer_console_chip_SNES").performClick()
        advanceQuick(harness)

        // Only SNES games visible
        onNodeWithTag("developer_game_game-dev-1").assertExists()
        onNodeWithTag("developer_game_game-dev-2").assertExists()
        onNodeWithTag("developer_game_game-dev-3").assertDoesNotExist()

        // Click SNES again to clear filter
        onNodeWithTag("developer_console_chip_SNES").performClick()
        advanceQuick(harness)

        // All games visible again
        onNodeWithTag("developer_game_game-dev-1").assertExists()
        onNodeWithTag("developer_game_game-dev-2").assertExists()
        onNodeWithTag("developer_game_game-dev-3").assertExists()
    }
}
