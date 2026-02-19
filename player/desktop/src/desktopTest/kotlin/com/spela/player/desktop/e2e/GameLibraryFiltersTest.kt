package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.state.ViewMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for Phase 5: Library Enhancements.
 * Tests: Console filter, sort options, sort order toggle, view mode toggle, list view.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GameLibraryFiltersTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun allGamesScreenShowsConsoleFilterChips() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.AllGames)
        )
        advance(harness)

        // Should show "All Consoles" filter chip and console names
        onNodeWithContentDescription("All Consoles filter, selected").assertIsDisplayed()
        onNodeWithContentDescription("Nintendo Entertainment System filter").assertIsDisplayed()
        onNodeWithContentDescription("Super Nintendo filter").assertIsDisplayed()
    }

    @Test
    fun selectingConsoleFilterFiltersGames() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.AllGames)
        )
        advance(harness)

        // Initially all 5 games should be visible (or at least loaded)
        val initialState = harness.gameListViewModel.state.value
        assertEquals(null, initialState.selectedConsoleFilter)

        // Tap "Super Nintendo" filter chip
        onNodeWithContentDescription("Super Nintendo filter").performClick()
        advance(harness)

        // ViewModel state should reflect the console filter
        val filteredState = harness.gameListViewModel.state.value
        assertEquals("2", filteredState.selectedConsoleFilter)

        // SNES games should be visible
        onNodeWithText("Chrono Trigger").assertExists()
        onNodeWithText("Super Metroid").assertExists()
    }

    @Test
    fun sortDropdownShowsCurrentOption() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.AllGames)
        )
        advance(harness)

        // Default sort should be "Title"
        onNodeWithContentDescription("Sort by Title").assertIsDisplayed()
    }

    @Test
    fun changingSortByUpdatesSortOption() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.AllGames)
        )
        advance(harness)

        // Click the sort dropdown
        onNodeWithContentDescription("Sort by Title").performClick()
        advance(harness)

        // Select "File Size" from dropdown
        onNodeWithText("File Size").performClick()
        advance(harness)

        // ViewModel state should reflect new sort
        val state = harness.gameListViewModel.state.value
        assertEquals("file_size", state.sortBy)
    }

    @Test
    fun sortOrderToggleWorksBetweenAscAndDesc() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.AllGames)
        )
        advance(harness)

        // Default is ascending
        onNodeWithContentDescription("Sort ascending").assertIsDisplayed()

        // Toggle to descending
        onNodeWithContentDescription("Sort ascending").performClick()
        advance(harness)

        // Should now be descending
        val state = harness.gameListViewModel.state.value
        assertEquals("desc", state.sortOrder)
        onNodeWithContentDescription("Sort descending").assertIsDisplayed()
    }

    @Test
    fun viewModeToggleSwitchesToListView() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.AllGames)
        )
        advance(harness)

        // Default is grid view
        assertEquals(ViewMode.GRID, harness.gameListViewModel.state.value.viewMode)
        onNodeWithContentDescription("Grid view, selected").assertIsDisplayed()
        onNodeWithContentDescription("List view").assertIsDisplayed()

        // Toggle to list view
        onNodeWithContentDescription("List view").performClick()
        advance(harness)

        // Should now be in list view
        assertEquals(ViewMode.LIST, harness.gameListViewModel.state.value.viewMode)
        onNodeWithContentDescription("List view, selected").assertIsDisplayed()
    }

    @Test
    fun listViewShowsGameRowsWithDetails() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.AllGames)
        )
        advance(harness)

        // Switch to list view
        harness.gameListViewModel.onIntent(GameListIntent.ToggleViewMode)
        advance(harness)

        // Game rows should show title and console name via content description
        onNodeWithContentDescription("Castlevania, NES").assertIsDisplayed()
        // File size should be shown (Castlevania is 131072 bytes = 128 KB)
        onNodeWithText("128 KB").assertExists()
    }

    @Test
    fun filterAndSortCombinationWorksTogether() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.AllGames)
        )
        advance(harness)

        // Select NES console filter
        onNodeWithContentDescription("Nintendo Entertainment System filter").performClick()
        advance(harness)

        // Change sort to File Size
        onNodeWithContentDescription("Sort by Title").performClick()
        advance(harness)
        onNodeWithText("File Size").performClick()
        advance(harness)

        // Verify both filters are applied in ViewModel state
        val state = harness.gameListViewModel.state.value
        assertEquals("1", state.selectedConsoleFilter)
        assertEquals("file_size", state.sortBy)
    }

    @Test
    fun reselectingAllConsolesRemovesFilter() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.AllGames)
        )
        advance(harness)

        // Select NES filter first
        onNodeWithContentDescription("Nintendo Entertainment System filter").performClick()
        advance(harness)
        assertEquals("1", harness.gameListViewModel.state.value.selectedConsoleFilter)

        // Go back to All Consoles
        onNodeWithContentDescription("All Consoles filter").performClick()
        advance(harness)

        assertEquals(null, harness.gameListViewModel.state.value.selectedConsoleFilter)
    }
}
