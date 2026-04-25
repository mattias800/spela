package com.spela.player.android

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
        rule.waitForText("Download", timeout = 8_000)

        // Navigate to challenge and attempt it
        rule.navigateToChallengeList()
        rule.waitForText("Activity Feed Test", timeout = 8_000)
        rule.tapOn("Activity Feed Test")
        rule.waitForText("Attempt Challenge", timeout = 5_000)

        // Start and complete attempt
        rule.tapOn("Attempt Challenge")
        rule.waitForVisible("Game running", timeout = 15_000)
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

        // The page should have the primary action and the standard
        // sections — verify a few section headers scroll into view.
        // The 'Save States' section was renamed to 'Sessions' and
        // a sibling 'Community Saves' was added; the challenges
        // section is the new addition under test.
        rule.assertVisible("Download")
        rule.scrollToAndTapText("Sessions")
        rule.scrollToAndTapText("Community Saves")

        // Challenges section should exist alongside others.
        rule.scrollToAndTapText("Challenges")
        rule.assertVisible("View Challenges")

        rule.pressBack()
    }
}
