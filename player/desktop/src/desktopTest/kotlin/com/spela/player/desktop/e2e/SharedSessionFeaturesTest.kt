package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionInvitation
import com.spela.player.domain.model.SharedSessionMember
import com.spela.player.domain.model.SharedSessionSave
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * E2E tests for the Shared Session feature on desktop.
 * Tests: navigation to shared sessions screen, shared session list display, invitations,
 * shared session detail view, members, turn controls, and saves.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SharedSessionFeaturesTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // ---- Shared sessions screen: navigation ----

    @Test
    fun sharedSessionsScreenIsAccessibleViaNavigation() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessions)
        )
        advance(harness)

        // Should navigate to Shared Sessions screen (shows empty state or list)
        onNodeWithContentDescription("Go back").assertIsDisplayed()
    }

    // ---- Shared sessions screen: empty state ----

    @Test
    fun sharedSessionsScreenShowsEmptyStateWhenNoSharedSessions() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessions)
        )
        advance(harness)

        onNodeWithText("No shared sessions yet").assertIsDisplayed()
    }

    // ---- Shared sessions screen: list ----

    @Test
    fun sharedSessionsScreenShowsList() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessions = listOf(
            SharedSession(
                id = "ss1",
                name = "Zelda Race",
                gameId = "1",
                gameTitle = "Castlevania",
                ownerId = "1",
                ownerUsername = "player",
                status = "active",
                memberCount = 3,
            ),
            SharedSession(
                id = "ss2",
                name = "Mario Session",
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
            NavigationIntent.NavigateTo(SpScreen.SharedSessions)
        )
        advance(harness)

        onNodeWithText("My Shared Sessions").assertExists()
        onNodeWithContentDescription("Shared Session: Zelda Race, game: Castlevania").assertExists()
        onNodeWithContentDescription("Shared Session: Mario Session, game: Super Mario Bros.").assertExists()
    }

    @Test
    fun sharedSessionsScreenShowsStatusChips() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessions = listOf(
            SharedSession(
                id = "ss1",
                name = "Active Session",
                gameId = "1",
                gameTitle = "Castlevania",
                ownerId = "1",
                status = "active",
                memberCount = 2,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessions)
        )
        advance(harness)

        onNodeWithText("Active").assertExists()
        onNodeWithText("2 members").assertExists()
    }

    // ---- Shared sessions screen: invitations ----

    @Test
    fun sharedSessionsScreenShowsInvitations() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.invitations = listOf(
            SharedSessionInvitation(
                id = "inv1",
                sharedSessionId = "ss1",
                sharedSessionName = "Zelda Race",
                gameId = "1",
                gameTitle = "Castlevania",
                inviterUsername = "alice",
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessions)
        )
        advance(harness)

        onNodeWithText("Invitations").assertExists()
        onNodeWithText("Zelda Race").assertExists()
        onNodeWithText("Invited by alice").assertExists()
        onNodeWithText("Accept").assertExists()
        onNodeWithText("Decline").assertExists()
    }

    @Test
    fun sharedSessionsScreenShowsBothInvitationsAndSessions() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.invitations = listOf(
            SharedSessionInvitation(
                id = "inv1",
                sharedSessionId = "ss1",
                sharedSessionName = "Zelda Race",
                gameId = "1",
                gameTitle = "Castlevania",
                inviterUsername = "alice",
            ),
        )
        harness.sharedSessionRepo.sharedSessions = listOf(
            SharedSession(
                id = "ss2",
                name = "Mario Session",
                gameId = "2",
                gameTitle = "Super Mario Bros.",
                ownerId = "1",
                status = "active",
                memberCount = 2,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessions)
        )
        advance(harness)

        onNodeWithText("Invitations").assertExists()
        onNodeWithText("My Shared Sessions").assertExists()
    }

    // ---- Shared session detail screen ----

    @Test
    fun sharedSessionDetailScreenShowsInfo() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
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
                SharedSessionMember(userId = "1", username = "player", role = "owner", isOnline = true),
                SharedSessionMember(userId = "2", username = "alice", role = "member", isOnline = false),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithText("Zelda Race").assertExists()
        onNodeWithText("Castlevania").assertExists()
        onNodeWithText("NES").assertExists()
        onNodeWithText("Racing through Zelda together").assertExists()
    }

    @Test
    fun sharedSessionDetailScreenShowsMembers() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
            name = "Test Session",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 2,
            members = listOf(
                SharedSessionMember(userId = "1", username = "player", role = "owner", isOnline = true),
                SharedSessionMember(userId = "2", username = "alice", role = "member", isOnline = false),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithText("Members").assertExists()
        onNodeWithContentDescription("player, owner").assertExists()
        onNodeWithContentDescription("alice, member").assertExists()
        onNodeWithText("Owner").assertExists()
    }

    @Test
    fun sharedSessionDetailScreenShowsOnlineMemberStatus() = runComposeUiTest {
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
                SharedSessionMember(userId = "1", username = "player", role = "owner", isOnline = true),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithText("Online").assertExists()
    }

    @Test
    fun sharedSessionDetailScreenShowsTakeTurnButton() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
            name = "Test Session",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 1,
            activeUserId = null,
            members = listOf(
                SharedSessionMember(userId = "1", username = "player", role = "owner"),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithText("Take Turn").assertIsDisplayed()
    }

    @Test
    fun sharedSessionDetailScreenShowsCurrentlyPlayingWhenOtherUserHasTurn() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.sharedSessionRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
            name = "Test Session",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 2,
            activeUserId = "2",
            members = listOf(
                SharedSessionMember(userId = "1", username = "player", role = "owner"),
                SharedSessionMember(userId = "2", username = "alice", role = "member"),
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithText("alice is currently playing").assertIsDisplayed()
    }

    // ---- Shared session detail: saves ----

    @Test
    fun sharedSessionDetailScreenShowsEmptySavesState() = runComposeUiTest {
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
        harness.sharedSessionRepo.sharedSessionSaves = emptyList()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithText("Save States").assertExists()
        onNodeWithText("No saves yet").assertExists()
    }

    @Test
    fun sharedSessionDetailScreenShowsSaves() = runComposeUiTest {
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
        harness.sharedSessionRepo.sharedSessionSaves = listOf(
            SharedSessionSave(
                id = 1,
                sharedSessionId = "ss1",
                username = "player",
                name = "Boss fight save",
                fileSize = 4096,
                isAuto = false,
            ),
            SharedSessionSave(
                id = 2,
                sharedSessionId = "ss1",
                username = "alice",
                name = "Auto Save",
                fileSize = 4096,
                isAuto = true,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithText("Save States").assertExists()
        onNodeWithText("Boss fight save").assertExists()
        onNodeWithText("by player").assertExists()
        onNodeWithText("Auto Save").assertExists()
        onNodeWithText("Auto").assertExists()
    }

    // ---- Shared session detail: save age display ----

    @Test
    fun sharedSessionDetailScreenShowsSaveAge() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val threeHoursAgo = (Clock.System.now() - 3.hours).toString()
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
        harness.sharedSessionRepo.sharedSessionSaves = listOf(
            SharedSessionSave(
                id = 1,
                sharedSessionId = "ss1",
                username = "player",
                name = "Boss fight save",
                fileSize = 4096,
                isAuto = false,
                createdAt = threeHoursAgo,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithText("Boss fight save").assertExists()
        onNodeWithText("by player · 3h ago", substring = true).assertExists()
    }

    // ---- Shared session detail: copy save to game ----

    @Test
    fun sharedSessionDetailScreenShowsCopyButtonOnSaves() = runComposeUiTest {
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
        harness.sharedSessionRepo.sharedSessionSaves = listOf(
            SharedSessionSave(
                id = 1,
                sharedSessionId = "ss1",
                username = "player",
                name = "Boss fight save",
                fileSize = 4096,
                isAuto = false,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithContentDescription("Copy Boss fight save to your library").assertExists()
    }

    @Test
    fun sharedSessionDetailScreenCopySaveShowsSuccessMessage() = runComposeUiTest {
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
        harness.sharedSessionRepo.sharedSessionSaves = listOf(
            SharedSessionSave(
                id = 1,
                sharedSessionId = "ss1",
                username = "player",
                name = "Boss fight save",
                fileSize = 4096,
                isAuto = false,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithContentDescription("Copy Boss fight save to your library").assertExists()
        onNodeWithContentDescription("Copy Boss fight save to your library").performClick()
        // Advance test dispatcher only (not Compose clock) to process ViewModel coroutine
        harness.testDispatcher.scheduler.advanceTimeBy(2_000)
        harness.testDispatcher.scheduler.runCurrent()

        val state = harness.sharedSessionDetailViewModel.state.value
        assertEquals("Save copied to your library", state.successMessage)
    }

    // ---- Shared session detail: invite section ----

    @Test
    fun sharedSessionDetailScreenShowsInviteSection() = runComposeUiTest {
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

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail("ss1"))
        )
        advance(harness)

        onNodeWithText("Invite a friend").assertExists()
        onNodeWithText("Invite").assertExists()
    }
}
