package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.Keyword
import com.spela.player.domain.model.Theme
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for Phase 3: Theme & Keyword Browsing.
 *
 * Covers:
 * - Theme grid renders on Explore screen
 * - Keyword chips render on Explore screen
 * - Navigation to theme detail works
 * - Theme detail shows games
 * - Navigation to keyword detail works
 * - Keyword detail shows games
 * - Sections hidden when no data
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreThemeKeywordTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleThemes = listOf(
        Theme(id = "17", name = "Sci-Fi", gameCount = 25),
        Theme(id = "18", name = "Horror", gameCount = 12),
        Theme(id = "21", name = "Fantasy", gameCount = 30),
    )

    private val sampleKeywords = listOf(
        Keyword(id = "100", name = "Time Travel", gameCount = 8),
        Keyword(id = "101", name = "Zombies", gameCount = 15),
        Keyword(id = "102", name = "Steampunk", gameCount = 5),
    )

    private val sampleThemeGames = listOf(
        Game(
            id = "50",
            title = "Metroid Prime",
            consoleName = "GC",
            consoleId = "gc",
            coverUrl = null,
            rating = 96.0,
        ),
        Game(
            id = "51",
            title = "Mega Man X",
            consoleName = "SNES",
            consoleId = "snes",
            coverUrl = null,
            rating = 88.0,
        ),
    )

    private val sampleKeywordGames = listOf(
        Game(
            id = "60",
            title = "Chrono Trigger",
            consoleName = "SNES",
            consoleId = "snes",
            coverUrl = null,
            rating = 95.0,
        ),
    )

    @Test
    fun themeGridRendersOnExploreScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.themes = sampleThemes

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_themes_section").assertIsDisplayed()
        onNodeWithText("Browse by Theme").assertIsDisplayed()
        onNodeWithTag("theme_grid").assertIsDisplayed()
    }

    @Test
    fun themeCardsShowThemeNames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.themes = sampleThemes

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithText("Sci-Fi").assertExists()
        onNodeWithText("Horror").assertExists()
        onNodeWithText("Fantasy").assertExists()
    }

    @Test
    fun themeCardsShowGameCounts() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.themes = sampleThemes

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithText("25 games").assertExists()
        onNodeWithText("12 games").assertExists()
        onNodeWithText("30 games").assertExists()
    }

    @Test
    fun keywordChipsRenderOnExploreScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.keywords = sampleKeywords

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_keywords_section").assertIsDisplayed()
        onNodeWithText("Popular Keywords").assertIsDisplayed()
        onNodeWithTag("keyword_chips").assertIsDisplayed()
    }

    @Test
    fun keywordChipsShowNamesAndCounts() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.keywords = sampleKeywords

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithText("Time Travel (8)").assertExists()
        onNodeWithText("Zombies (15)").assertExists()
        onNodeWithText("Steampunk (5)").assertExists()
    }

    @Test
    fun navigationToThemeDetailWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.themes = sampleThemes
        harness.exploreRepo.themeGames = mapOf("17" to sampleThemeGames)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        // Click the Sci-Fi theme card
        onNodeWithTag("theme_card_17").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_theme/17", navState.currentScreen.route)
    }

    @Test
    fun themeDetailShowsGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.themeGames = mapOf("17" to sampleThemeGames)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreTheme("17", "Sci-Fi"))
        )
        advance(harness)

        onNodeWithTag("explore_theme_screen").assertIsDisplayed()
        onNodeWithText("Sci-Fi").assertIsDisplayed()
        onNodeWithText("Metroid Prime").assertExists()
        onNodeWithText("Mega Man X").assertExists()
    }

    @Test
    fun themeDetailNavigatesToGameDetail() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.themeGames = mapOf("17" to sampleThemeGames)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreTheme("17", "Sci-Fi"))
        )
        advance(harness)

        onNodeWithTag("theme_game_card_50").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals(SpScreen.GameDetail("50"), navState.currentScreen)
    }

    @Test
    fun navigationToKeywordDetailWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.keywords = sampleKeywords
        harness.exploreRepo.keywordGames = mapOf("100" to sampleKeywordGames)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        // Click the "Time Travel" keyword chip
        onNodeWithTag("keyword_chip_100").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_keyword/100", navState.currentScreen.route)
    }

    @Test
    fun keywordDetailShowsGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.keywordGames = mapOf("100" to sampleKeywordGames)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreKeyword("100", "Time Travel"))
        )
        advance(harness)

        onNodeWithTag("explore_keyword_screen").assertIsDisplayed()
        onNodeWithText("Time Travel").assertIsDisplayed()
        onNodeWithText("Chrono Trigger").assertExists()
    }

    @Test
    fun themeSectionHiddenWhenNoThemes() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.themes = emptyList()
        harness.exploreRepo.keywords = sampleKeywords

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_themes_section").assertDoesNotExist()
    }

    @Test
    fun keywordSectionHiddenWhenNoKeywords() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.themes = sampleThemes
        harness.exploreRepo.keywords = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_keywords_section").assertDoesNotExist()
    }
}
