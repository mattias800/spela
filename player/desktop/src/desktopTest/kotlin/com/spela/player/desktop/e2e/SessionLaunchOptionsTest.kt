package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.PendingLaunch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Desktop E2E tests for the session row launch options dropdown.
 *
 * When a session has been played (lastPlayedAt != null), a dropdown chevron
 * appears next to the play button. Clicking it reveals "Continue from Title
 * Screen" which launches the session with skipAutoLoad=true.
 *
 * Covers:
 * - Dropdown chevron appears on played sessions (lastPlayedAt set)
 * - Dropdown chevron does NOT appear on unplayed sessions
 * - Clicking the dropdown shows "Continue from Title Screen"
 * - Primary play button still works (continues with save state)
 * - "Continue from Title Screen" dispatches correct intent (skipAutoLoad=true, sessionId set)
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SessionLaunchOptionsTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.scrollToSessions() {
        onNodeWithTag("game_detail_content")
            .performScrollToNode(hasTestTag("sessions_section"))
    }

    // ── Dropdown chevron appears on played sessions ──

    @Test
    fun dropdownChevronAppearsOnPlayedSession() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "My Run",
            lastPlayedAt = "2024-01-15T10:30:00Z",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()

        // The "More play options" chevron should be visible
        onNodeWithContentDescription("More play options").assertIsDisplayed()
    }

    // ── Dropdown chevron does NOT appear on unplayed sessions ──

    @Test
    fun dropdownChevronHiddenOnUnplayedSession() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Fresh Run",
            // lastPlayedAt is null (default) — session has never been played
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()

        // The "More play options" chevron should NOT exist
        onNodeWithContentDescription("More play options").assertDoesNotExist()
    }

    // ── Mixed: only played session shows chevron ──

    @Test
    fun onlyPlayedSessionShowsChevron() = runComposeUiTest {
        val harness = createHarness()
        // Session that has been played
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Played Run",
            lastPlayedAt = "2024-01-10T08:00:00Z",
        )
        // Session that has NOT been played
        harness.sessionRepo.preAddSession(
            id = "s2",
            gameId = "1",
            name = "Unplayed Run",
            // lastPlayedAt = null
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()
        onNodeWithTag("session_item_s1").assertIsDisplayed()
        onNodeWithTag("session_item_s2").assertIsDisplayed()

        // Exactly one chevron should exist (for the played session)
        onAllNodesWithContentDescription("More play options").assertCountEquals(1)
    }

    // ── Clicking the dropdown chevron shows "Continue from Title Screen" ──

    @Test
    fun dropdownShowsContinueFromTitleScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "My Run",
            lastPlayedAt = "2024-01-15T10:30:00Z",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()

        // Click the dropdown chevron
        onNodeWithContentDescription("More play options").performClick()
        advanceQuick(harness)

        // "Continue from Title Screen" menu item should appear
        onNodeWithText("Continue from Title Screen").assertIsDisplayed()
    }

    // ── Primary play button continues with save state (default behavior) ──

    @Test
    fun primaryPlayButtonContinuesWithSaveState() = runComposeUiTest {
        val harness = createHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "My Run",
            lastPlayedAt = "2024-01-15T10:30:00Z",
        )

        var capturedLaunch: PendingLaunch? = null
        harness.scope.launch {
            harness.emulationViewModel.launchReady.collect { capturedLaunch = it }
        }

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()

        // Click the primary play button (not the chevron)
        onNodeWithContentDescription("Continue session").performClick()
        advance(harness)

        // Should dispatch PrepareLaunch with skipAutoLoad=false and the correct sessionId
        val launch = capturedLaunch
        assertTrue(launch != null, "Expected a PendingLaunch to be emitted")
        assertEquals("1", launch.gameId)
        assertEquals("s1", launch.sessionId)
        assertFalse(launch.skipAutoLoad, "skipAutoLoad should be false for normal continue")
    }

    // ── "Continue from Title Screen" dispatches skipAutoLoad=true with sessionId ──

    @Test
    fun continueFromTitleScreenDispatchesCorrectIntent() = runComposeUiTest {
        val harness = createHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "My Run",
            lastPlayedAt = "2024-01-15T10:30:00Z",
        )

        var capturedLaunch: PendingLaunch? = null
        harness.scope.launch {
            harness.emulationViewModel.launchReady.collect { capturedLaunch = it }
        }

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        scrollToSessions()

        // Open the dropdown and click "Continue from Title Screen"
        onNodeWithContentDescription("More play options").performClick()
        advanceQuick(harness)
        onNodeWithText("Continue from Title Screen").performClick()
        advance(harness)

        // Should dispatch PrepareLaunch with skipAutoLoad=true and the correct sessionId
        val launch = capturedLaunch
        assertTrue(launch != null, "Expected a PendingLaunch to be emitted")
        assertEquals("1", launch.gameId)
        assertEquals("s1", launch.sessionId)
        assertTrue(launch.skipAutoLoad, "skipAutoLoad should be true for title screen continue")
        assertFalse(launch.forceNewSession, "forceNewSession should be false")
    }
}
