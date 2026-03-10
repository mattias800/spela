package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ForYouRow
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Desktop E2E tests for Phase 6: Personalized "For You" recommendations on the Explore page.
 *
 * Covers:
 * - For You section renders when rows exist
 * - "Because you played" row shows source game reference
 * - Section hidden when no rows
 * - Multiple row types render correctly
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreForYouTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sourceGame = Game(
        id = "game-source-1",
        title = "Super Mario World",
        consoleId = "snes",
        consoleName = "SNES",
        genre = "Platform",
        coverUrl = "https://example.com/smw-cover.jpg",
    )

    private val sampleForYouRows = listOf(
        ForYouRow(
            type = "because_you_played",
            title = "Because you played Super Mario World",
            sourceGame = sourceGame,
            genre = null,
            games = listOf(
                Game(
                    id = "game-rec-1",
                    title = "Donkey Kong Country",
                    consoleId = "snes",
                    consoleName = "SNES",
                    genre = "Platform",
                ),
                Game(
                    id = "game-rec-2",
                    title = "Kirby Super Star",
                    consoleId = "snes",
                    consoleName = "SNES",
                    genre = "Platform",
                ),
            ),
        ),
        ForYouRow(
            type = "more_genre",
            title = "More Platform for you",
            sourceGame = null,
            genre = null,
            games = listOf(
                Game(
                    id = "game-rec-3",
                    title = "Mega Man X",
                    consoleId = "snes",
                    consoleName = "SNES",
                    genre = "Platform",
                ),
            ),
        ),
        ForYouRow(
            type = "unfinished",
            title = "Your unfinished business",
            sourceGame = null,
            genre = null,
            games = listOf(
                Game(
                    id = "game-rec-4",
                    title = "Chrono Trigger",
                    consoleId = "snes",
                    consoleName = "SNES",
                    genre = "RPG",
                ),
            ),
        ),
        ForYouRow(
            type = "expand_horizons",
            title = "Expand your horizons — try Fighting",
            sourceGame = null,
            genre = "Fighting",
            games = listOf(
                Game(
                    id = "game-rec-5",
                    title = "Street Fighter II",
                    consoleId = "snes",
                    consoleName = "SNES",
                    genre = "Fighting",
                ),
            ),
        ),
    )

    // --- For You section on Explore screen ---

    @Test
    fun forYouSection_rendersWhenRowsExist() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.forYouRows = sampleForYouRows

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_for_you_section").assertExists()
        onNodeWithTag("for_you_section").assertExists()
        onNodeWithText("For You").assertExists()
    }

    @Test
    fun forYouSection_becauseYouPlayedRow_showsSourceGame() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.forYouRows = listOf(sampleForYouRows[0])

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("for_you_row_because_you_played").assertExists()
        onNodeWithText("Because you played Super Mario World").assertExists()
        onNodeWithTag("for_you_source_game_game-source-1").assertExists()
        onNodeWithTag("for_you_game_game-rec-1").assertExists()
        onNodeWithText("Donkey Kong Country").assertExists()
    }

    @Test
    fun forYouSection_hiddenWhenNoRows() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.forYouRows = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("for_you_section").assertDoesNotExist()
    }

    @Test
    fun forYouSection_showsMultipleRowTypes() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.forYouRows = sampleForYouRows

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("for_you_row_because_you_played").assertExists()
        onNodeWithTag("for_you_row_more_genre").assertExists()
        onNodeWithText("More Platform for you").assertExists()
        onNodeWithText("Mega Man X").assertExists()

        // The unfinished and expand_horizons rows may need scrolling
        // but the tags should exist in the tree
        onNodeWithTag("for_you_row_unfinished").assertExists()
        onNodeWithTag("for_you_row_expand_horizons").assertExists()
    }
}
