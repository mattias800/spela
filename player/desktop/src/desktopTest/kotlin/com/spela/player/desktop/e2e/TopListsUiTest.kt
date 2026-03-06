package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.TopListGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for the Top Lists screen.
 *
 * Covers:
 * - Screen renders with games
 * - Empty state when no games
 * - Game items show rank, name, console, and rating
 * - Tapping a game navigates to game detail
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class TopListsUiTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleGames = listOf(
        TopListGame(rank = 1, gameId = "42", name = "Super Mario World", coverUrl = null, consoleName = "SNES", consoleId = "snes", rating = 92.5),
        TopListGame(rank = 2, gameId = "99", name = "The Legend of Zelda", coverUrl = null, consoleName = "NES", consoleId = "nes", rating = 91.0),
        TopListGame(rank = 3, gameId = "55", name = "Chrono Trigger", coverUrl = null, consoleName = "SNES", consoleId = "snes", rating = 90.3),
    )

    @Test
    fun topListsScreenRendersGames() = runComposeUiTest {
        val harness = createHarness()
        harness.gameRepo.topListGames = sampleGames

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.TopLists))
        advance(harness)

        onNodeWithTag("top_lists_screen").assertIsDisplayed()
        onNodeWithText("Top Lists").assertIsDisplayed()
        onNodeWithText("Super Mario World").assertIsDisplayed()
        onNodeWithText("The Legend of Zelda").assertIsDisplayed()
        onNodeWithText("Chrono Trigger").assertIsDisplayed()
    }

    @Test
    fun topListsScreenShowsEmptyState() = runComposeUiTest {
        val harness = createHarness()
        harness.gameRepo.topListGames = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.TopLists))
        advance(harness)

        onNodeWithTag("top_lists_screen").assertIsDisplayed()
        onNodeWithTag("top_lists_empty").assertIsDisplayed()
        onNodeWithText("No top rated games yet").assertIsDisplayed()
    }

    @Test
    fun topListsShowsConsoleNames() = runComposeUiTest {
        val harness = createHarness()
        harness.gameRepo.topListGames = listOf(
            TopListGame(rank = 1, gameId = "42", name = "Super Mario World", consoleName = "SNES", consoleId = "snes", rating = 92.5),
            TopListGame(rank = 2, gameId = "99", name = "Mega Man 2", consoleName = "Game Boy", consoleId = "gb", rating = 88.0),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.TopLists))
        advance(harness)

        // Each game has a unique console name
        onNodeWithText("SNES").assertExists()
        onNodeWithText("Game Boy").assertExists()
    }

    @Test
    fun topListsShowsRatings() = runComposeUiTest {
        val harness = createHarness()
        harness.gameRepo.topListGames = sampleGames

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.TopLists))
        advance(harness)

        onNodeWithText("92.5").assertExists()
        onNodeWithText("91.0").assertExists()
        onNodeWithText("90.3").assertExists()
    }

    @Test
    fun tappingGameNavigatesToGameDetail() = runComposeUiTest {
        val harness = createHarness()
        harness.gameRepo.topListGames = sampleGames

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.TopLists))
        advance(harness)

        onNodeWithTag("top_list_item_42").performClick()
        advance(harness)

        // Should navigate to game detail for game ID 42
        val navState = harness.navigationViewModel.state.value
        assertEquals(SpScreen.GameDetail("42"), navState.currentScreen)
    }
}
