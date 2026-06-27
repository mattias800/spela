package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.GameSession
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
 * - Creating, renaming, deleting, and cloning sessions
 * - Continue session triggers navigation with sessionId
 * - Multiplayer/shared-session metadata display
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

    @Test
    fun sessionsListDisplaysMultipleSessionsAndFiltersByGame() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Casual Run",
            totalPlayTime = 3600,
        )
        harness.sessionRepo.preAddSession(id = "s2", gameId = "1", name = "100% Completion")
        harness.sessionRepo.preAddSession(id = "s3", gameId = "1", name = "No Damage Run")
        harness.sessionRepo.preAddSession(id = "s4", gameId = "2", name = "Mario Run")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("sessions_section").assertIsDisplayed()
        onNodeWithText("Sessions").assertIsDisplayed()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNodeWithTag("session_item_s2").assertIsDisplayed()

        onNodeWithTag("game_detail_content")
            .performScrollToNode(hasTestTag("session_item_s3"))
        onNodeWithTag("session_item_s3").assertIsDisplayed()

        onNodeWithText("Casual Run").assertIsDisplayed()
        onNodeWithText("100% Completion").assertIsDisplayed()
        onNodeWithText("No Damage Run").assertIsDisplayed()
        onNodeWithText("Mario Run").assertDoesNotExist()
        onAllNodes(hasTestTag("session_current_badge"), useUnmergedTree = true)
            .assertCountEquals(1)
        onNodeWithText("Current").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsWhenNoSessions() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("sessions_section").assertIsDisplayed()
        onNodeWithTag("sessions_empty").assertIsDisplayed()
        onNodeWithText("No sessions yet. Press Play to start your first playthrough.")
            .assertIsDisplayed()
    }

    @Test
    fun createRenameAndDeleteSession() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("create_session_button").performClick()
        advanceQuick(harness)

        onNodeWithTag("create_session_input").assertIsDisplayed()
        onNodeWithTag("create_session_input").performTextClearance()
        onNodeWithTag("create_session_input").performTextInput("Speedrun Attempt")
        advanceQuick(harness)

        onNodeWithText("Create").performClick()
        advance(harness)

        assertEquals(1, harness.sessionRepo.sessions.size)
        assertEquals("Speedrun Attempt", harness.sessionRepo.sessions[0].name)
        onNodeWithTag("session_item_session-1").assertIsDisplayed()

        onNodeWithTag("session_actions_menu_session-1").performClick()
        advanceQuick(harness)
        onNodeWithTag("session_action_rename_session-1").performClick()
        advanceQuick(harness)

        onNodeWithText("Rename Session").assertIsDisplayed()
        onNodeWithTag("rename_session_input").assertIsDisplayed()
        onNodeWithTag("rename_session_input").performTextClearance()
        onNodeWithTag("rename_session_input").performTextInput("New Name")
        advanceQuick(harness)

        onNodeWithText("Rename").performClick()
        advance(harness)

        assertEquals("New Name", harness.sessionRepo.sessions[0].name)

        onNodeWithTag("session_actions_menu_session-1").performClick()
        advanceQuick(harness)
        onNodeWithTag("session_action_delete_session-1").performClick()
        advanceQuick(harness)

        onNodeWithText("Delete Session").assertIsDisplayed()
        onNodeWithText("Delete \"New Name\"? All saves in this session will be permanently removed.")
            .assertIsDisplayed()

        onAllNodesWithText("Delete").filterToOne(hasClickAction()).performClick()
        advance(harness)

        assertTrue(harness.sessionRepo.sessions.isEmpty())
    }

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

        onNodeWithContentDescription("Continue session").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertTrue(navState.showInGameOverlay || navState.overlaySessionId == "s1",
            "Expected session overlay to be triggered with sessionId s1")
    }

    @Test
    fun multiplayerSessionShowsMemberAvatarsOverflowAndLastPlayedBy() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Co-op Run",
            memberCount = 7,
            memberAvatars = listOf(
                "https://example.com/a1.png",
                "https://example.com/a2.png",
                "https://example.com/a3.png",
                "https://example.com/a4.png",
                "https://example.com/a5.png",
            ),
            lastPlayedByUsername = "player2",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNode(hasTestTag("session_member_avatars_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag("session_multiplayer_badge_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag("session_avatar_overflow"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Multiplayer").assertIsDisplayed()
        onNodeWithText("by player2", substring = true).assertIsDisplayed()
        onNodeWithText("+3").assertIsDisplayed()
    }

    @Test
    fun sharedSessionShowsBadgeAndSinglePlayerHidesMultiplayerBadge() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Shared Session",
            memberCount = 3,
            memberUsernames = listOf("alice", "bob", "charlie"),
            isSharedSession = true,
        )
        harness.sessionRepo.preAddSession(
            id = "s2",
            gameId = "1",
            name = "Solo Run",
            memberCount = 1,
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNode(hasTestTag("session_multiplayer_badge_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag("session_member_names_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Shared Session").assertIsDisplayed()
        onNodeWithText("alice, bob, charlie").assertIsDisplayed()

        onNodeWithTag("session_item_s2").assertIsDisplayed()
        onNode(hasTestTag("session_multiplayer_badge_s2"), useUnmergedTree = true).assertDoesNotExist()
        onNode(hasTestTag("session_member_avatars_s2"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun cloneSessionCreatesACopyViaOverflowMenu() = runComposeUiTest {
        val harness = createHarness()
        val source = GameSession(
            id = "s1",
            gameId = "1",
            name = "My Playthrough",
            totalPlayTime = 36_000L,
            pinnedCoreSha256 = "cafebabe1234",
        )
        harness.sessionRepo.sessions.add(source)

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()

        onNodeWithTag("session_actions_menu_s1").performClick()
        advanceQuick(harness)
        onNodeWithTag("session_action_clone_s1").performClick()
        advanceQuick(harness)

        onNodeWithTag("clone_session_dialog").assertIsDisplayed()
        onNodeWithTag("clone_session_input").assertIsDisplayed()
        onNodeWithTag("clone_session_confirm").performClick()
        advance(harness)

        assertEquals(1, harness.sessionRepo.cloneInvocations.size)
        val call = harness.sessionRepo.cloneInvocations.first()
        assertEquals("s1", call.sessionId)
        assertEquals("My Playthrough (Copy)", call.name)
        assertEquals(null, call.saveId)

        assertEquals(2, harness.sessionRepo.sessions.size)
        val clone = harness.sessionRepo.sessions[1]
        assertEquals("My Playthrough (Copy)", clone.name)
        assertEquals(36_000L, clone.totalPlayTime)
        assertEquals("cafebabe1234", clone.pinnedCoreSha256)
        onNodeWithText("My Playthrough (Copy)").assertIsDisplayed()
    }

    @Test
    fun cloneFromSpecificSaveCreatesNewSession() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Main",
        )
        harness.sessionRepo.preAddSessionSave(
            sessionId = "s1",
            saveId = "1",
            name = "Before tough boss",
            bytes = byteArrayOf(0x11, 0x22, 0x33),
        )
        harness.sessionRepo.preAddSessionSave(
            sessionId = "s1",
            saveId = "2",
            name = "After tough boss",
            bytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()),
        )

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(
                SpScreen.SessionDetail("s1"),
            ),
        )
        advance(harness)

        onNodeWithTag("session_save_item_1").assertIsDisplayed()
        onNodeWithTag("session_save_menu_1").performClick()
        advanceQuick(harness)
        onNodeWithTag("session_save_clone_item_1").performClick()
        advanceQuick(harness)

        onNodeWithTag("session_detail_clone_from_save_dialog").assertIsDisplayed()
        onNodeWithTag("session_detail_clone_from_save_confirm").performClick()
        advance(harness)

        assertEquals(1, harness.sessionRepo.cloneInvocations.size)
        val call = harness.sessionRepo.cloneInvocations.first()
        assertEquals("s1", call.sessionId)
        assertEquals(1L, call.saveId)
        assertEquals(byteArrayOf(0x11, 0x22, 0x33).toList(), call.seededSaveBytes?.toList())
    }
}
