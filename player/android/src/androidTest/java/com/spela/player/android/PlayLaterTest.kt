package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayLaterTest {

    

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Wait for either play later button state to be visible on game detail. */
    private fun waitForPlayLaterButton() {
        rule.waitUntil(timeoutMillis = 8_000) {
            try {
                rule.onAllNodesWithContentDescription("Add to Play Later", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithContentDescription("Remove from Play Later", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) {
                false
            }
        }
    }

    /** Ensure game is in play later queue. */
    private fun ensureInPlayLater() {
        waitForPlayLaterButton()
        val alreadyInQueue = rule.onAllNodesWithContentDescription("Remove from Play Later", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (!alreadyInQueue) {
            rule.tapOn("Add to Play Later")
            rule.waitForContentDescription("Remove from Play Later", timeout = 5_000)
        }
    }

    /** Ensure game is not in play later queue. */
    private fun ensureNotInPlayLater() {
        waitForPlayLaterButton()
        val inQueue = rule.onAllNodesWithContentDescription("Remove from Play Later", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (inQueue) {
            rule.tapOn("Remove from Play Later")
            rule.waitForContentDescription("Add to Play Later", timeout = 5_000)
        }
    }

    @Test
    fun addToPlayLaterFromGameDetail() {
        rule.startLoggedIn()
        rule.navigateToCastlevania()
        ensureNotInPlayLater()

        rule.tapOn("Add to Play Later")

        rule.waitForContentDescription("Remove from Play Later", timeout = 5_000)
        rule.assertContentDescriptionVisible("Remove from Play Later")
    }

    @Test
    fun removeFromPlayLaterFromGameDetail() {
        rule.startLoggedIn()
        rule.navigateToCastlevania()
        ensureInPlayLater()

        rule.tapOn("Remove from Play Later")

        rule.waitForContentDescription("Add to Play Later", timeout = 5_000)
        rule.assertContentDescriptionVisible("Add to Play Later")
    }

    @Test
    fun playLaterSectionOnHomeScreen() {
        rule.startLoggedIn()

        // Ensure a game is in the Play Later queue
        rule.navigateToCastlevania()
        ensureInPlayLater()

        // Navigate back to home
        rule.pressBack()
        rule.pressBack()
        rule.waitForText("Spela", timeout = 8_000)

        // Restart app to force a fresh dashboard load
        rule.restartApp()
        rule.waitForText("Spela", timeout = 15_000)

        // Use scrollToAndTapText to find "Play Later" in the LazyColumn
        // This scrolls through the LazyColumn searching for the text
        try {
            rule.scrollToAndTapText("Play Later")
            // If we got here, the section header was found
        } catch (e: IllegalStateException) {
            throw AssertionError("Expected 'Play Later' section on home screen but it was not found after scrolling", e)
        }
    }

    @Test
    fun activityFeedShowsPlayLaterEvent() {
        rule.startLoggedIn()
        rule.navigateToCastlevania()
        ensureNotInPlayLater()

        // Add to play later to generate a queued_play_later activity event
        rule.tapOn("Add to Play Later")
        rule.waitForContentDescription("Remove from Play Later", timeout = 5_000)

        // Navigate back to home
        rule.pressBack()
        rule.pressBack()
        rule.waitForText("Spela", timeout = 8_000)

        // Restart app to force fresh data load including activity feed
        rule.restartApp()
        rule.waitForText("Spela", timeout = 15_000)

        // The activity event is rendered in the LazyColumn and may be off-screen.
        // First scroll to find "Recent Activity" section, then verify the event text.
        // The event text is "player added Castlevania to Play Later queue" (rendered as
        // annotated string). Search for a substring that identifies the event.
        try {
            rule.scrollToAndTapText("Play Later queue")
        } catch (_: IllegalStateException) {
            throw AssertionError(
                "Expected activity event with 'Play Later queue' text on home screen " +
                    "but it was not found after scrolling"
            )
        }
    }

    @Test
    fun playLaterTogglePersistsOnGameDetail() {
        rule.startLoggedIn()
        rule.navigateToCastlevania()
        ensureNotInPlayLater()

        // Add to play later
        rule.tapOn("Add to Play Later")
        rule.waitForContentDescription("Remove from Play Later", timeout = 5_000)

        // Navigate back to game list
        rule.pressBack()
        rule.waitForText("Castlevania", timeout = 8_000)

        // Navigate to a different game first, then back, to force a fresh load
        rule.scrollToAndTapText("Section Z")
        rule.waitForText("Download", timeout = 5_000)
        rule.pressBack()
        rule.waitForText("Castlevania", timeout = 8_000)

        // Re-navigate to Castlevania
        rule.scrollToAndTapText("Castlevania")
        rule.waitForText("Download", timeout = 5_000)

        // Wait for the play later button to appear (either Add or Remove)
        waitForPlayLaterButton()

        // The game detail initially loads from the cached game list which may have
        // stale isInPlayLater state. Give extra time for the server response to arrive.
        rule.waitForContentDescription("Remove from Play Later", timeout = 15_000)
        rule.assertContentDescriptionVisible("Remove from Play Later")
    }
}
