package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E tests for attempting challenges (US-5).
 *
 * When a player taps "Attempt Challenge", the game launches with the challenge's
 * save state loaded. A timer starts. The overlay is modified:
 * - "Complete" + "Restart" + "Give Up" + "Controls" (no Save/Load/FF)
 * - Timer pauses when overlay is open (Decision #6)
 * - "Resume" button to dismiss overlay
 *
 * Two-step API: POST /attempts/start → play → POST /attempts/:id/complete
 * Server-side timing — client timer is display-only.
 *
 * Prerequisites:
 * - Server running with seeded data (player/player123 user, Castlevania game)
 * - Castlevania game available and downloadable
 */
@RunWith(AndroidJUnit4::class)
class ChallengeAttemptTest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Create a challenge, navigate to its detail screen, and start an attempt.
     * Returns with the game running in challenge mode.
     */
    private fun createAndStartAttempt(title: String = "Attempt Test Challenge") {
        // Create a challenge from gameplay
        rule.ensureChallengeExists(title)

        // Navigate to challenge detail via challenge list
        rule.navigateToChallengeList()
        rule.waitForText(title, timeout = 8_000)
        rule.tapOn(title)
        rule.waitForText("Attempt Challenge", timeout = 5_000)

        // Start attempt
        rule.tapOn("Attempt Challenge")

        // Wait for game to load with challenge save state
        rule.waitForVisible("Touch controls", timeout = 15_000)
    }

    // ── US-5 AC: Tapping "Attempt" loads game with challenge save state ──

    @Test
    fun attemptChallengeLoadsGame() {
        createAndStartAttempt("Attempt Load Test")

        // Game should be running (touch controls visible)
        rule.assertVisible("Touch controls")

        // Timer should be visible during gameplay
        rule.waitForVisible("Challenge timer", timeout = 5_000)

        // Clean up: abandon the attempt
        rule.abandonChallenge()
    }

    // ── US-5 AC: Timer displayed during gameplay ──

    @Test
    fun challengeTimerVisibleDuringPlay() {
        createAndStartAttempt("Timer Visibility Test")

        // Timer HUD should be visible while playing
        rule.waitForVisible("Challenge timer", timeout = 5_000)

        rule.abandonChallenge()
    }

    // ── US-5 AC: Modified overlay — no Save/Load/FF ──

    @Test
    fun challengeOverlayHasModifiedControls() {
        createAndStartAttempt("Overlay Controls Test")
        rule.openChallengeOverlay()

        // Challenge-mode overlay should show these buttons (per Decision #5):
        rule.assertVisible("Complete")
        rule.assertVisible("Restart")
        rule.assertTextVisible("Give Up")
        rule.assertVisible("Controls")

        // Normal overlay controls should NOT be present:
        rule.assertNotVisible("Save")
        rule.assertNotVisible("Load")
        rule.assertNotVisible("Fast")

        // Game title visible in overlay
        rule.assertVisible("Castlevania")

        // "Resume" button to dismiss overlay (challenge mode uses "Resume" not "Continue")
        rule.assertVisible("Resume")

        rule.resumeChallengeFromOverlay()
        rule.abandonChallenge()
    }

    // ── US-5 AC: Timer pauses when overlay is open (Decision #6) ──

    @Test
    fun timerPausesWhenOverlayOpen() {
        createAndStartAttempt("Timer Pause Test")

        // Let some time elapse
        Thread.sleep(2_000)

        rule.openChallengeOverlay()

        // Overlay should show challenge controls (timer visible in overlay too)
        rule.assertVisible("Complete")

        // Close overlay with "Resume" and verify timer is still running
        rule.resumeChallengeFromOverlay()
        rule.waitForVisible("Challenge timer", timeout = 3_000)

        rule.abandonChallenge()
    }

    // ── US-5 AC: "Give Up" abandons attempt (goes through confirmation dialog) ──

    @Test
    fun giveUpAbandonsAttempt() {
        createAndStartAttempt("Give Up Test")
        rule.openChallengeOverlay()

        // Tap "Give Up" — triggers confirmation dialog
        rule.tapOn("Give Up")
        rule.waitForText("Give Up Challenge?", timeout = 5_000)

        // Confirmation dialog should show explanation and buttons
        rule.assertTextVisible("Your current attempt will be abandoned")
        rule.assertTextVisible("Keep Playing")

        // Confirm give up
        val giveUpNodes = rule.onAllNodesWithText("Give Up").fetchSemanticsNodes()
        rule.onAllNodesWithText("Give Up")[giveUpNodes.size - 1].performClick()
        rule.waitForIdle()
    }

    // ── US-5 AC: "Complete" submits attempt and shows result ──

    @Test
    fun completeSubmitsAttempt() {
        createAndStartAttempt("Complete Test")

        // Let some time elapse for a non-zero duration
        Thread.sleep(2_000)

        rule.completeChallenge()

        // Result screen should appear
        rule.waitForText("Challenge Complete", timeout = 8_000)

        // Should show the completion time label
        rule.assertVisible("Your time")

        // Dismiss result screen
        rule.tapOn("Done")
        rule.waitForIdle()
    }

    // ── US-5 AC: "Restart" reloads challenge save state, resets timer ──

    @Test
    fun restartReloadsOriginalState() {
        createAndStartAttempt("Restart Test")

        // Let some time pass
        Thread.sleep(2_000)

        rule.openChallengeOverlay()
        rule.tapOn("Restart")

        // Game should reload — touch controls visible again
        rule.waitForVisible("Touch controls", timeout = 15_000)

        // Timer should have reset (new attempt started server-side)
        rule.waitForVisible("Challenge timer", timeout = 5_000)

        // Verify overlay still has challenge controls after restart
        rule.openChallengeOverlay()
        rule.assertVisible("Complete")
        rule.assertVisible("Restart")
        rule.assertTextVisible("Give Up")
        rule.assertNotVisible("Save")

        rule.resumeChallengeFromOverlay()
        rule.abandonChallenge()
    }

    // ── Back button during challenge attempt opens challenge overlay ──

    @Test
    fun backButtonOpensOverlayDuringAttempt() {
        createAndStartAttempt("Back Button Test")

        // Back button should open the challenge overlay
        rule.pressBack()
        rule.waitForText("Give Up", timeout = 5_000)

        // Verify it's the challenge overlay, not normal overlay
        rule.assertVisible("Complete")
        rule.assertNotVisible("Save")

        // Dismiss overlay with "Resume"
        rule.resumeChallengeFromOverlay()

        // Game still running
        rule.assertVisible("Touch controls")

        rule.abandonChallenge()
    }

    // ── Give Up confirmation "Keep Playing" cancels the abandon ──

    @Test
    fun giveUpKeepPlayingCancelsAbandon() {
        createAndStartAttempt("Keep Playing Test")
        rule.openChallengeOverlay()

        // Tap "Give Up" to open confirmation dialog
        rule.tapOn("Give Up")
        rule.waitForText("Give Up Challenge?", timeout = 5_000)

        // Tap "Keep Playing" to dismiss dialog
        rule.tapOn("Keep Playing")
        rule.waitForTextNotVisible("Give Up Challenge?")

        // Should still be on the challenge overlay
        rule.assertVisible("Complete")
        rule.assertVisible("Restart")

        rule.resumeChallengeFromOverlay()
        rule.abandonChallenge()
    }
}
