package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Cheat
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /** Navigate to session detail and wait for cheat data to fully load (nested async chain). */
    private fun ComposeUiTest.navigateToSessionDetailWithCheats(harness: SpelaTestHarness, sessionId: String) {
        navigateToSessionDetail(harness, sessionId)
        // loadAvailableCheats is a nested coroutine launched from loadSession's onSuccess
        advanceFully(harness)
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
            saveId = "1",
            name = "Boss Fight Save",
        )
        harness.sessionRepo.preAddSessionSave(
            sessionId = "s1",
            saveId = "2",
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
        harness.addSampleCheats()

        setContent { harness.App() }
        navigateToSessionDetail(harness, "s1")

        // Scroll to cheats section
        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_section"))

        onNodeWithTag("session_cheats_toggle").assertIsDisplayed()

        // Toggle cheats on
        onNodeWithTag("session_cheats_toggle").performClick()
        advanceFully(harness)

        // Verify the cheat config was updated
        val config = harness.sessionRepo.getLastCheatConfig("s1")
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
        harness.addSampleCheats()

        setContent { harness.App() }
        navigateToSessionDetailWithCheats(harness, "s1")

        // Scroll to cheats section
        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_section"))

        onNodeWithTag("session_cheats_count").assertIsDisplayed()
        onNodeWithText("3 cheat(s) active").assertIsDisplayed()
    }

    // ── Per-cheat selection: individual cheat rows appear when cheats enabled ──

    private fun SpelaTestHarness.addSampleCheats(gameId: String = "1"): List<Cheat> {
        val cheats = listOf(
            Cheat(id = "c1", gameId = gameId, index = 0, description = "Infinite Lives", code = "7E0073:09", enabled = false),
            Cheat(id = "c2", gameId = gameId, index = 1, description = "Max Health", code = "7E0DBE:63", enabled = false),
            Cheat(id = "c3", gameId = gameId, index = 2, description = "Moon Jump", code = "7E0D86:FF", enabled = false),
        )
        cheatRepo.cheats.addAll(cheats)
        return cheats
    }

    @Test
    fun individualCheatRowsAppearWhenCheatsEnabled() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Cheat Run")
        harness.sessionRepo.preSetSessionCheats("s1", cheatsEnabled = true)
        harness.addSampleCheats()

        setContent { harness.App() }
        navigateToSessionDetailWithCheats(harness, "s1")

        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_list"))

        onNodeWithTag("session_cheat_item_0").assertIsDisplayed()
        onNodeWithTag("session_cheat_item_1").assertIsDisplayed()
        onNodeWithTag("session_cheat_item_2").assertIsDisplayed()
    }

    // ── Cheat description and code are displayed ──

    @Test
    fun cheatDescriptionAndCodeDisplayed() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Cheat Run")
        harness.sessionRepo.preSetSessionCheats("s1", cheatsEnabled = true)
        harness.addSampleCheats()

        setContent { harness.App() }
        navigateToSessionDetailWithCheats(harness, "s1")

        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_list"))

        onNodeWithContentDescription("Cheat: Infinite Lives").assertIsDisplayed()
        onNodeWithText("7E0073:09").assertIsDisplayed()
        onNodeWithContentDescription("Cheat: Max Health").assertIsDisplayed()
        onNodeWithText("7E0DBE:63").assertIsDisplayed()
    }

    // ── Toggling individual cheat works ──

    @Test
    fun toggleIndividualCheat() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Cheat Run")
        harness.sessionRepo.preSetSessionCheats("s1", cheatsEnabled = true)
        harness.addSampleCheats()

        setContent { harness.App() }
        navigateToSessionDetailWithCheats(harness, "s1")

        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_list"))

        // Toggle cheat at index 0 on
        onNodeWithTag("session_cheat_toggle_0").performClick()
        advanceFully(harness)

        // Verify the state was updated in the repo
        val config = harness.sessionRepo.getLastCheatConfig("s1")
        assertTrue(0 in (config?.enabledIndices ?: emptyList()), "Cheat index 0 should be enabled")

        // Toggle it off
        onNodeWithTag("session_cheat_toggle_0").performClick()
        advanceFully(harness)

        val config2 = harness.sessionRepo.getLastCheatConfig("s1")
        assertFalse(0 in (config2?.enabledIndices ?: emptyList()), "Cheat index 0 should be disabled")
    }

    // ── Select All cheats ──

    @Test
    fun selectAllCheatsWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Cheat Run")
        harness.sessionRepo.preSetSessionCheats("s1", cheatsEnabled = true)
        harness.addSampleCheats()

        setContent { harness.App() }
        navigateToSessionDetailWithCheats(harness, "s1")

        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_select_all"))

        onNodeWithTag("session_cheats_select_all").performClick()
        advanceFully(harness)

        val config = harness.sessionRepo.getLastCheatConfig("s1")
        assertEquals(listOf(0, 1, 2), config?.enabledIndices?.sorted(), "All cheats should be enabled")
    }

    // ── Deselect All cheats ──

    @Test
    fun deselectAllCheatsWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Cheat Run")
        harness.sessionRepo.preSetSessionCheats("s1", cheatsEnabled = true, enabledIndices = listOf(0, 1, 2))
        harness.addSampleCheats()

        setContent { harness.App() }
        navigateToSessionDetailWithCheats(harness, "s1")

        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_deselect_all"))

        onNodeWithTag("session_cheats_deselect_all").performClick()
        advanceFully(harness)

        val config = harness.sessionRepo.getLastCheatConfig("s1")
        assertTrue(config?.enabledIndices?.isEmpty() == true, "All cheats should be deselected")
    }

    // ── Master toggle off hides individual cheat list ──

    @Test
    fun masterToggleOffHidesCheatList() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Cheat Run")
        harness.sessionRepo.preSetSessionCheats("s1", cheatsEnabled = false)
        harness.addSampleCheats()

        setContent { harness.App() }
        navigateToSessionDetailWithCheats(harness, "s1")

        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_section"))

        // Master toggle is off — cheat list should not exist
        onNodeWithTag("session_cheats_list").assertDoesNotExist()
        onNodeWithTag("session_cheat_item_0").assertDoesNotExist()
    }

    // ── Search filter works for large cheat lists ──

    @Test
    fun searchFilterWorksCheats() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Cheat Run")
        harness.sessionRepo.preSetSessionCheats("s1", cheatsEnabled = true)

        // Need >10 cheats for search field to appear
        val cheats = (0..11).map { i ->
            Cheat(id = "c$i", gameId = "1", index = i, description = "Cheat $i", code = "CODE$i", enabled = false)
        }
        harness.cheatRepo.cheats.addAll(cheats)

        setContent { harness.App() }
        navigateToSessionDetailWithCheats(harness, "s1")

        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_search"))

        onNodeWithTag("session_cheats_search").assertIsDisplayed()

        // Type a search query that matches only "Cheat 11"
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("session_cheats_search")))
            .performTextInput("Cheat 11")
        advanceQuick(harness)

        // Scroll to the filtered cheat item (header may push it below viewport)
        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheat_item_11"))

        // Only cheat 11 should be visible, cheat 0 should be filtered out
        onNodeWithTag("session_cheat_item_11").assertIsDisplayed()
        onNodeWithTag("session_cheat_item_0").assertDoesNotExist()
    }

    // ── No cheats available (unverified game) shows unavailable message ──

    @Test
    fun noCheatsAvailableShowsUnavailableMessage() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(id = "s1", gameId = "1", name = "Cheat Run")
        harness.sessionRepo.preSetSessionCheats("s1", cheatsEnabled = false)
        // No cheats added to cheatRepo — simulates unverified game

        setContent { harness.App() }
        navigateToSessionDetailWithCheats(harness, "s1")

        onNodeWithTag("session_detail_content")
            .performScrollToNode(hasTestTag("session_cheats_unavailable"))

        onNodeWithTag("session_cheats_unavailable").assertIsDisplayed()
        onNodeWithText("Cheats are not available for this game. Cheats require a verified ROM (checksum match).").assertIsDisplayed()
    }
}
