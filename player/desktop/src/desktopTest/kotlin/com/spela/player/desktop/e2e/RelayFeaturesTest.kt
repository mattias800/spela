package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Relay
import com.spela.player.domain.model.RelayDetail
import com.spela.player.domain.model.RelayInvitation
import com.spela.player.domain.model.RelayMember
import com.spela.player.domain.model.RelaySave
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the Relay feature on desktop.
 * Tests: navigation to relays screen, relay list display, invitations,
 * relay detail view, members, turn controls, and saves.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class RelayFeaturesTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // ---- Home screen: Relays navigation ----

    @Test
    fun homeScreenShowsRelaysButton() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        onNodeWithContentDescription("Relays").assertIsDisplayed()
    }

    // ---- Relays screen: empty state ----

    @Test
    fun relaysScreenShowsEmptyStateWhenNoRelays() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Relays)
        )
        advance(harness)

        onNodeWithText("No relays yet").assertIsDisplayed()
    }

    // ---- Relays screen: relay list ----

    @Test
    fun relaysScreenShowsRelayList() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.relays = listOf(
            Relay(
                id = "r1",
                name = "Zelda Race",
                gameId = "1",
                gameTitle = "Castlevania",
                ownerId = "1",
                ownerUsername = "player",
                status = "active",
                memberCount = 3,
            ),
            Relay(
                id = "r2",
                name = "Mario Relay",
                gameId = "2",
                gameTitle = "Super Mario Bros.",
                ownerId = "2",
                ownerUsername = "bob",
                status = "paused",
                memberCount = 2,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Relays)
        )
        advance(harness)

        onNodeWithText("My Relays").assertExists()
        onNodeWithContentDescription("Relay: Zelda Race, game: Castlevania").assertExists()
        onNodeWithContentDescription("Relay: Mario Relay, game: Super Mario Bros.").assertExists()
    }

    @Test
    fun relaysScreenShowsRelayStatusChips() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.relays = listOf(
            Relay(
                id = "r1",
                name = "Active Relay",
                gameId = "1",
                gameTitle = "Castlevania",
                ownerId = "1",
                status = "active",
                memberCount = 2,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Relays)
        )
        advance(harness)

        onNodeWithText("Active").assertExists()
        onNodeWithText("2 members").assertExists()
    }

    // ---- Relays screen: invitations ----

    @Test
    fun relaysScreenShowsInvitations() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.invitations = listOf(
            RelayInvitation(
                id = "inv1",
                relayId = "r1",
                relayName = "Zelda Race",
                gameId = "1",
                gameTitle = "Castlevania",
                inviterUsername = "alice",
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Relays)
        )
        advance(harness)

        onNodeWithText("Invitations").assertExists()
        onNodeWithText("Zelda Race").assertExists()
        onNodeWithText("Invited by alice").assertExists()
        onNodeWithText("Accept").assertExists()
        onNodeWithText("Decline").assertExists()
    }

    @Test
    fun relaysScreenShowsBothInvitationsAndRelays() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.invitations = listOf(
            RelayInvitation(
                id = "inv1",
                relayId = "r1",
                relayName = "Zelda Race",
                gameId = "1",
                gameTitle = "Castlevania",
                inviterUsername = "alice",
            ),
        )
        harness.relayRepo.relays = listOf(
            Relay(
                id = "r2",
                name = "Mario Relay",
                gameId = "2",
                gameTitle = "Super Mario Bros.",
                ownerId = "1",
                status = "active",
                memberCount = 2,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Relays)
        )
        advance(harness)

        onNodeWithText("Invitations").assertExists()
        onNodeWithText("My Relays").assertExists()
    }

    // ---- Relay detail screen ----

    @Test
    fun relayDetailScreenShowsRelayInfo() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Zelda Race",
            description = "Racing through Zelda together",
            gameId = "1",
            gameTitle = "Castlevania",
            gameConsoleName = "NES",
            ownerId = "1",
            ownerUsername = "player",
            status = "active",
            memberCount = 2,
            members = listOf(
                RelayMember(userId = "1", username = "player", role = "owner", isOnline = true),
                RelayMember(userId = "2", username = "alice", role = "member", isOnline = false),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.RelayDetail("r1"))
        )
        advance(harness)

        onNodeWithText("Zelda Race").assertExists()
        onNodeWithText("Castlevania").assertExists()
        onNodeWithText("NES").assertExists()
        onNodeWithText("Racing through Zelda together").assertExists()
    }

    @Test
    fun relayDetailScreenShowsMembers() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 2,
            members = listOf(
                RelayMember(userId = "1", username = "player", role = "owner", isOnline = true),
                RelayMember(userId = "2", username = "alice", role = "member", isOnline = false),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.RelayDetail("r1"))
        )
        advance(harness)

        onNodeWithText("Members").assertExists()
        onNodeWithContentDescription("player, owner").assertExists()
        onNodeWithContentDescription("alice, member").assertExists()
        onNodeWithText("Owner").assertExists()
    }

    @Test
    fun relayDetailScreenShowsOnlineMemberStatus() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 1,
            members = listOf(
                RelayMember(userId = "1", username = "player", role = "owner", isOnline = true),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.RelayDetail("r1"))
        )
        advance(harness)

        onNodeWithText("Online").assertExists()
    }

    @Test
    fun relayDetailScreenShowsTakeTurnButton() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 1,
            activeUserId = null,
            members = listOf(
                RelayMember(userId = "1", username = "player", role = "owner"),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.RelayDetail("r1"))
        )
        advance(harness)

        onNodeWithText("Take Turn").assertIsDisplayed()
    }

    @Test
    fun relayDetailScreenShowsCurrentlyPlayingWhenOtherUserHasTurn() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 2,
            activeUserId = "2",
            members = listOf(
                RelayMember(userId = "1", username = "player", role = "owner"),
                RelayMember(userId = "2", username = "alice", role = "member"),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.RelayDetail("r1"))
        )
        advance(harness)

        onNodeWithText("alice is currently playing").assertIsDisplayed()
    }

    // ---- Relay detail: saves ----

    @Test
    fun relayDetailScreenShowsEmptySavesState() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 1,
            members = emptyList(),
        )
        harness.relayRepo.relaySaves = emptyList()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.RelayDetail("r1"))
        )
        advance(harness)

        onNodeWithText("Save States").assertExists()
        onNodeWithText("No saves yet").assertExists()
    }

    @Test
    fun relayDetailScreenShowsSaves() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 1,
            members = emptyList(),
        )
        harness.relayRepo.relaySaves = listOf(
            RelaySave(
                id = 1,
                relayId = "r1",
                username = "player",
                name = "Boss fight save",
                fileSize = 4096,
                isAuto = false,
            ),
            RelaySave(
                id = 2,
                relayId = "r1",
                username = "alice",
                name = "Auto Save",
                fileSize = 4096,
                isAuto = true,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.RelayDetail("r1"))
        )
        advance(harness)

        onNodeWithText("Save States").assertExists()
        onNodeWithText("Boss fight save").assertExists()
        onNodeWithText("by player").assertExists()
        onNodeWithText("Auto Save").assertExists()
        onNodeWithText("Auto").assertExists()
    }

    // ---- Relay detail: invite section ----

    @Test
    fun relayDetailScreenShowsInviteSection() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.relayRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 1,
            members = emptyList(),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.RelayDetail("r1"))
        )
        advance(harness)

        onNodeWithText("Invite a friend").assertExists()
        onNodeWithText("Invite").assertExists()
    }
}
