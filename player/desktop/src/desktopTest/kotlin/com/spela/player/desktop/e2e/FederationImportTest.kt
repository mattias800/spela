package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ConnectedConsole
import com.spela.player.domain.model.RemoteGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop UI tests for the connected-servers browse + import flow (#1391).
 * Covers: browse grid, empty state, the capability gate on the remote-game
 * detail, and starting an import.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class FederationImportTest {

    private fun harness(
        consoles: List<ConnectedConsole> = listOf(ConnectedConsole("SNES", 1)),
        games: Map<String, List<RemoteGame>> = mapOf(
            "SNES" to listOf(
                RemoteGame(
                    key = "igdb:1022",
                    title = "Chrono Trigger",
                    console = "SNES",
                    coverUrl = null,
                    originCount = 2,
                    local = false,
                ),
            ),
        ),
    ): SpelaTestHarness {
        val h = SpelaTestHarness(StandardTestDispatcher())
        h.federationRepo.consoles = consoles
        h.federationRepo.gamesByConsole = games
        return h
    }

    @Test
    fun browseShowsConnectedServerGames() = runComposeUiTest {
        val h = harness()
        setContent { h.App() }
        h.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ConnectedServers))
        advance(h)

        onNodeWithTag("connected_servers_screen").assertExists()
        onNodeWithTag("remote_game_igdb:1022", useUnmergedTree = true).assertExists()
        onNodeWithText("Chrono Trigger").assertExists()
    }

    @Test
    fun emptyStateWhenNoConnectedServerGames() = runComposeUiTest {
        val h = harness(consoles = emptyList(), games = emptyMap())
        setContent { h.App() }
        h.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ConnectedServers))
        advance(h)

        onNodeWithText("No connected-server games").assertExists()
    }

    @Test
    fun nonImporterSeesPermissionNotice() = runComposeUiTest {
        val h = harness()
        h.authRepo.simulateLoggedIn() // plain "player" role, no import capability
        setContent { h.App() }
        h.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.RemoteGameDetail("igdb:1022")))
        advanceFully(h)

        onNodeWithTag("remote_game_detail_screen").assertExists()
        onNodeWithTag("import_game_button").assertDoesNotExist()
        onNodeWithText("don't have permission", substring = true).assertExists()
    }

    @Test
    fun adminCanStartImport() = runComposeUiTest {
        val h = harness()
        h.authRepo.simulateAdminLoggedIn()
        setContent { h.App() }
        h.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.RemoteGameDetail("igdb:1022")))
        advanceFully(h)

        onNodeWithTag("import_game_button").assertExists()
        onNodeWithTag("import_game_button").performClick()
        advanceFully(h)

        // The import was enqueued and the progress UI replaced the button.
        assertEquals(1, h.federationRepo.imports.size)
        onNodeWithTag("import_progress", useUnmergedTree = true).assertExists()
    }
}
