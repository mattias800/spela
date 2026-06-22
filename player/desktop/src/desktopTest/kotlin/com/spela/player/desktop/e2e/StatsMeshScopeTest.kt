package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.MeshStat
import com.spela.player.domain.model.MeshStatMetric
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

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
        advance(harness)

        onNodeWithText("Mesh Favorite", useUnmergedTree = true).assertExists()
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
        advance(harness)

        onNodeWithText("Nothing across connected servers yet").assertExists()
    }
}
