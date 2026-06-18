package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ConnectedConsole
import com.spela.player.domain.model.FriendPresence
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Desktop UI tests for the "Playing now across connected servers" presence
 * section on the Connected Servers screen (#1403, player follow-up). Verifies
 * the section renders remote players, filters out local ones, and stays hidden
 * when no one is playing.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ConnectedServersPresenceTest {

    private fun harness(presence: List<FriendPresence>): SpelaTestHarness {
        val h = SpelaTestHarness(StandardTestDispatcher())
        h.federationRepo.consoles = listOf(ConnectedConsole("SNES", 1))
        h.federationRepo.presence = presence
        return h
    }

    @Test
    fun showsRemotePlayersPlayingNow() = runComposeUiTest {
        val h = harness(
            listOf(
                FriendPresence(
                    username = "alice",
                    gameKey = "igdb:1022",
                    gameTitle = "Chrono Trigger",
                    serverName = "Server B",
                    hops = 1,
                ),
            ),
        )
        setContent { h.App() }
        h.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ConnectedServers))
        advance(h)

        onNodeWithTag("friends_playing_now", useUnmergedTree = true).assertExists()
        onNodeWithText("alice").assertExists()
        onNodeWithText("Chrono Trigger", substring = true).assertExists()
        onNodeWithText("Server B", substring = true).assertExists()
    }

    @Test
    fun filtersOutLocalPlayers() = runComposeUiTest {
        val h = harness(
            listOf(
                FriendPresence("localguy", "igdb:1", "Mario", "", hops = 0),
                FriendPresence("remotebob", "igdb:2", "Zelda", "Server B", hops = 1),
            ),
        )
        setContent { h.App() }
        h.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ConnectedServers))
        advance(h)

        onNodeWithText("remotebob").assertExists()
        onNodeWithText("localguy").assertDoesNotExist()
    }

    @Test
    fun hidesSectionWhenNobodyPlaying() = runComposeUiTest {
        val h = harness(emptyList())
        setContent { h.App() }
        h.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.ConnectedServers))
        advance(h)

        onNodeWithTag("connected_servers_screen").assertExists()
        onNodeWithTag("friends_playing_now", useUnmergedTree = true).assertDoesNotExist()
    }
}
