package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Cheat
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E2E tests for cheat support on GameDetail screen:
 * - Section visibility based on cheat availability
 * - Toggle cheat enabled state
 * - Disable-all functionality
 * - Active count label
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CheatsUiTest {

    private val testCheats = listOf(
        Cheat(id = "c1", gameId = "1", index = 0, description = "Infinite Lives", code = "AAAA-BBBB", enabled = false),
        Cheat(id = "c2", gameId = "1", index = 1, description = "Max Gold", code = "CCCC-DDDD", enabled = false),
        Cheat(id = "c3", gameId = "1", index = 2, description = "No Clip", code = "EEEE-FFFF", enabled = true),
    )

    private fun createHarness(cheats: List<Cheat> = testCheats): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.cheatRepo.cheats.addAll(cheats)
        return harness
    }

    private fun ComposeUiTest.navigateToGameDetail(harness: SpelaTestHarness) =
        navigateToGameDetail(harness, "1")

    private fun ComposeUiTest.scrollToSection(matcher: SemanticsMatcher) {
        onNodeWithTag("game_detail_content").performScrollToNode(matcher)
    }

    // --- Section Visibility ---

    @Test
    fun cheatsSectionHiddenWhenNoCheats() = runComposeUiTest {
        val harness = createHarness(cheats = emptyList())

        setContent { harness.App() }
        navigateToGameDetail(harness)

        onNodeWithTag("cheats_section").assertDoesNotExist()
    }

    @Test
    fun cheatsSectionShowsWhenCheatsExist() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("cheats_section"))
        onNodeWithText("Cheats").assertIsDisplayed()
    }

    @Test
    fun cheatsSectionShowsCheatDescriptions() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("cheats_section"))
        onNodeWithText("Infinite Lives").assertIsDisplayed()
        onNodeWithText("Max Gold").assertIsDisplayed()
        onNodeWithText("No Clip").assertIsDisplayed()
    }

    // --- Active Count ---

    @Test
    fun cheatsSectionShowsActiveCount() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("cheats_section"))
        onNodeWithText("1 active").assertIsDisplayed()
    }

    // --- Toggle ---

    @Test
    fun toggleCheatUpdatesState() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Toggle "Infinite Lives" (c1) on
        harness.gameDetailViewModel.onIntent(GameDetailIntent.ToggleCheat("c1", true))
        advance(harness)

        // Verify the fake repo was updated
        assertTrue(harness.cheatRepo.cheats.first { it.id == "c1" }.enabled)
    }

    // --- Load After Download ---

    @Test
    fun cheatsLoadedAfterDownloadingGame() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Cheats should already be loaded from initial loadGame → loadCheats
        val stateBefore = harness.gameDetailViewModel.state.value
        assertEquals(3, stateBefore.cheats.size, "cheats loaded on initial game load")

        // Clear cheats in repo to simulate a scenario where initial load didn't have them,
        // then re-add them so the post-download loadCheats can fetch them
        harness.cheatRepo.cheats.clear()
        // Force clear VM state to simulate no cheats loaded
        harness.gameDetailViewModel.onIntent(GameDetailIntent.LoadCheats("1"))
        advance(harness)
        assertEquals(0, harness.gameDetailViewModel.state.value.cheats.size, "cheats cleared")

        // Re-add cheats to repo so post-download loadCheats finds them
        harness.cheatRepo.cheats.addAll(testCheats)

        // Trigger download — the onSuccess callback should call loadCheats
        harness.gameDetailViewModel.onIntent(GameDetailIntent.DownloadGame)
        advanceFully(harness)

        val stateAfter = harness.gameDetailViewModel.state.value
        assertTrue(stateAfter.isGameCached, "game should be cached after download")
        assertEquals(3, stateAfter.cheats.size, "cheats should be re-loaded after download")
    }


    // --- Disable All ---

    @Test
    fun disableAllCheatsDisablesAllForGame() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Enable one more cheat first
        harness.gameDetailViewModel.onIntent(GameDetailIntent.ToggleCheat("c1", true))
        advance(harness)

        // Disable all
        harness.gameDetailViewModel.onIntent(GameDetailIntent.DisableAllCheats)
        advance(harness)

        // All cheats should be disabled in the fake repo
        harness.cheatRepo.cheats.filter { it.gameId == "1" }.forEach { cheat ->
            assertFalse(cheat.enabled, "Cheat ${cheat.description} should be disabled")
        }
    }
}
