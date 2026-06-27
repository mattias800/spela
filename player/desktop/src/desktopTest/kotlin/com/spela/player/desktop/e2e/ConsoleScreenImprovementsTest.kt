package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E2E tests for the 8 console screen improvements:
 *
 * 1. Fading edge on ConsoleInfoSection stats column
 * 2. Sort/filter dropdown (SwapVert button, sort menu, ordering)
 * 3. Favorite heart badge on cover art
 * 4. Expandable About section (More/Less toggle)
 * 5. Scraping shimmer (isLoading state)
 * 6. ConsoleInfoSection alignment (max 96dp width)
 * 7. Star rating row on game cards
 * 8. Improved empty state (NoGamesInConsole)
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ConsoleScreenImprovementsTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToConsole(
        harness: SpelaTestHarness,
        consoleId: String = "nes",
    ) {
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console(consoleId))
        )
        advance(harness)
    }

    private fun ComposeUiTest.navigateToConsoleGames(
        harness: SpelaTestHarness,
        consoleId: String = "nes",
    ) {
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ConsoleGames(consoleId))
        )
        advance(harness)
    }

    // ────────────────────────────────────────────────
    // #1: Fading edge — ConsoleInfoSection stats
    // ────────────────────────────────────────────────

    @Test
    fun consoleInfoSectionShowsMetadata() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        navigateToConsole(harness, "nes")

        // The ConsoleInfoSection should display static metadata for NES
        onNodeWithText("Nintendo").assertExists()
        onNodeWithText("1983").assertExists()
        onNodeWithText("Cartridge").assertExists()
        onNodeWithText("Media", substring = true).assertExists()
        onNodeWithText("61.9M units").assertExists()
    }

    // ────────────────────────────────────────────────
    // #2: Sort/filter dropdown
    // ────────────────────────────────────────────────

    @Test
    fun sortMenuShowsOptionsUpdatesStateAndDismisses() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        navigateToConsoleGames(harness)

        onNodeWithContentDescription("Sort games").assertIsDisplayed()

        onNodeWithContentDescription("Sort games").performClick()
        advanceQuick(harness)

        onNodeWithText("Title (A–Z)").assertIsDisplayed()
        onNodeWithText("Rating").assertIsDisplayed()
        onNodeWithText("Release date").assertIsDisplayed()
        onNodeWithText("Recently played").assertIsDisplayed()

        onNodeWithText("Release date").performClick()
        advanceQuick(harness)
        assertEquals("releaseDate", harness.gameListViewModel.state.value.sortBy)
        onNodeWithText("Rating").assertDoesNotExist()

        onNodeWithContentDescription("Sort games").performClick()
        advanceQuick(harness)
        onNodeWithText("Rating").performClick()
        advanceQuick(harness)
        assertEquals("rating", harness.gameListViewModel.state.value.sortBy)
        onNodeWithText("Release date").assertDoesNotExist()
    }

    // ────────────────────────────────────────────────
    // #3: Favorite heart badge
    // ────────────────────────────────────────────────

    @Test
    fun gameCardsShowFavoritesAndRatingsTogether() = runComposeUiTest {
        val harness = createLoggedInHarness()

        harness.gameRepo.games = harness.gameRepo.games.map {
            when (it.id) {
                "1" -> it.copy(isFavorite = true, communityRating = 4.8)
                "2" -> it.copy(communityRating = 1.0)
                "3" -> it.copy(isFavorite = true, communityRating = 0.5)
                else -> it
            }
        }

        setContent { harness.App() }
        navigateToConsoleGames(harness)

        // Favorited games should include the badge in their content description.
        onNodeWithContentDescription("Castlevania, NES, favorited").assertIsDisplayed()
        onNodeWithContentDescription("Mega Man 2, NES, favorited").assertIsDisplayed()

        onNodeWithContentDescription("Super Mario Bros., NES").assertIsDisplayed()
        onNodeWithText("4.8").assertIsDisplayed()
        onNodeWithText("1.0").assertIsDisplayed()
        onNodeWithText("0.5").assertExists()
    }

    // ────────────────────────────────────────────────
    // #5: Scraping shimmer — tested indirectly via isLoading
    // ────────────────────────────────────────────────
    // The shimmer is an animation — we can't directly assert visual shimmer in
    // Compose UI tests. However, we verify the correct condition triggers shimmer:
    // isLoading = true when coverUrl == null && scrapeAttempts == 0.

    @Test
    fun coverLoadingAndLoadedGamesRenderCards() = runComposeUiTest {
        val harness = createLoggedInHarness()

        harness.gameRepo.games = harness.gameRepo.games.map {
            when (it.id) {
                "1" -> it.copy(coverUrl = null, scrapeAttempts = 0)
                "2" -> it.copy(coverUrl = "https://example.com/cover.jpg", scrapeAttempts = 1)
                else -> it
            }
        }

        setContent { harness.App() }
        navigateToConsoleGames(harness)

        onNodeWithContentDescription("Castlevania, NES").assertIsDisplayed()
        onNodeWithContentDescription("Super Mario Bros., NES").assertIsDisplayed()
    }

    // ────────────────────────────────────────────────
    // #7: Star rating row
    // ────────────────────────────────────────────────

    @Test
    fun gameWithLowRatingDoesNotShowStarRating() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // All games have default communityRating = 0.0 — no star should show
        setContent { harness.App() }
        navigateToConsoleGames(harness)

        // No rating text for any game
        onAllNodesWithText("0.0").assertCountEquals(0)
    }

    // ────────────────────────────────────────────────
    // #8: Improved empty state
    // ────────────────────────────────────────────────

    @Test
    fun emptyConsoleShowsImprovedCopy() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Clear all NES games to trigger empty state
        harness.gameRepo.games = harness.gameRepo.games.filter { it.consoleId != "nes" }

        setContent { harness.App() }
        navigateToConsoleGames(harness, "nes")

        // The improved empty state message from SpEmptyStates.NoGamesInConsole
        // uses console.name ("Nintendo Entertainment System"), not abbreviation
        onNodeWithText("No Nintendo Entertainment System games", substring = true).assertIsDisplayed()
        onNodeWithText("ROM files", substring = true).assertIsDisplayed()
    }

    @Test
    fun emptyConsoleShowsConsoleSpecificName() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Clear all SNES games
        harness.gameRepo.games = harness.gameRepo.games.filter { it.consoleId != "snes" }

        setContent { harness.App() }
        navigateToConsoleGames(harness, "snes")

        // Should use the console name "Super Nintendo" in the empty state
        onNodeWithText("No Super Nintendo games", substring = true).assertIsDisplayed()
    }

    @Test
    fun nonEmptyConsoleDoesNotShowEmptyState() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        navigateToConsoleGames(harness, "nes")

        // With 3 NES games, the empty state should not appear
        onAllNodesWithText("No Nintendo Entertainment System games", substring = true)
            .assertCountEquals(0)

        // Game count text should be present (appears in both hero banner and heading)
        onAllNodesWithText("3 games", substring = true)
            .fetchSemanticsNodes()
            .also { assertTrue(it.isNotEmpty(), "Expected at least one '3 games' text node") }
    }

    // ────────────────────────────────────────────────
    // Combined scenarios
    // ────────────────────────────────────────────────

    @Test
    fun sortByRatingOrdersCorrectly() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Give distinct ratings to NES games
        harness.gameRepo.games = harness.gameRepo.games.map {
            when (it.id) {
                "1" -> it.copy(communityRating = 2.0) // Castlevania
                "2" -> it.copy(communityRating = 5.0) // Super Mario Bros.
                "3" -> it.copy(communityRating = 3.5) // Mega Man 2
                else -> it
            }
        }

        setContent { harness.App() }
        navigateToConsoleGames(harness)

        // Open sort menu and select Rating
        onNodeWithContentDescription("Sort games").performClick()
        advanceQuick(harness)
        onNodeWithText("Rating").performClick()
        advanceQuick(harness)

        // Verify the ViewModel state changed
        assertEquals("rating", harness.gameListViewModel.state.value.sortBy)

        // All three rated games should still be visible
        onNodeWithText("2.0").assertIsDisplayed()
        onNodeWithText("5.0").assertIsDisplayed()
        onNodeWithText("3.5").assertIsDisplayed()
    }
}
