package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.MoodDefinition
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for Phase 5: Mood Picker on the Explore page.
 *
 * Covers:
 * - Mood picker renders on Explore screen
 * - Mood cards show correct info
 * - Mood picker hidden when no moods
 * - Navigate to mood detail screen
 * - Mood detail shows games
 * - Mood detail shows empty state
 * - Surprise me button exists
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreMoodTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleMoods = listOf(
        MoodDefinition(
            id = "chill",
            name = "Chill",
            description = "Relax with something easy-going",
            icon = "\uD83C\uDFB5",
            gradient = listOf("#1B5E20", "#4CAF50"),
        ),
        MoodDefinition(
            id = "challenge",
            name = "Challenge Me",
            description = "Test your skills with something tough",
            icon = "\uD83D\uDD25",
            gradient = listOf("#B71C1C", "#F44336"),
        ),
        MoodDefinition(
            id = "nostalgia",
            name = "Nostalgia Trip",
            description = "Revisit your most-played classics",
            icon = "\u2728",
            gradient = listOf("#4A148C", "#9C27B0"),
        ),
    )

    private val sampleMoodGames = listOf(
        Game(
            id = "game-10",
            title = "Relaxing Puzzle Game",
            consoleId = "snes",
            consoleName = "SNES",
            genre = "Puzzle",
        ),
        Game(
            id = "game-11",
            title = "Chill Platformer",
            consoleId = "gba",
            consoleName = "GBA",
            genre = "Platform",
        ),
    )

    // --- Mood picker on Explore screen ---

    @Test
    fun moodPickerRendersOnExploreScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.moodsList = sampleMoods

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_moods_section").assertIsDisplayed()
        onNodeWithText("What are you in the mood for?").assertIsDisplayed()
        onNodeWithTag("mood_picker").assertIsDisplayed()
    }

    @Test
    fun moodCardsShowCorrectInfo() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.moodsList = sampleMoods

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithText("Chill").assertExists()
        onNodeWithText("Challenge Me").assertExists()
        onNodeWithText("Nostalgia Trip").assertExists()
        onNodeWithText("Relax with something easy-going").assertExists()
    }

    @Test
    fun moodPickerHiddenWhenNoMoods() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.moodsList = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_moods_section").assertDoesNotExist()
    }

    // --- Navigation to mood detail ---

    @Test
    fun navigationToMoodDetailWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.moodsList = sampleMoods
        harness.exploreRepo.moodGames = mapOf("chill" to sampleMoodGames)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("mood_card_chill").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_mood/chill", navState.currentScreen.route)
    }

    // --- Mood detail screen ---

    @Test
    fun moodDetailShowsGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.moodsList = sampleMoods
        harness.exploreRepo.moodGames = mapOf("chill" to sampleMoodGames)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreMood("chill", "Chill"))
        )
        advance(harness)

        onNodeWithTag("explore_mood_screen").assertIsDisplayed()
        onNodeWithText("Relaxing Puzzle Game").assertExists()
        onNodeWithText("Chill Platformer").assertExists()
        onNodeWithText("2 games").assertExists()
    }

    @Test
    fun moodDetailShowsEmptyState() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.moodsList = sampleMoods
        harness.exploreRepo.moodGames = mapOf("chill" to emptyList())

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreMood("chill", "Chill"))
        )
        advance(harness)

        onNodeWithTag("explore_mood_screen").assertIsDisplayed()
        onNodeWithTag("mood_empty_state").assertIsDisplayed()
        onNodeWithText("No games found").assertExists()
    }

    @Test
    fun luckyButtonExistsInWildFeatures() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.moodsList = sampleMoods

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        // The "I'm Feeling Lucky" button should exist in the Wild Features section.
        onNodeWithTag("wild_lucky_button").assertExists()
    }
}
