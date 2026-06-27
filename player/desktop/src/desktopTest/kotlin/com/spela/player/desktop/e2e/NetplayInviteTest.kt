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

    private fun ComposeUiTest.navigateToNetplayLobby(harness: SpelaTestHarness) {
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.NetplayLobby("session-1"))
        )
        advanceFully(harness)
    }

    // ---- Netplay lobby: invite button visibility ----

    @Test
    fun netplayLobbyShowsInviteButtonWhenHostAndWaiting() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession()

        setContent { harness.App() }
        navigateToNetplayLobby(harness)

        // Verify session loaded
        onNodeWithText("Castlevania").assertExists()
        // Scroll to and verify invite button (may be below fold in LazyColumn)
        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").assertExists()
    }

    @Test
    fun netplayLobbyHidesInviteButtonWhenNotHost() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Current user is "1" (from FakeAuthRepository), but host is "2"
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

        // Verify session loaded (we can see the game title)
        onNodeWithText("Castlevania").assertExists()
        // Invite Player should NOT exist — user is not host
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
        // Invite Player should NOT exist — client has joined
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
        // Invite Player should NOT exist — session is in progress
        onNodeWithText("Invite Player").assertDoesNotExist()
    }

    // ---- Netplay lobby: invite sheet opens/closes ----

    @Test
    fun inviteSheetShowsUsersAndRecentPartners() = runComposeUiTest {
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

        // Scroll to and click the Invite Player button
        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)

        // The invite sheet dialog should be open — header text "Invite Player" in dialog
        // plus search field and user list section
        onNodeWithText("Search users...").assertExists()
        onNodeWithText("All Users").assertExists()
        onNodeWithText("Previous").assertExists()
        onNodeWithText("recent-buddy").assertExists()
        onNodeWithText("alice").assertExists()
        onNodeWithText("bob").assertExists()
    }

    @Test
    fun closingInviteSheetDismissesDialog() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession()
        harness.userRepo.searchResults = listOf(
            UserSearchResult(id = "2", username = "alice", avatarUrl = null),
        )

        setContent { harness.App() }
        navigateToNetplayLobby(harness)

        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)

        // Close via X button
        onNodeWithContentDescription("Close").performClick()
        advanceQuick(harness)

        // Dialog should be gone — "All Users" only appears in the dialog
        onNodeWithText("All Users").assertDoesNotExist()
    }

    @Test
    fun inviteSheetShowsNoUsersFoundWhenEmpty() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession()
        harness.userRepo.searchResults = emptyList()

        setContent { harness.App() }
        navigateToNetplayLobby(harness)

        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)

        onNodeWithText("No users found").assertExists()
    }

    // ---- Netplay lobby: invite action ----

    @Test
    fun invitingUserMarksThemAsInvited() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession()
        harness.userRepo.searchResults = listOf(
            UserSearchResult(id = "2", username = "alice", avatarUrl = null),
        )

        setContent { harness.App() }
        navigateToNetplayLobby(harness)

        // Open invite sheet
        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)

        // alice should be invitable
        onNodeWithContentDescription("alice, tap to invite").assertExists()
        // Click the Invite button in alice's row
        onAllNodesWithText("Invite").filterToOne(hasClickAction()).performClick()
        advanceFully(harness)

        // alice should now show as invited
        onNodeWithContentDescription("alice, already invited").assertExists()
        onNodeWithContentDescription("Invited").assertExists()
    }

    @Test
    fun invitedUserCannotBeInvitedAgain() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession()
        harness.userRepo.searchResults = listOf(
            UserSearchResult(id = "2", username = "alice", avatarUrl = null),
            UserSearchResult(id = "3", username = "bob", avatarUrl = null),
        )

        setContent { harness.App() }
        navigateToNetplayLobby(harness)

        // Open invite sheet
        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)

        // Invite alice
        onAllNodesWithText("Invite").onFirst().performClick()
        advanceFully(harness)

        // alice should be marked as invited
        onNodeWithContentDescription("alice, already invited").assertExists()
        // bob should still be invitable
        onNodeWithContentDescription("bob, tap to invite").assertExists()
    }

    @Test
    fun inviteShowsSuccessMessage() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.netplayRepo.currentSession = createHostSession()
        harness.userRepo.searchResults = listOf(
            UserSearchResult(id = "2", username = "alice", avatarUrl = null),
        )

        setContent { harness.App() }
        navigateToNetplayLobby(harness)

        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)

        onAllNodesWithText("Invite").filterToOne(hasClickAction()).performClick()
        // Use advanceQuick (2000ms) — enough for coroutine to complete but
        // before SpSnackbar's 3000ms auto-dismiss clears inviteSuccessMessage.
        advanceQuick(harness)

        // ViewModel state should have success message
        val state = harness.netplayLobbyViewModel.state.value
        assertEquals("Invite sent to alice", state.inviteSuccessMessage)
        assertTrue(state.invitedUsernames.contains("alice"))
    }

    // ---- Shared session detail: invite button and sheet ----

    @Test
    fun sharedSessionDetailInviteButtonOpensInviteSheet() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessionDetail = SharedSessionDetail(
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
        harness.userRepo.searchResults = listOf(
            UserSearchResult(id = "2", username = "alice", avatarUrl = null),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advanceFully(harness)

        // The "Invite a friend" section should be visible
        onNodeWithText("Invite a friend").assertExists()

        // Click the "Invite Player" button
        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)

        // The invite sheet dialog should open
        onNodeWithText("Search users...").assertExists()
        onNodeWithText("All Users").assertExists()
    }

    @Test
    fun sharedSessionInviteSheetShowsUsersAndRecentPartners() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
            name = "Test Session",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 1,
            members = emptyList(),
        )
        harness.userRepo.searchResults = listOf(
            UserSearchResult(id = "2", username = "alice", avatarUrl = null),
            UserSearchResult(id = "3", username = "charlie", avatarUrl = null),
        )
        harness.userRepo.recentPartners = listOf(
            UserSearchResult(id = "4", username = "old-friend", avatarUrl = null),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advanceFully(harness)

        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)

        // Should show recent partners
        onNodeWithText("Previous").assertExists()
        onNodeWithText("old-friend").assertExists()
        // Should show all users
        onNodeWithText("alice").assertExists()
        onNodeWithText("charlie").assertExists()
    }

    @Test
    fun sharedSessionInviteSheetInviteMarksUserAsInvited() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
            name = "Test Session",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 1,
            members = emptyList(),
        )
        harness.userRepo.searchResults = listOf(
            UserSearchResult(id = "2", username = "alice", avatarUrl = null),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advanceFully(harness)

        // Open invite sheet
        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)

        // Invite alice
        onNodeWithContentDescription("alice, tap to invite").assertExists()
        onAllNodesWithText("Invite").filterToOne(hasClickAction()).performClick()
        advanceFully(harness)

        // alice should be marked as invited
        onNodeWithContentDescription("alice, already invited").assertExists()

        // ViewModel state should reflect the invite
        val state = harness.sharedSessionDetailViewModel.state.value
        assertTrue(state.invitedUsernames.contains("alice"))
    }

    @Test
    fun sharedSessionInviteSheetCloseButton() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
            name = "Test Session",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 1,
            members = emptyList(),
        )
        harness.userRepo.searchResults = listOf(
            UserSearchResult(id = "2", username = "alice", avatarUrl = null),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advanceFully(harness)

        // Open invite sheet
        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Invite Player"))
        onNodeWithText("Invite Player").performClick()
        advanceFully(harness)

        // Verify sheet is open
        onNodeWithText("Search users...").assertExists()

        // Close it
        onNodeWithContentDescription("Close").performClick()
        advanceQuick(harness)

        // Dialog should be gone
        onNodeWithText("All Users").assertDoesNotExist()
    }
}
