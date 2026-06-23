package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.MeshAchiever
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Desktop UI tests for the federated "Top Achievers" leaderboard section on the
 * Stats screen (player mirror of the web work). Verifies the section renders mesh
 * achievers and that the This-server | Across-servers toggle filters by hop.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class StatsTopAchieversTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun showsTopAchieversAndFiltersByScope() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // A local stat so the Stats screen renders its sections (the achievers
        // section lives inside that content, not the full-empty state).
        val game = harness.gameRepo.games.first()
        harness.statsRepo.mostPlayedGames = listOf(
            MostPlayedGame(game = game, totalPlayers = 1, totalPlayTime = 60),
        )
        harness.federationRepo.aggregatedAchievers = listOf(
            MeshAchiever(username = "remotebob", count = 20, serverName = "Server B", hops = 1),
            MeshAchiever(username = "localyou", count = 5, serverName = "", hops = 0),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Stats))
        advance(harness)

        // Default "across" scope: both the remote and local achiever show.
        onNodeWithText("Top Achievers", useUnmergedTree = true).assertExists()
        onNodeWithText("remotebob", useUnmergedTree = true).assertExists()
        onNodeWithText("localyou", useUnmergedTree = true).assertExists()

        // Toggle to "This server" → only the local (hop 0) achiever remains.
        onNodeWithTag("top-achievers-this-server").performClick()
        advance(harness)

        onNodeWithText("localyou", useUnmergedTree = true).assertExists()
        onNodeWithText("remotebob", useUnmergedTree = true).assertDoesNotExist()
    }
}
