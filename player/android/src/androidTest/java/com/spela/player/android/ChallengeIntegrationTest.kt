package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Test

/**
 * E2E tests for cross-feature challenge integration.
 *
 * Verifies that challenges integrate correctly with:
 * - Activity feed (challenge_completed events)
 * - App restart persistence
 * - Normal overlay regression (existing features still work)
 *
 * Also serves as regression checks for existing features that challenges touch.
 */
class ChallengeIntegrationTest : BaseE2ETest() {

    // ── Activity feed: challenge_completed event ──

    @Test
    fun completedChallengeAppearsInActivityFeed() {
        // Create challenge
        rule.navigateToGameAndPlay(preferredGameTitle = "Castlevania")
        rule.createChallengeFromOverlay("Activity Feed Test")
        rule.openOverlayAndExit()
        // After exit the action button is Play/Resume/Download
        // depending on cache + auto-save state — any of them means
        // we're back on game detail.
        rule.pollUntil(timeoutMillis = 8_000L) {
            try {
                rule.onAllNodesWithText("Resume", substring = false)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Play", substring = false)
                        .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Download", substring = false)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: Exception) { false }
        }

        // Navigate to challenge and attempt it
        rule.navigateToChallengeList()
        rule.waitForText("Activity Feed Test", timeout = 8_000)
        rule.tapOn("Activity Feed Test")
        rule.waitForText("Attempt Challenge", timeout = 5_000)

        // Start and complete attempt — testTag click bypasses the
        // touch-routing weirdness on multi-display hardware.
        rule.tapOnTag("attempt_challenge_button")
        rule.waitForGameRunning(timeout = 15_000)
        Thread.sleep(1_000)
        rule.completeChallenge()
        rule.waitForText("Challenge Complete", timeout = 8_000)
        rule.tapOn("Done")
        rule.waitForIdle()

        // Navigate back to home
        rule.pressBack() // challenge detail → challenge list
        rule.pressBack() // challenge list → game detail
        rule.pressBack() // game detail → console list
        rule.pressBack() // console list → home

        rule.waitForText("Spela", timeout = 8_000)

        // Navigate to Activity tab
        rule.tapOn("Activity")
        rule.waitForText("Activity", timeout = 5_000)

        // Activity feed should show challenge completion event
        // Per spec: "player completed Activity Feed Test in {time}"
        rule.scrollToAndTapText("completed")
        rule.assertVisible("Activity Feed Test")

        rule.pressBack()
    }

    // ── Challenges persist across app restart ──

    @Test
    fun challengesPersistAcrossRestart() {
        // Create a challenge
        rule.ensureChallengeExists("Persist Test")

        // Verify challenges section exists on game detail
        rule.scrollToAndTapText("View Challenges")
        rule.waitForText("Persist Test", timeout = 8_000)

        rule.pressBack() // back to game detail
        rule.pressBack() // back to console list
        rule.pressBack() // back to home

        // Restart app
        rule.restartApp()
        rule.waitForText("Spela", timeout = 15_000)

        // Navigate back to game detail
        rule.navigateToCastlevania()

        // Challenges should still be visible (fetched from server)
        rule.navigateToChallengeList()
        rule.waitForText("Persist Test", timeout = 8_000)

        rule.pressBack()
    }

    // ── Normal overlay still works (regression) ──

    @Test
    fun normalOverlayUnaffectedByChallenge() {
        rule.navigateToGameAndPlay(preferredGameTitle = "Castlevania")

        // Normal overlay should still have all standard controls
        rule.openOverlay()
        rule.assertVisible("Save")
        rule.assertVisible("Load")
        rule.assertVisible("Screenshot")
        rule.assertVisible("Fast")
        rule.assertVisible("Controls")
        rule.assertVisible("Challenge")
        rule.assertVisible("Exit Game")
        rule.assertVisible("Continue")

        rule.exitGame()
    }

    // ── Game detail "Challenges" section coexists with existing sections ──

    @Test
    fun gameDetailLayoutIntactWithChallengesSection() {
        rule.navigateToCastlevania()

        // The page should have the primary action plus the always-on
        // sections. game_shared_sessions_section only renders when
        // shared sessions exist (or a load is in flight); a fresh
        // test seed has neither, so we don't assert on it here.
        // Drive everything by tag so the test stays stable across
        // copy changes.
        rule.assertVisible("Download")
        rule.scrollToAndTapTag("sessions_section")
        rule.scrollToAndTapTag("challenges_section")
        rule.assertTagVisible("view_challenges_button")

        rule.pressBack()
    }
}
