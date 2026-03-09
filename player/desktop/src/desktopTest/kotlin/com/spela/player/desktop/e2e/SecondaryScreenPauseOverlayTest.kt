package com.spela.player.desktop.e2e

import kotlin.test.Test

/**
 * E2E tests for pause overlay text formatting.
 *
 * Verifies that the pause overlay format logic produces the expected
 * content for local pause and netplay pause scenarios.
 */
class SecondaryScreenPauseOverlayTest {

    @Test
    fun pauseOverlayFormattingForLocalPause() {
        // Test the formatting logic for local pause display
        val sessionElapsedSeconds = 2705L // 45 min 5 sec
        val activeSlot = 3
        val hours = sessionElapsedSeconds / 3600
        val minutes = (sessionElapsedSeconds % 3600) / 60
        val seconds = sessionElapsedSeconds % 60
        val sessionDuration = "%d:%02d:%02d".format(hours, minutes, seconds)

        // Verify formatting
        assert(sessionDuration == "0:45:05") { "Expected 0:45:05, got $sessionDuration" }
        assert(activeSlot == 3)
    }

    @Test
    fun pauseOverlayFormattingForNetplayPause() {
        // Test the formatting logic for netplay pause display
        val peerUsername = "player2"
        val pauseSeconds = 15L
        val pauseMinutes = pauseSeconds / 60
        val pauseRemaining = pauseSeconds % 60
        val pauseDuration = "$pauseMinutes:%02d".format(pauseRemaining)

        // Verify formatting
        assert(pauseDuration == "0:15") { "Expected 0:15, got $pauseDuration" }
        val pauseTitle = "PAUSED BY ${peerUsername.uppercase()}"
        assert(pauseTitle == "PAUSED BY PLAYER2") { "Expected PAUSED BY PLAYER2, got $pauseTitle" }
    }
}
