package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import com.spela.player.presentation.ui.TestTags
import org.junit.Test

class PlayLaterTest : BaseE2ETest() {

    /**
     * Open the More-actions overflow menu on the game detail screen.
     * The Play-Later toggle lives inside this menu (see
     * GameActionsMenu in commonMain) — there's no top-level
     * Play Later button on the page itself.
     */
    private fun openActionsMenu() {
        rule.tapOnTag(TestTags.GAME_DETAIL_MORE_ACTIONS)
        rule.waitForTag(TestTags.GAME_DETAIL_MENU_PLAY_LATER, timeout = 5_000)
    }

    /**
     * True if the game is currently in the Play Later queue. The menu
     * item's TEXT toggles between "Play Later" and "Remove from Play
     * Later"; the testTag is the same on both, so we read the text
     * inside the tagged node.
     */
    private fun isInPlayLaterFromMenu(): Boolean {
        return try {
            rule.onAllNodesWithText("Remove from Play Later", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) { false }
    }

    /** Open the menu, return whether the game is currently in Play Later. Closes the menu. */
    private fun queryPlayLaterState(): Boolean {
        openActionsMenu()
        val inQueue = isInPlayLaterFromMenu()
        // Close menu by tapping outside (backdrop area).
        rule.tapAtPercent(50f, 5f)
        Thread.sleep(300)
        return inQueue
    }

    /** Toggle the Play Later state via the menu, idempotent — desired-state is `desiredInQueue`. */
    private fun setPlayLater(desiredInQueue: Boolean) {
        openActionsMenu()
        val currentlyInQueue = isInPlayLaterFromMenu()
        if (currentlyInQueue == desiredInQueue) {
            // Already in the right state, just close the menu.
            rule.tapAtPercent(50f, 5f)
            Thread.sleep(300)
            return
        }
        if (desiredInQueue) {
            rule.tapOn("Play Later")
        } else {
            rule.tapOn("Remove from Play Later")
        }
        Thread.sleep(500)
    }

    @Test
    fun addToPlayLaterFromGameDetail() {
        // Local seed has Castlevania; CI ships nestest. The Play
        // Later toggle is game-agnostic, so navigate to whichever
        // NES game is available.
        rule.navigateToAnyNesGame()
        setPlayLater(desiredInQueue = false)
        // Now flip it to "in queue".
        setPlayLater(desiredInQueue = true)
        // Verify state changed.
        check(queryPlayLaterState()) { "Expected game to be in Play Later after toggling on" }
    }

}
