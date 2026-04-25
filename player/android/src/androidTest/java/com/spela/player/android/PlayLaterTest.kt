package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Test

class PlayLaterTest : BaseE2ETest() {

    /**
     * Open the More-actions overflow menu on the game detail screen.
     * The Play-Later toggle lives inside this menu (see
     * GameActionsMenu in commonMain) — there's no top-level
     * Play Later button on the page itself.
     */
    private fun openActionsMenu() {
        rule.tapOn("More actions")
        // Menu is a DropdownMenu — wait for an item to render.
        rule.pollUntil(timeoutMillis = 5_000) {
            try {
                rule.onAllNodesWithText("Favorite", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Play Later", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) { false }
        }
    }

    /** True if game is currently in the Play Later queue (menu shows "Remove from Play Later"). */
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
        rule.navigateToCastlevania()
        setPlayLater(desiredInQueue = false)
        // Now flip it to "in queue".
        setPlayLater(desiredInQueue = true)
        // Verify state changed.
        check(queryPlayLaterState()) { "Expected game to be in Play Later after toggling on" }
    }

    @Test
    fun removeFromPlayLaterFromGameDetail() {
        rule.navigateToCastlevania()
        setPlayLater(desiredInQueue = true)
        setPlayLater(desiredInQueue = false)
        check(!queryPlayLaterState()) { "Expected game NOT in Play Later after toggling off" }
    }

    @Test
    fun playLaterSectionOnHomeScreen() {
        // Add a game to Play Later first.
        rule.navigateToCastlevania()
        setPlayLater(desiredInQueue = true)

        // Navigate back to Home and trigger a dashboard refresh by
        // bouncing off another tab (avoids restartApp() which is
        // documented as unreliable on emulators / this device).
        rule.navigateBackToHome()
        rule.waitForText("Spela", timeout = 8_000)
        rule.tapOn("Consoles")
        rule.waitForContentDescription("Nintendo Entertainment System", timeout = 8_000)
        rule.tapOn("Home")
        rule.waitForText("Spela", timeout = 8_000)

        // Find the "Play Later" section header in the LazyColumn.
        try {
            rule.scrollToAndTapText("Play Later")
        } catch (e: IllegalStateException) {
            throw AssertionError(
                "Expected 'Play Later' section on Home after adding a game", e,
            )
        }
    }

    @Test
    fun activityFeedShowsPlayLaterEvent() {
        rule.navigateToCastlevania()
        setPlayLater(desiredInQueue = false)
        setPlayLater(desiredInQueue = true) // generates queued_play_later event

        // Navigate to Home (don't use restartApp — known unreliable).
        rule.navigateBackToHome()
        rule.waitForText("Spela", timeout = 8_000)
        // Bounce tabs to force a dashboard refresh.
        rule.tapOn("Consoles")
        rule.waitForContentDescription("Nintendo Entertainment System", timeout = 8_000)
        rule.tapOn("Home")
        rule.waitForText("Spela", timeout = 8_000)

        // Activity event text contains "Play Later queue".
        try {
            rule.scrollToAndTapText("Play Later queue")
        } catch (_: IllegalStateException) {
            throw AssertionError(
                "Expected an activity event mentioning 'Play Later queue' on Home",
            )
        }
    }

    @Test
    fun playLaterTogglePersistsOnGameDetail() {
        rule.navigateToCastlevania()
        setPlayLater(desiredInQueue = true)

        // Go all the way back to Home, then re-navigate to Castlevania.
        // This forces the game-detail screen to re-fetch from the server
        // rather than relying on cached UI state.
        rule.navigateBackToHome()
        rule.waitForText("Spela", timeout = 8_000)
        rule.navigateToCastlevania()

        // The state should still report "in Play Later".
        check(queryPlayLaterState()) {
            "Expected Play Later state to persist across navigation"
        }
    }
}
