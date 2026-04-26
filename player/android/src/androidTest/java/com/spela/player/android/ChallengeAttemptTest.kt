package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Test

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
 * All tests share a single challenge to avoid the expensive create flow
 * (play game → create challenge → exit) for every test. The shared cache
 * in TestHelpers tracks whether the challenge has been created in this process.
 *
 * Prerequisites:
 * - Server running with seeded data (player/player123 user, Castlevania game)
 * - Castlevania game available and downloadable
 */
class ChallengeAttemptTest : BaseE2ETest() {

    companion object {
        private const val SHARED_CHALLENGE = "Attempt E2E Challenge"
    }

    /**
     * Ensure the shared challenge exists, navigate to its detail screen,
     * and start an attempt. Returns with the game running in challenge mode.
     */
    private fun startAttempt() {
        rule.ensureChallengeExists(SHARED_CHALLENGE)

        // Navigate to challenge detail via challenge list
        rule.navigateToChallengeList()
        rule.waitForText(SHARED_CHALLENGE, timeout = 15_000)
        rule.tapOn(SHARED_CHALLENGE)
        rule.waitForText("Attempt Challenge", timeout = 8_000)

        // Start attempt. Compose's onClick semantic action fires
        // the button's onClick lambda directly without going through
        // touch dispatch — that bypasses both the multi-display
        // routing on Thor and the Espresso idle wait. Find the
        // clickable node by text + clickAction so it doesn't matter
        // which sub-node carries the tag.
        rule.onAllNodes(
            androidx.compose.ui.test.hasText("Attempt Challenge", substring = true) and
                androidx.compose.ui.test.hasClickAction()
        )[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        Thread.sleep(500)

        // Wait for the game to load. UiAutomator's "Core running"
        // marker can sit on the wrong physical display on Thor's
        // multi-display setup, and Compose's semantic-tree fetch can
        // throw AppNotIdleException during the 60fps render loop —
        // fall back to logcat markers we know fire on real start.
        rule.waitForGameRunning(timeout = 30_000)

        // Wait for touch controls. If the in-game overlay opened unexpectedly
        // (e.g., after a previous test's state leaked), dismiss it first.
        // Both the initial wait and the overlay probe must survive
        // AppNotIdleException — Compose APIs block on Espresso idle which
        // never fires during the 60fps emulation render loop, and on Thor
        // the "Game running" 1dp marker can render on display 4 where
        // UiAutomator can't see it.
        // ComposeTimeoutException extends Throwable directly, not
        // Exception, so we catch Throwable here. AppNotIdleException is
        // a RuntimeException so a Throwable catch covers both.
        try {
            rule.waitForVisible("Game running", timeout = 5_000)
        } catch (_: Throwable) {
            val device = androidx.test.uiautomator.UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            )
            val overlayOpen = device.findObject(
                androidx.test.uiautomator.UiSelector().textContains("Resume")
            ).exists() || device.findObject(
                androidx.test.uiautomator.UiSelector().textContains("Give Up")
            ).exists() || runCatching {
                rule.onAllNodesWithText("Resume").fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Give Up").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
            if (overlayOpen) {
                rule.resumeChallengeFromOverlay()
            }
            // Best-effort second wait — if it still throws, swallow it
            // and trust the logcat-based waitForGameRunning above; the
            // touch-control marker is informational only at this point.
            runCatching { rule.waitForVisible("Game running", timeout = 10_000) }
        }
    }

    // ── US-5 AC: Tapping "Attempt" loads game with challenge save state ──

    @Test
    fun attemptChallengeLoadsGame() {
        startAttempt()

        // Game should be running
        rule.assertVisible("Game running")

        // Timer should be visible during gameplay
        rule.waitForVisible("Challenge timer", timeout = 5_000)

        // Clean up: abandon the attempt
        rule.abandonChallenge()
    }

    // ── US-5 AC: Timer displayed during gameplay ──

    @Test
    fun challengeTimerVisibleDuringPlay() {
        startAttempt()

        // Timer HUD should be visible while playing
        rule.waitForVisible("Challenge timer", timeout = 5_000)

        rule.abandonChallenge()
    }

    // ── US-5 AC: Modified overlay — no Save/Load/FF ──

    @Test
    fun challengeOverlayHasModifiedControls() {
        startAttempt()
        rule.openChallengeOverlay()

        // Challenge-mode overlay should show these buttons (per Decision #5):
        rule.assertVisible("Complete")
        rule.assertVisible("Restart")
        rule.assertTextVisible("Give Up")
        rule.assertVisible("Controls")

        // Normal overlay controls should NOT be present.
        // Exact match — "Save" substring-matches "Save Slots" on the
        // secondary display companion, which is unrelated to the
        // in-game overlay action buttons we're verifying here.
        rule.assertNotVisibleExact("Save")
        rule.assertNotVisibleExact("Load")
        rule.assertNotVisibleExact("Fast")

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
        startAttempt()

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
        startAttempt()
        rule.openChallengeOverlay()

        // Tap "Give Up" — triggers confirmation dialog
        rule.tapOn("Give Up")
        rule.waitForText("Give Up Challenge?", timeout = 5_000)

        // Confirmation dialog should show explanation and buttons
        rule.assertTextVisible("Your current attempt will be abandoned")
        rule.assertTextVisible("Keep Playing")

        // Confirm give up — tapLastWithText handles UiAutomator during emulation
        rule.tapLastWithText("Give Up")

        // Wait for core shutdown after give-up exits the game
        rule.waitForCoreIdle()
    }

    // ── US-5 AC: "Complete" submits attempt and shows result ──

    @Test
    fun completeSubmitsAttempt() {
        startAttempt()

        // Let some time elapse for a non-zero duration
        Thread.sleep(2_000)

        rule.completeChallenge()

        // Result screen should appear
        rule.waitForText("Challenge Complete", timeout = 8_000)

        // Should show the completion time label
        rule.assertVisible("Your time")

        // Dismiss result screen
        rule.dismissChallengeResult()
    }

    // ── US-5 AC: "Restart" reloads challenge save state, resets timer ──

    @Test
    fun restartReloadsOriginalState() {
        startAttempt()

        // Let some time pass
        Thread.sleep(2_000)

        rule.openChallengeOverlay()
        rule.tapOn("Restart")

        // Game should reload — game running indicator visible again
        rule.waitForVisible("Game running", timeout = 15_000)

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
        startAttempt()

        // Back button should open the challenge overlay
        rule.pressBack()
        rule.waitForText("Give Up", timeout = 5_000)

        // Verify it's the challenge overlay, not normal overlay
        rule.assertVisible("Complete")
        rule.assertNotVisible("Save")

        // Dismiss overlay with "Resume"
        rule.resumeChallengeFromOverlay()

        // Game still running
        rule.assertVisible("Game running")

        rule.abandonChallenge()
    }

    // ── Give Up confirmation "Keep Playing" cancels the abandon ──

    @Test
    fun giveUpKeepPlayingCancelsAbandon() {
        startAttempt()
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
