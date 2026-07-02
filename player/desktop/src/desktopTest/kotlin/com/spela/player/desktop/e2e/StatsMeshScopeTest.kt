package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.MeshStat
import com.spela.player.domain.model.MeshStatMetric
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.state.StatScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Desktop UI tests for the "This server | Across connected servers" scope toggle
 * on the Stats screen's Most Played Games section (player mirror of the web
 * mesh-stats work). Verifies switching to the mesh scope swaps in federated rows
 * and shows an empty state when no connected server has shared stats.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class StatsMeshScopeTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.awaitText(
        harness: SpelaTestHarness,
        text: String,
        useUnmergedTree: Boolean = false,
    ) {
        // 6 rounds to match awaitMostPlayedMeshState below. 3 rounds still
        // flaked once under full-suite load after the #1547 UI-thread drain
        // fix (which removed the permanent composition freeze — the remaining
        // variance is plain settling latency, so a larger budget is the fix).
        repeat(6) {
            advanceQuick(harness)
            if (onAllNodesWithText(text, useUnmergedTree = useUnmergedTree)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
                return
            }
        }
        onNodeWithText(text, useUnmergedTree = useUnmergedTree).assertExists()
    }

    private fun ComposeUiTest.awaitMostPlayedMeshState(
        harness: SpelaTestHarness,
        description: String,
        isReady: () -> Boolean,
    ) {
        repeat(6) {
            advanceQuick(harness)
            if (isReady()) {
                return
            }
        }
        assertTrue(isReady(), description)
    }

    @Test
    fun mostPlayedTogglesBetweenThisServerAndMesh() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val game = harness.gameRepo.games.first()
        harness.statsRepo.mostPlayedGames = listOf(
            MostPlayedGame(game = game, totalPlayers = 5, totalPlayTime = 3600),
        )
        harness.federationRepo.meshStatsByMetric = mapOf(
            MeshStatMetric.GamePlay to listOf(
                MeshStat(key = "igdb:1022", label = "Mesh Favorite", playTimeSeconds = 7200, players = 9),
            ),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Stats))
        advance(harness)

        // "This server" scope: the local game is shown, the mesh row is not.
        // useUnmergedTree: the row text lives inside an SpCard whose own
        // contentDescription absorbs child text in the merged tree.
        onNodeWithText(game.title, useUnmergedTree = true).assertExists()
        onNodeWithText("Mesh Favorite", useUnmergedTree = true).assertDoesNotExist()

        // Switch to "Across servers".
        onNodeWithTag("most-played-across").performClick()
        awaitMostPlayedMeshState(
            harness = harness,
            description = "Expected most-played mesh stats to load after switching scope",
        ) {
            val state = harness.statsViewModel.state.value
            state.mostPlayedScope == StatScope.AcrossServers &&
                !state.isLoadingMeshMostPlayed &&
                state.meshMostPlayed.any { it.label == "Mesh Favorite" }
        }

        awaitText(harness, "Mesh Favorite", useUnmergedTree = true)
    }

    @Test
    fun mostPlayedMeshScopeShowsEmptyStateWhenNoMeshData() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val game = harness.gameRepo.games.first()
        harness.statsRepo.mostPlayedGames = listOf(
            MostPlayedGame(game = game, totalPlayers = 5, totalPlayTime = 3600),
        )
        // No federation mesh stats configured.

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Stats))
        advance(harness)

        onNodeWithTag("most-played-across").performClick()
        awaitMostPlayedMeshState(
            harness = harness,
            description = "Expected most-played mesh empty state to finish loading after switching scope",
        ) {
            val state = harness.statsViewModel.state.value
            state.mostPlayedScope == StatScope.AcrossServers &&
                !state.isLoadingMeshMostPlayed &&
                state.meshMostPlayed.isEmpty()
        }

        awaitText(harness, "Nothing across connected servers yet")
    }
}
