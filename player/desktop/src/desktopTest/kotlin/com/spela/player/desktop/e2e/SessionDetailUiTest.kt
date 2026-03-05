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
 * Desktop E2E tests for the Session Detail screen.
 *
 * Covers:
 * - Session detail renders with session info
 * - Save states display correctly
 * - Cheat toggle works
 * - Delete session from detail screen works
 * - Rename session works
 * - Empty saves state shows correctly
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SessionDetailUiTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToSessionDetail(harness: SpelaTestHarness, sessionId: String) {
        advance(harness) // settle initial composition
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SessionDetail(sessionId))
        )
        advanceFully(harness)
    }

    // ── Session detail renders with session info ──

    @Test
    fun sessionDetailRendersSessionInfo() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "My First Run",
            totalPlayTime = 3600,
        )

        setContent { harness.App() }
        navigateToSessionDetail(harness, "s1")

        onNodeWithTag("session_detail_header").assertIsDisplayed()
        onNodeWithContentDescription("Session: My First Run").assertIsDisplayed()
    }

    // ── Save states display correctly ──

    @Test
    fun saveStatesDisplayCorrectly() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "My Run",
        )
        harness.sessionRepo.preAddSessionSave(
            sessionId = "s1",
            saveId = 1,
            name = "Boss Fight Save",
        )
        harness.sessionRepo.preAddSessionSave(
            sessionId = "s1",
            saveId = 2,
            name = "After Tutorial",
        )

        setContent { harness.App() }
        navigateToSessionDetail(harness, "s1")

        onNodeWithTag("session_save_item_1").assertIsDisplayed()
        onNodeWithText("Boss Fight Save").assertIsDisplayed()
        onNodeWithTag("session_save_item_2").assertIsDisplayed()
        onNodeWithText("After Tutorial").assertIsDisplayed()
    }

    // ── Empty saves state shows correctly ──

    @Test
    fun emptySavesStateShows() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Empty Run",
        )

        setContent { harness.App() }
        navigateToSessionDetail(harness, "s1")

        onNodeWithTag("session_saves_empty").assertIsDisplayed()
        onNodeWithText("No saves yet").assertIsDisplayed()
    }

    // ── Cheat toggle works ──

    @Test
    fun cheatToggleWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Cheat Run",
        )

        setContent { harness.App() }
        navigateToSessionDetail(harness, "s1")

        // Scroll to cheats section
        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_section"))

        onNodeWithTag("session_cheats_toggle").assertIsDisplayed()

        // Toggle cheats on
        onNodeWithTag("session_cheats_toggle").performClick()
        advance(harness)

        // Verify the session repo was updated
        val config = harness.sessionRepo.sessions.find { it.id == "s1" }
        assertTrue(config?.cheatsEnabled == true, "Expected cheats to be enabled")
    }

    // ── Delete session from detail screen works ──

    @Test
    fun deleteSessionFromDetailScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Doomed Run",
        )

        setContent { harness.App() }
        navigateToSessionDetail(harness, "s1")

        // Scroll to delete button
        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_delete_button"))

        onNodeWithTag("session_delete_button").performClick()
        advanceQuick(harness)

        // Delete confirmation dialog should appear
        onNodeWithTag("session_delete_dialog").assertIsDisplayed()
        onNodeWithText("Delete \"Doomed Run\"? All saves in this session will be permanently removed.")
            .assertIsDisplayed()

        // Confirm deletion
        onAllNodesWithText("Delete").filterToOne(hasClickAction()).performClick()
        advance(harness)

        // Session should be deleted
        assertTrue(harness.sessionRepo.sessions.isEmpty())
    }

    // ── Rename session works from detail screen ──

    @Test
    fun renameSessionFromDetailScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Old Name",
        )

        setContent { harness.App() }
        navigateToSessionDetail(harness, "s1")

        // Click rename button
        onNodeWithTag("session_detail_rename_button").performClick()
        advanceQuick(harness)

        // Rename dialog should appear
        onNodeWithText("Rename Session").assertIsDisplayed()
        onNodeWithTag("session_detail_rename_input").assertIsDisplayed()

        // Clear and type new name
        onNodeWithTag("session_detail_rename_input").performTextClearance()
        onNodeWithTag("session_detail_rename_input").performTextInput("New Name")
        advanceQuick(harness)

        // Confirm rename
        onNodeWithText("Rename").performClick()
        advance(harness)

        // Session should be renamed
        assertEquals("New Name", harness.sessionRepo.sessions[0].name)
    }

    // ── Cheats show count when enabled ──

    @Test
    fun cheatsShowCountWhenEnabled() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Cheat Run",
        )
        harness.sessionRepo.preSetSessionCheats("s1", cheatsEnabled = true, enabledIndices = listOf(0, 2, 5))

        setContent { harness.App() }
        navigateToSessionDetail(harness, "s1")

        // Scroll to cheats section
        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_section"))

        onNodeWithTag("session_cheats_count").assertIsDisplayed()
        onNodeWithText("3 cheat(s) active").assertIsDisplayed()
    }
}
