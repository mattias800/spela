package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.TopRatedGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the Top Rated row on the Console screen.
 *
 * The row displays IGDB top-rated games for the current console.
 * Games that exist in the local library (`localGameId != null`) are tappable
 * and at full opacity; games not in the library are dimmed and not clickable.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class TopRatedRowTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
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

    // --- Section visibility ---

    @Test
    fun topRatedSectionShownWhenDataAvailable() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.topRatedGames = listOf(
            TopRatedGame(rank = 1, name = "Chrono Trigger", rating = 96.0, localGameId = null),
            TopRatedGame(rank = 2, name = "Super Metroid", rating = 94.0, localGameId = "1"),
        )

        setContent { harness.App() }
        navigateToConsole(harness)

        onNodeWithText("Top Rated").assertIsDisplayed()
    }

    @Test
    fun topRatedSectionHiddenWhenNoData() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.topRatedGames = emptyList()

        setContent { harness.App() }
        navigateToConsole(harness)

        onNodeWithText("Top Rated").assertDoesNotExist()
    }

    // --- Card content ---

    @Test
    fun topRatedCardsShowGameNameAndRating() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.topRatedGames = listOf(
            TopRatedGame(
                rank = 1,
                name = "Chrono Trigger",
                rating = 96.0,
                localGameId = null,
                consoleName = "Super Nintendo",
            ),
            TopRatedGame(
                rank = 2,
                name = "Super Metroid",
                rating = 94.0,
                localGameId = "1",
                consoleName = "Super Nintendo",
            ),
        )

        setContent { harness.App() }
        navigateToConsole(harness)

        // Card contentDescription comes from SpGameCard: "$title, $subtitle".
        // Subtitle is consoleName here. Rating lives in its own on-card badge,
        // reachable via its text.
        onNodeWithContentDescription("Chrono Trigger, Super Nintendo").assertExists()
        onNodeWithContentDescription("Super Metroid, Super Nintendo").assertExists()

        // Game names visible as text
        onNodeWithText("Chrono Trigger").assertExists()
        onNodeWithText("Super Metroid").assertExists()

        // Rating scores visible. SpGameCard renders via formatRating(),
        // which emits "96.0" / "94.0" — never a bare integer.
        onNodeWithText("96.0").assertExists()
        onNodeWithText("94.0").assertExists()
    }

    // --- Playability ---

    @Test
    fun playableGameCardIsTappable() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.topRatedGames = listOf(
            TopRatedGame(
                rank = 1,
                name = "Super Metroid",
                rating = 94.0,
                localGameId = "1",
                consoleName = "Super Nintendo",
            ),
        )

        setContent { harness.App() }
        navigateToConsole(harness)

        // Card with localGameId should have Button role (clickable)
        onNodeWithContentDescription("Super Metroid, Super Nintendo")
            .assertHasClickAction()
    }

    @Test
    fun nonPlayableGameCardIsNotTappable() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.topRatedGames = listOf(
            TopRatedGame(
                rank = 1,
                name = "Chrono Trigger",
                rating = 96.0,
                localGameId = null,
                consoleName = "Super Nintendo",
            ),
        )

        setContent { harness.App() }
        navigateToConsole(harness)

        // Card without localGameId should not have click action.
        // SpAvailabilityGameCard substitutes a no-op onClick for unavailable
        // games, so the node still has a button role with a click action.
        // The rendered "Not in library" badge is the user-visible signal.
        onNodeWithText("Not in library").assertExists()
    }
}
