package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.AnniversaryItem
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Desktop E2E tests for Phase 11: Temporal Discovery.
 *
 * Covers:
 * - On This Day section renders with games
 * - Anniversaries section renders
 * - Empty temporal data hides sections
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreTemporalTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun makeGame(id: String, title: String, releaseDate: String? = null): Game = Game(
        id = id,
        title = title,
        consoleId = "snes",
        consoleName = "Super Nintendo",
        fileName = "$title.sfc",
        fileSize = 1024,
        discCount = 1,
        description = "",
        coverUrl = null,
        developer = "",
        publisher = "",
        releaseDate = releaseDate,
        genre = "Action",
        players = 1,
        rating = 80.0,
        isFavorite = false,
        isInPlayLater = false,
        coverAspectRatio = 0.75f,
    )

    @Test
    fun onThisDaySectionRendersWithGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.onThisDayDate = "March 10"
        harness.exploreRepo.onThisDayGames = listOf(
            makeGame("otd1", "Classic Alpha", releaseDate = "1994-03-10"),
            makeGame("otd2", "Classic Beta", releaseDate = "2001-03-10"),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_on_this_day_section").assertExists()
        onNodeWithTag("on_this_day_row").assertExists()
        onNodeWithTag("on_this_day_year_otd1").assertExists()
    }

    @Test
    fun anniversariesSectionRenders() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.anniversariesList = listOf(
            AnniversaryItem(
                game = makeGame("ann1", "Nostalgic RPG"),
                yearsAgo = 3,
                playedAt = "2023-03-10T14:00:00Z",
            ),
            AnniversaryItem(
                game = makeGame("ann2", "Old Favorite"),
                yearsAgo = 1,
                playedAt = "2025-03-10T10:00:00Z",
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_anniversaries_section").assertExists()
        onNodeWithTag("anniversaries_row").assertExists()
        onNodeWithTag("anniversary_years_ann1").assertExists()
    }

    @Test
    fun emptyTemporalDataHidesSections() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_on_this_day_section").assertDoesNotExist()
        onNodeWithTag("explore_anniversaries_section").assertDoesNotExist()
    }
}
