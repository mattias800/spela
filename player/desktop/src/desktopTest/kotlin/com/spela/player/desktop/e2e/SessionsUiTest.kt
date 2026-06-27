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
        // SessionsSection no longer renders a session count next to the
        // title — verify the session item itself instead.
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNodeWithText("My First Run").assertIsDisplayed()
        onNode(hasTestTag("session_current_badge"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Current").assertIsDisplayed()
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
        onNodeWithText("No sessions yet. Press Play to start your first playthrough.")
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

        // Dialog should appear with its input field. "New Session" text
        // appears on both the trigger button and the dialog title so we
        // key on the input tag for the dialog-open assertion.
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

    // ── Rename session works (via `…` overflow menu) ──

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

        // Open the session's `…` overflow menu, then click Rename.
        onNodeWithTag("session_actions_menu_s1").performClick()
        advanceQuick(harness)
        onNodeWithTag("session_action_rename_s1").performClick()
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

    // ── Delete session works (via `…` overflow menu) ──

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

        // Open the session's `…` overflow menu, then click Delete.
        onNodeWithTag("session_actions_menu_s1").performClick()
        advanceQuick(harness)
        onNodeWithTag("session_action_delete_s1").performClick()
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
        // No session-count pill next to the title — assert item presence.
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNodeWithTag("session_item_s2").assertIsDisplayed()

        // Scroll to see the third session
        onNodeWithTag("game_detail_content")
            .performScrollToNode(hasTestTag("session_item_s3"))
        onNodeWithTag("session_item_s3").assertIsDisplayed()

        onNodeWithText("Casual Run").assertIsDisplayed()
        onNodeWithText("100% Completion").assertIsDisplayed()
        onNodeWithText("No Damage Run").assertIsDisplayed()
        onAllNodes(hasTestTag("session_current_badge"), useUnmergedTree = true)
            .assertCountEquals(1)
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
        // The Castlevania session item exists and the Mario session (for
        // a different game) does not — the count pill next to the title
        // is no longer rendered.
        onNodeWithText("Castlevania Run").assertIsDisplayed()
        onNodeWithText("Mario Run").assertDoesNotExist()
    }

    // ── Multiplayer session shows member avatars ──

    @Test
    fun multiplayerSessionShowsMemberAvatarsAndLastPlayedBy() = runComposeUiTest {
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
            lastPlayedByUsername = "player2",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNode(hasTestTag("session_member_avatars_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag("session_multiplayer_badge_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Multiplayer").assertIsDisplayed()
        onNodeWithText("by player2", substring = true).assertIsDisplayed()
    }

    // ── Shared session shows Shared Session badge and member names ──

    @Test
    fun sharedSessionShowsBadgeAndMemberNames() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Shared Session",
            memberCount = 3,
            memberUsernames = listOf("alice", "bob", "charlie"),
            isSharedSession = true,
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNode(hasTestTag("session_multiplayer_badge_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Shared Session").assertIsDisplayed()
        onNode(hasTestTag("session_member_names_s1"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("alice, bob, charlie").assertIsDisplayed()
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

    // ── Clone session: #553 US-2 (own session, from list overflow) ──

    @Test
    fun cloneSessionCreatesACopyViaOverflowMenu() = runComposeUiTest {
        val harness = createHarness()
        // Pre-populate a session with a pinned core + play time so we can
        // verify those are inherited by the clone.
        val source = com.spela.player.domain.model.GameSession(
            id = "s1",
            gameId = "1",
            name = "My Playthrough",
            totalPlayTime = 36_000L, // 10h
            pinnedCoreSha256 = "cafebabe1234",
        )
        harness.sessionRepo.sessions.add(source)

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()

        // Open the session's `…` overflow menu and pick "Clone session".
        // (No primary "Clone" button exists on the list per #553 PO brief.)
        onNodeWithTag("session_actions_menu_s1").performClick()
        advanceQuick(harness)
        onNodeWithTag("session_action_clone_s1").performClick()
        advanceQuick(harness)

        // The clone confirmation dialog pre-fills "{source} (Copy)" and is
        // editable. Accept the default here (US-2).
        onNodeWithTag("clone_session_dialog").assertIsDisplayed()
        onNodeWithTag("clone_session_input").assertIsDisplayed()
        onNodeWithTag("clone_session_confirm").performClick()
        advance(harness)

        // Fake repo records the cloneSession call — NOT the deprecated
        // duplicate path.
        assertEquals(1, harness.sessionRepo.cloneInvocations.size)
        val call = harness.sessionRepo.cloneInvocations.first()
        assertEquals("s1", call.sessionId)
        assertEquals("My Playthrough (Copy)", call.name)
        assertEquals(null, call.saveId)

        // A new session now exists with the inherited pin + play time.
        assertEquals(2, harness.sessionRepo.sessions.size)
        val clone = harness.sessionRepo.sessions[1]
        assertEquals("My Playthrough (Copy)", clone.name)
        assertEquals(36_000L, clone.totalPlayTime)
        assertEquals("cafebabe1234", clone.pinnedCoreSha256)
        onNodeWithText("My Playthrough (Copy)").assertIsDisplayed()
    }

    // ── Clone session: #553 US-3 (from a specific save, on detail screen) ──

    @Test
    fun cloneFromSpecificSaveCreatesNewSession() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Main",
        )
        // Add two saves — older (id=1) and newer (id=2). The older save
        // carries distinct bytes so the fake can assert which one seeded
        // the clone.
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
        // Navigate to the session detail page.
        harness.navigationViewModel.onIntent(
            com.spela.player.presentation.navigation.NavigationIntent.NavigateTo(
                com.spela.player.presentation.navigation.SpScreen.SessionDetail("s1"),
            ),
        )
        advance(harness)

        // Both saves render. Open the OLDER save's per-row `…` menu.
        onNodeWithTag("session_save_item_1").assertIsDisplayed()
        onNodeWithTag("session_save_menu_1").performClick()
        advanceQuick(harness)
        onNodeWithTag("session_save_clone_item_1").performClick()
        advanceQuick(harness)

        // Clone dialog — accept the default name.
        onNodeWithTag("session_detail_clone_from_save_dialog").assertIsDisplayed()
        onNodeWithTag("session_detail_clone_from_save_confirm").performClick()
        advance(harness)

        // Verify the clone was seeded from save id=1 (bytes 0x11,0x22,0x33)
        // — NOT the most-recent save. The Long conversion is explicit in
        // SessionDetailScreen.kt (SaveState.id is a String on the domain).
        assertEquals(1, harness.sessionRepo.cloneInvocations.size)
        val call = harness.sessionRepo.cloneInvocations.first()
        assertEquals("s1", call.sessionId)
        assertEquals(1L, call.saveId)
        assertEquals(byteArrayOf(0x11, 0x22, 0x33).toList(), call.seededSaveBytes?.toList())
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
