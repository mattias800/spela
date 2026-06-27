package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.NetplaySession
import com.spela.player.domain.model.NetplaySessionStatus
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionMember
import com.spela.player.domain.model.UserSearchResult
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E2E tests for Netplay Lobby invite and Shared Session invite functionality.
 * Tests: invite button visibility, invite sheet dialog, search, invite action,
 * and shared session invite sheet.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class NetplayInviteTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.simulateLoggedIn()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun createHostSession(
        clientUserId: String? = null,
        clientUsername: String? = null,
        status: NetplaySessionStatus = NetplaySessionStatus.WAITING,
    ) = NetplaySession(
        id = "session-1",
        gameId = "1",
        gameTitle = "Castlevania",
        gameConsoleName = "NES",
        hostUserId = "1",
        hostUsername = "player",
        clientUserId = clientUserId,
        clientUsername = clientUsername,
        status = status,
        inputDelay = 3,
        inviteCode = "ABCD1234",
    )

    private fun createSharedSessionDetail() = SharedSessionDetail(
        id = "ss1",
        name = "Test Session",
        gameId = "1",
        gameTitle = "Castlevania",
        ownerId = "1",
        ownerUsername = "player",
        memberCount = 1,
        members = listOf(
            SharedSessionMember(userId = "1", username = "player", role = "owner"),
        ),
    )

    private fun ComposeUiTest.navigateToNetplayLobby(harness: SpelaTestHarness) {
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.NetplayLobby("session-1"))
        )
        advanceFully(harness)
    }

    private fun ComposeUiTest.navigateToSharedSession(harness: SpelaTestHarness) {
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advanceFully(harness)
    }

    private fun ComposeUiTest.openInviteSheet(harness: SpelaTestHarness) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)
    }

    @Test
    fun netplayLobbyShowsInviteButtonWhenHostAndWaiting() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession()

        setContent { harness.App() }
        navigateToNetplayLobby(harness)

        onNodeWithText("Castlevania").assertExists()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").assertExists()
    }

    @Test
    fun netplayLobbyHidesInviteButtonWhenNotHost() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = NetplaySession(
            id = "session-1",
            gameId = "1",
            gameTitle = "Castlevania",
            hostUserId = "2",
            hostUsername = "other-player",
            clientUserId = "1",
            clientUsername = "player",
            status = NetplaySessionStatus.WAITING,
            inputDelay = 3,
            inviteCode = "ABCD1234",
        )

        setContent { harness.App() }
        navigateToNetplayLobby(harness)

        onNodeWithText("Castlevania").assertExists()
        onNodeWithText("Invite Player").assertDoesNotExist()
    }

    @Test
    fun netplayLobbyHidesInviteButtonWhenClientHasJoined() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession(
            clientUserId = "2",
            clientUsername = "alice",
        )

        setContent { harness.App() }
        navigateToNetplayLobby(harness)

        onNodeWithText("Castlevania").assertExists()
        onNodeWithText("Invite Player").assertDoesNotExist()
    }

    @Test
    fun netplayLobbyHidesInviteButtonWhenSessionInProgress() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession(
            clientUserId = "2",
            clientUsername = "alice",
            status = NetplaySessionStatus.IN_PROGRESS,
        )

        setContent { harness.App() }
        navigateToNetplayLobby(harness)

        onNodeWithText("Castlevania").assertExists()
        onNodeWithText("Invite Player").assertDoesNotExist()
    }

    @Test
    fun netplayInviteSheetShowsUsersInvitesAndCloses() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession()
        harness.userRepo.recentPartners = listOf(
            UserSearchResult(id = "5", username = "recent-buddy", avatarUrl = null),
        )
        harness.userRepo.searchResults = listOf(
            UserSearchResult(id = "2", username = "alice", avatarUrl = null),
            UserSearchResult(id = "3", username = "bob", avatarUrl = null),
        )

        setContent { harness.App() }
        navigateToNetplayLobby(harness)
        openInviteSheet(harness)

        onNodeWithText("Search users...").assertExists()
        onNodeWithText("All Users").assertExists()
        onNodeWithText("Previous").assertExists()
        onNodeWithText("recent-buddy").assertExists()
        onNodeWithText("alice").assertExists()
        onNodeWithText("bob").assertExists()

        onAllNodesWithText("Invite").filter(hasClickAction())[1].performClick()
        advanceQuick(harness)

        onNodeWithContentDescription("alice, already invited").assertExists()
        onNodeWithContentDescription("Invited").assertExists()
        onNodeWithContentDescription("bob, tap to invite").assertExists()

        val state = harness.netplayLobbyViewModel.state.value
        assertEquals("Invite sent to alice", state.inviteSuccessMessage)
        assertTrue(state.invitedUsernames.contains("alice"))

        onNodeWithContentDescription("Close").performClick()
        advanceQuick(harness)
        onNodeWithText("All Users").assertDoesNotExist()
    }

    @Test
    fun netplayInviteSheetShowsNoUsersFoundWhenEmpty() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession()
        harness.userRepo.searchResults = emptyList()

        setContent { harness.App() }
        navigateToNetplayLobby(harness)
        openInviteSheet(harness)

        onNodeWithText("No users found").assertExists()
    }

    @Test
    fun sharedSessionInviteSheetShowsUsersInvitesAndCloses() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessionDetail = createSharedSessionDetail()
        harness.userRepo.searchResults = listOf(
            UserSearchResult(id = "2", username = "alice", avatarUrl = null),
            UserSearchResult(id = "3", username = "charlie", avatarUrl = null),
        )
        harness.userRepo.recentPartners = listOf(
            UserSearchResult(id = "4", username = "old-friend", avatarUrl = null),
        )

        setContent { harness.App() }
        navigateToSharedSession(harness)

        onNodeWithText("Invite a friend").assertExists()

        openInviteSheet(harness)

        onNodeWithText("Search users...").assertExists()
        onNodeWithText("All Users").assertExists()
        onNodeWithText("Previous").assertExists()
        onNodeWithText("old-friend").assertExists()
        onNodeWithText("alice").assertExists()
        onNodeWithText("charlie").assertExists()

        onAllNodesWithText("Invite").filter(hasClickAction())[1].performClick()
        advanceFully(harness)

        onNodeWithContentDescription("alice, already invited").assertExists()
        assertTrue(harness.sharedSessionDetailViewModel.state.value.invitedUsernames.contains("alice"))

        onNodeWithContentDescription("Close").performClick()
        advanceQuick(harness)
        onNodeWithText("All Users").assertDoesNotExist()
    }
}
