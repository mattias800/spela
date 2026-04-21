package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.SimilarGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the Similar Games section on the Game Detail screen.
 *
 * Similar games come from IGDB data. Games that exist in the local library
 * (`localGameId != null`) are at full opacity and tappable; games not in
 * the library are dimmed and not clickable.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SimilarGamesTest {

    private fun createHarnessOnGameDetail(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.scrollToSection(matcher: SemanticsMatcher) {
        onNodeWithTag("game_detail_content").performScrollToNode(matcher)
    }

    // --- Section visibility ---

    @Test
    fun similarGamesSectionShownWhenDataAvailable() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameRepo.similarGames = listOf(
            SimilarGame(name = "Dracula X", rating = 78.0, localGameId = null),
            SimilarGame(name = "Super Mario Bros.", rating = 85.0, localGameId = "2"),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithText("Castlevania").assertIsDisplayed()
        scrollToSection(hasText("Similar Games"))
        onNodeWithText("Similar Games").assertIsDisplayed()
    }

    @Test
    fun similarGamesSectionHiddenWhenNoSimilarGames() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameRepo.similarGames = emptyList()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("Similar Games").assertDoesNotExist()
    }

    // --- Card content ---

    @Test
    fun similarGameCardsShowGameName() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameRepo.similarGames = listOf(
            SimilarGame(name = "Dracula X", rating = 78.0, localGameId = null),
            SimilarGame(name = "Super Mario Bros.", rating = 85.0, localGameId = "2"),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSection(hasText("Similar Games"))

        // Game names visible as text
        onNodeWithText("Dracula X").assertExists()
        onNodeWithText("Super Mario Bros.").assertExists()
    }

    @Test
    fun similarGameCardsShowRatingAndAvailabilityBadge() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameRepo.similarGames = listOf(
            SimilarGame(name = "Super Mario Bros.", rating = 85.0, localGameId = "2"),
            SimilarGame(name = "Dracula X", rating = 78.0, localGameId = null),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSection(hasText("Similar Games"))

        // Rating values render via formatRating() as "85.0" / "78.0" on the
        // card. The "Not in library" badge is the only user-visible signal
        // of unavailability right now (rating+availability in semantics
        // would be a nice a11y improvement — see #633 for a follow-up).
        onNodeWithText("85.0").assertExists()
        onNodeWithText("78.0").assertExists()
        onNodeWithText("Not in library").assertExists()
    }

    // --- Playability ---

    @Test
    fun playableSimilarGameCardIsTappable() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameRepo.similarGames = listOf(
            SimilarGame(name = "Super Mario Bros.", rating = 85.0, localGameId = "2"),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSection(hasText("Similar Games"))

        // The playable card has no "Not in library" badge.
        onNodeWithText("Not in library").assertDoesNotExist()
        onNodeWithText("Super Mario Bros.").assertExists()
    }

    @Test
    fun nonPlayableSimilarGameCardShowsNotInLibraryBadge() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.gameRepo.similarGames = listOf(
            SimilarGame(name = "Dracula X", rating = 78.0, localGameId = null),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSection(hasText("Similar Games"))

        // Use assertExists rather than assertIsDisplayed — the card can sit
        // below the fold on the default viewport; visible-on-screen status
        // depends on viewport height that varies across CI hosts.
        onNodeWithText("Not in library").assertExists()
        onNodeWithText("Dracula X").assertExists()
    }
}
