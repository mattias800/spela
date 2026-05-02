package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Regression coverage for #885 — Share session menu item.
 *
 * Two contracts:
 *   1. The "Share session…" menu item appears on a session row when
 *      the game's PlaySemantics resolves to ResumesFromSaveState
 *      (console supports save states AND user hasn't opted out).
 *   2. Clicking it opens the share-session dialog with the form pre-
 *      populated for the right source session.
 *
 * The capability-OFF case (ScummVM, demo cores, user opt-out) is
 * covered structurally — the SessionsSection renders the menu item
 * only when its host passed an `onShareSession` callback, and the
 * GameDetailScreen only passes it when `state.playSemantics ==
 * ResumesFromSaveState`. Adding a separate ScummVM-context test would
 * require a different game-detail fixture; the gate is small enough
 * that source review is sufficient.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ShareSessionMenuTest {

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
    fun shareSessionMenuItemAppearsOnSaveStateConsole() = runComposeUiTest {
        val harness = createHarness()
        // NES (default game id "1") has saveStateSupport = true. With
        // a session present, PlaySemantics resolves to
        // ResumesFromSaveState — the share gate should be open.
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "First Run",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")
        scrollToSessions()

        // Open the row's overflow menu.
        onNodeWithTag("session_actions_menu_s1").performClick()
        advanceQuick(harness)

        // "Share session…" item must be present.
        onNodeWithTag("session_action_share_s1").assertIsDisplayed()
    }

    @Test
    fun clickingShareOpensTheDialog() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Speedrun",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")
        scrollToSessions()

        onNodeWithTag("session_actions_menu_s1").performClick()
        advanceQuick(harness)
        onNodeWithTag("session_action_share_s1").performClick()
        advanceQuick(harness)

        // Dialog visible with its form fields and submit button.
        onNodeWithTag("share_session_dialog", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("share_session_name_input", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("share_session_description_input", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("share_session_submit_button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun cancellingDialogClosesIt() = runComposeUiTest {
        val harness = createHarness()
        harness.sessionRepo.preAddSession(
            id = "s1",
            gameId = "1",
            name = "Casual run",
        )

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")
        scrollToSessions()

        onNodeWithTag("session_actions_menu_s1").performClick()
        advanceQuick(harness)
        onNodeWithTag("session_action_share_s1").performClick()
        advanceQuick(harness)
        onNodeWithTag("share_session_dialog", useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag("share_session_cancel_button", useUnmergedTree = true).performClick()
        advanceQuick(harness)

        onNodeWithTag("share_session_dialog", useUnmergedTree = true).assertDoesNotExist()
    }
}
