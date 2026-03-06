package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Desktop E2E tests for the Sessions feature on the Game Detail screen.
 *
 * Covers:
 * - Session list rendering with pre-populated sessions
 * - Empty state when no sessions exist
 * - Creating a new session
 * - Renaming a session
 * - Deleting a session
 * - Multiple sessions display correctly
 * - Continue session triggers navigation with sessionId
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SessionsUiTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.scrollToSessions() {
        onNodeWithTag("game_detail_content")
            .performScrollToNode(hasTestTag("sessions_section"))
    }

    // ── Session list renders on game detail ──

    @Test
    fun sessionsDisplayOnGameDetail() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "My First Run",
            totalPlayTime = 3600,
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("sessions_section").assertIsDisplayed()
        onNodeWithText("Sessions").assertIsDisplayed()
        onNodeWithText("(1)").assertIsDisplayed()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNodeWithText("My First Run").assertIsDisplayed()
    }

    // ── Empty state shows correctly ──

    @Test
    fun emptyStateShowsWhenNoSessions() = runComposeUiTest {
        val harness = createHarness()
        // No sessions added

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("sessions_section").assertIsDisplayed()
        onNodeWithTag("sessions_empty").assertIsDisplayed()
        onNodeWithText("No sessions yet. Start a new playthrough to track your progress.")
            .assertIsDisplayed()
    }

    // ── Create new session works ──

    @Test
    fun createNewSession() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("create_session_button").performClick()
        advanceQuick(harness)

        // Dialog should appear with default name
        onNodeWithText("New Session").assertIsDisplayed()
        onNodeWithTag("create_session_input").assertIsDisplayed()

        // Clear and type a custom name
        onNodeWithTag("create_session_input").performTextClearance()
        onNodeWithTag("create_session_input").performTextInput("Speedrun Attempt")
        advanceQuick(harness)

        // Confirm creation
        onNodeWithText("Create").performClick()
        advance(harness)

        // Session should be created in the fake repo
        assertEquals(1, harness.sessionRepo.sessions.size)
        assertEquals("Speedrun Attempt", harness.sessionRepo.sessions[0].name)
    }

    // ── Rename session works ──

    @Test
    fun renameSession() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Old Name",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()

        // Click the rename button
        onNodeWithContentDescription("Rename session").performClick()
        advanceQuick(harness)

        // Rename dialog should appear
        onNodeWithText("Rename Session").assertIsDisplayed()
        onNodeWithTag("rename_session_input").assertIsDisplayed()

        // Clear and type new name
        onNodeWithTag("rename_session_input").performTextClearance()
        onNodeWithTag("rename_session_input").performTextInput("New Name")
        advanceQuick(harness)

        // Confirm rename
        onNodeWithText("Rename").performClick()
        advance(harness)

        // Session should be renamed in the fake repo
        assertEquals("New Name", harness.sessionRepo.sessions[0].name)
    }

    // ── Delete session works ──

    @Test
    fun deleteSession() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Doomed Run",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()

        // Click the delete button
        onNodeWithContentDescription("Delete session").performClick()
        advanceQuick(harness)

        // Delete confirmation dialog should appear
        onNodeWithText("Delete Session").assertIsDisplayed()
        onNodeWithText("Delete \"Doomed Run\"? All saves in this session will be permanently removed.")
            .assertIsDisplayed()

        // Confirm deletion
        onAllNodesWithText("Delete").filterToOne(hasClickAction()).performClick()
        advance(harness)

        // Session should be deleted from the fake repo
        assertTrue(harness.sessionRepo.sessions.isEmpty())
    }

    // ── Multiple sessions display correctly ──

    @Test
    fun multipleSessionsDisplayCorrectly() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Casual Run")
        harness.sessionRepo.preAddSession(id = "s2", gameId = "1", name = "100% Completion")
        harness.sessionRepo.preAddSession(id = "s3", gameId = "1", name = "No Damage Run")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithText("(3)").assertIsDisplayed()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNodeWithTag("session_item_s2").assertIsDisplayed()

        // Scroll to see the third session
        onNodeWithTag("game_detail_content")
            .performScrollToNode(hasTestTag("session_item_s3"))
        onNodeWithTag("session_item_s3").assertIsDisplayed()

        onNodeWithText("Casual Run").assertIsDisplayed()
        onNodeWithText("100% Completion").assertIsDisplayed()
        onNodeWithText("No Damage Run").assertIsDisplayed()
    }

    // ── Continue session triggers overlay with sessionId ──

    @Test
    fun continueSessionSetsSessionId() = runComposeUiTest {
        val harness = createHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "My Run",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()

        // Click the continue/play button on the session
        onNodeWithContentDescription("Continue session").performClick()
        advance(harness)

        // The navigation state should have overlay with sessionId set
        val navState = harness.navigationViewModel.state.value
        assertTrue(navState.showInGameOverlay || navState.overlaySessionId == "s1",
            "Expected session overlay to be triggered with sessionId s1")
    }

    // ── Sessions from other games don't appear ──

    @Test
    fun sessionsFilteredByGameId() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Castlevania Run")
        harness.sessionRepo.preAddSession(id = "s2", gameId = "2", name = "Mario Run")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithText("(1)").assertIsDisplayed()
        onNodeWithText("Castlevania Run").assertIsDisplayed()
        onNodeWithText("Mario Run").assertDoesNotExist()
    }

    // ── Multiplayer session shows member avatars ──

    @Test
    fun multiplayerSessionShowsMemberAvatars() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Co-op Run",
            memberCount = 3,
            memberAvatars = listOf(
                "https://example.com/avatar1.png",
                "https://example.com/avatar2.png",
                "https://example.com/avatar3.png",
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNode(hasTestTag("session_member_avatars_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag("session_multiplayer_badge_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Multiplayer").assertIsDisplayed()
    }

    // ── Multiplayer session shows "Last played by X" text ──

    @Test
    fun multiplayerSessionShowsLastPlayedBy() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Shared Run",
            memberCount = 2,
            lastPlayedByUsername = "player2",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNodeWithText("by player2", substring = true).assertIsDisplayed()
    }

    // ── Shared session shows Shared Session badge ──

    @Test
    fun sharedSessionShowsSharedSessionBadge() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Shared Session",
            memberCount = 2,
            isSharedSession = true,
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNode(hasTestTag("session_multiplayer_badge_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Shared Session").assertIsDisplayed()
    }

    // ── First session shows "Current" badge ──

    @Test
    fun firstSessionShowsCurrentBadge() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Main Run")
        harness.sessionRepo.preAddSession(id = "s2", gameId = "1", name = "Side Run")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        // First session should have the "Current" badge
        onNode(hasTestTag("session_current_badge"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Current").assertIsDisplayed()
    }

    // ── Only the first session shows "Current" badge ──

    @Test
    fun onlyFirstSessionShowsCurrentBadge() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "First Run")
        harness.sessionRepo.preAddSession(id = "s2", gameId = "1", name = "Second Run")
        harness.sessionRepo.preAddSession(id = "s3", gameId = "1", name = "Third Run")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        // There should be exactly one "Current" badge in the tree
        onAllNodes(hasTestTag("session_current_badge"), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    // ── Single session shows "Current" badge ──

    @Test
    fun singleSessionShowsCurrentBadge() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Solo Run")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNode(hasTestTag("session_current_badge"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Current").assertIsDisplayed()
    }

    // ── Single-player session does NOT show multiplayer badge ──

    @Test
    fun singlePlayerSessionHidesMultiplayerBadge() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Solo Run",
            memberCount = 1,
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNode(hasTestTag("session_multiplayer_badge_s1"), useUnmergedTree = true).assertDoesNotExist()
        onNode(hasTestTag("session_member_avatars_s1"), useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Member count overflow shows "+N" ──

    @Test
    fun memberAvatarsShowOverflowCount() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Big Group",
            memberCount = 7,
            memberAvatars = listOf(
                "https://example.com/a1.png",
                "https://example.com/a2.png",
                "https://example.com/a3.png",
                "https://example.com/a4.png",
                "https://example.com/a5.png",
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNode(hasTestTag("session_member_avatars_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag("session_avatar_overflow"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("+3").assertIsDisplayed()
    }
}
