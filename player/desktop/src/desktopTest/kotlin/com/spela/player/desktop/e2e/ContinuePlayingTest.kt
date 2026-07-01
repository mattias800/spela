package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the Continue Playing "See all" destination (#1525): the Home
 * carousel is capped, so a See-all opens the full recently-played screen.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ContinuePlayingTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun homeShowsContinuePlayingSeeAllLink() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Default fake recentGames is non-empty, so the section (and its See-all) shows.
        setContent { harness.App() }
        advance(harness)

        onNodeWithContentDescription("See all Continue Playing").assertExists()
    }

    @Test
    fun continuePlayingScreenShowsEmptyStateWhenNoRecentGames() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.recentGamesOverride = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ContinuePlaying))
        advance(harness)

        assertEquals(SpScreen.ContinuePlaying, harness.navigationViewModel.state.value.currentScreen)
        onNodeWithText("No recently played games").assertIsDisplayed()
    }

    @Test
    fun continuePlayingScreenRendersGridWhenGamesPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Default fake recentGames = games.take(2), non-empty.

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ContinuePlaying))
        advance(harness)

        assertEquals(SpScreen.ContinuePlaying, harness.navigationViewModel.state.value.currentScreen)
        // Grid, not the empty state.
        onNodeWithText("No recently played games").assertDoesNotExist()
    }
}
