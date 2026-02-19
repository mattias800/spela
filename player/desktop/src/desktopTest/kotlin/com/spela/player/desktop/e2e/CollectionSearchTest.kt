package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameCollectionDetail
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for Phase 8: Collection search (in-collection client-side filtering).
 * Tests: Search field visibility threshold, search filtering behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CollectionSearchTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun createGameList(count: Int): List<Game> {
        return (1..count).map { i ->
            Game(
                id = "game-$i",
                title = "Game $i",
                consoleId = "1",
                consoleName = "NES",
                fileName = "game$i.nes",
                fileSize = 40960L * i,
            )
        }
    }

    @Test
    fun searchFieldAppearsWhenMoreThanFiveGames() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val games = createGameList(6)
        harness.collectionRepo.collectionDetail = GameCollectionDetail(
            id = "1",
            userId = "1",
            username = "player",
            name = "Big Collection",
            description = "Lots of games",
            isPublic = false,
            gameCount = games.size,
            games = games,
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.CollectionDetail("1"))
        )
        advance(harness)

        // Collection name visible
        onNodeWithText("Big Collection").assertIsDisplayed()

        // Search field should appear (placeholder text)
        onNodeWithText("Search in collection...").assertExists()
    }

    @Test
    fun searchFieldHiddenWhenFiveOrFewerGames() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val games = createGameList(5)
        harness.collectionRepo.collectionDetail = GameCollectionDetail(
            id = "1",
            userId = "1",
            username = "player",
            name = "Small Collection",
            description = null,
            isPublic = false,
            gameCount = games.size,
            games = games,
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.CollectionDetail("1"))
        )
        advance(harness)

        // Search field should NOT appear
        onNodeWithText("Search in collection...").assertDoesNotExist()
    }

    @Test
    fun searchFiltersGamesByTitle() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val games = listOf(
            Game(id = "1", title = "Castlevania", consoleId = "1", consoleName = "NES", fileName = "cv.nes", fileSize = 131072),
            Game(id = "2", title = "Super Mario Bros.", consoleId = "1", consoleName = "NES", fileName = "smb.nes", fileSize = 40960),
            Game(id = "3", title = "Mega Man 2", consoleId = "1", consoleName = "NES", fileName = "mm2.nes", fileSize = 262144),
            Game(id = "4", title = "Castlevania III", consoleId = "1", consoleName = "NES", fileName = "cv3.nes", fileSize = 262144),
            Game(id = "5", title = "Contra", consoleId = "1", consoleName = "NES", fileName = "contra.nes", fileSize = 131072),
            Game(id = "6", title = "Zelda", consoleId = "1", consoleName = "NES", fileName = "zelda.nes", fileSize = 262144),
        )
        harness.collectionRepo.collectionDetail = GameCollectionDetail(
            id = "1",
            userId = "1",
            username = "player",
            name = "My NES Games",
            description = null,
            isPublic = false,
            gameCount = games.size,
            games = games,
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.CollectionDetail("1"))
        )
        advance(harness)

        // All games visible initially
        onNodeWithText("Castlevania").assertExists()
        onNodeWithText("Super Mario Bros.").assertExists()
        onNodeWithText("Zelda").assertExists()

        // Type in the search field
        onNode(hasSetTextAction() and hasText("Search in collection...")).performTextInput("Castle")
        advanceQuick(harness)

        // Only Castlevania and Castlevania III should remain
        onNodeWithText("Castlevania").assertExists()
        onNodeWithText("Castlevania III").assertExists()
        // Others should be filtered out
        onNodeWithText("Super Mario Bros.").assertDoesNotExist()
        onNodeWithText("Zelda").assertDoesNotExist()
    }

    @Test
    fun searchWithNoMatchesShowsEmptyState() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val games = createGameList(6)
        harness.collectionRepo.collectionDetail = GameCollectionDetail(
            id = "1",
            userId = "1",
            username = "player",
            name = "Big Collection",
            description = null,
            isPublic = false,
            gameCount = games.size,
            games = games,
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.CollectionDetail("1"))
        )
        advance(harness)

        // Search for something that doesn't exist
        onNode(hasSetTextAction() and hasText("Search in collection...")).performTextInput("ZZZZZ")
        advanceQuick(harness)

        // Should show "No results" empty state
        onNodeWithText("No results", substring = true).assertExists()
    }
}
