package com.spela.player.android

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
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

        // Start and complete attempt — fire the SpButton's onClick
        // directly via SemanticsActions.OnClick. Bypasses the
        // multi-display touch routing on Thor and Espresso's idle
        // wait, both of which break a Compose performClick on the
        // inner Text child of an SpButton.
        rule.onAllNodes(
            androidx.compose.ui.test.hasText("Attempt Challenge", substring = true) and
                androidx.compose.ui.test.hasClickAction()
        )[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        Thread.sleep(500)
        rule.waitForGameRunning(timeout = 15_000)
        Thread.sleep(1_000)
        rule.completeChallenge()
        rule.waitForText("Challenge Complete", timeout = 8_000)
        rule.tapOn("Done")
        rule.waitForIdle()

        // Navigate back to home — use the helper which knows to
        // press back through any deep screen and stop at the Home
        // tab root rather than counting back-presses by hand.
        rule.navigateBackToHome()
        rule.pollUntil(timeoutMillis = 8_000L) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }

        // Navigate to Activity tab
        rule.tapOn("Activity")

        // Activity feed should show challenge completion event.
        // Format from formatActivityAction(): "completed a challenge for $gameName"
        // (game name is "Castlevania", so the full feed line is
        // "player completed a challenge for Castlevania"). The challenge name
        // is shown separately via the metadata, but the feed item's primary
        // text always contains "completed". Use that as the wait target —
        // unlike the bare "Activity" string it's only present after the feed
        // has actually loaded, so it doubles as a feed-loaded synchronisation
        // point.
        rule.scrollToAndTapText("completed")
        rule.assertVisible("Castlevania")

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
        // Wait for the app to land on Home (or whatever logged-in
        // surface the restored session takes us to). "Spela" alone
        // can collide with login-screen branding and isn't reliably
        // visible to UiAutomator on Thor's secondary display.
        rule.pollUntil(timeoutMillis = 15_000) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }

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
