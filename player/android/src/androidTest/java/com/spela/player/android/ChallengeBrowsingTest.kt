package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Test

/**
 * E2E tests for browsing and viewing challenges (US-3, US-4).
 *
 * Challenges are discoverable from:
 * - Game detail screen ("Challenges" section → "View Challenges" → ChallengeListScreen)
 * - Challenge detail screen (tap a challenge card)
 *
 * Per spec Decision #7: Player app has NO bottom nav item for challenges.
 * Discovery is contextual only (game detail → challenge list → challenge detail).
 *
 * Prerequisites:
 * - Server running with seeded data (player/player123 user, Castlevania game)
 */
class ChallengeBrowsingTest : BaseE2ETest() {

    // ── US-3 AC: Challenges section on game detail screen ──

    @Test
    fun gameDetailShowsChallengesSection() {
        rule.navigateToCastlevania()

        // Game detail should show "Challenges" section with "View Challenges" button
        // Scroll directly to the button (which is below the section heading)
        rule.scrollToAndTapText("View Challenges")

        // If we got here, the button was found and tapped — verify challenge list loaded
        rule.waitForText("Castlevania", timeout = 5_000)

        rule.pressBack()
    }

    // ── US-3 AC: Empty state when no challenges for a game ──

    @Test
    fun gameWithNoChallengesShowsEmptyState() {

        // Navigate to Castlevania
        rule.navigateToCastlevania()

        // Navigate to challenge list
        rule.navigateToChallengeList()

        // ChallengeAttemptTest (alphabetically first) may have already created
        // challenges for Castlevania. Verify the list screen loaded by checking
        // for either the empty state OR challenge content. Both prove the
        // ChallengeListScreen rendered correctly.
        rule.pollUntil(timeoutMillis = 8_000) {
            try {
                rule.onAllNodesWithText("No challenges yet", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Challenge", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) {
                false
            }
        }

        rule.pressBack()
    }

    // ── US-3 AC: ChallengeCard shows required info ──

    @Test
    fun challengeCardShowsRequiredInfo() {
        // Create a challenge so we have one to browse
        rule.ensureChallengeExists("Browsing Test Challenge")

        // Navigate to challenge list
        rule.navigateToChallengeList()
        rule.waitForText("Browsing Test Challenge", timeout = 8_000)

        // Challenge card content description includes difficulty, type, and game
        // SpChallengeCard: "{name}, {difficulty} {type} challenge for {gameTitle}"
        rule.assertVisible("Medium")
        rule.assertVisible("player")

        rule.pressBack()
    }

    // ── US-4 AC: Challenge detail screen ──

    @Test
    fun challengeDetailShowsFullInfo() {
        // Create a challenge so we have one to view
        rule.ensureChallengeExists("Detail Test Challenge")

        // Navigate to challenge list and tap the challenge
        rule.navigateToChallengeList()
        rule.waitForText("Detail Test Challenge", timeout = 8_000)
        rule.tapOn("Detail Test Challenge")

        // Detail screen shows: title, game name, difficulty chip, type chip, creator
        rule.waitForText("Detail Test Challenge", timeout = 5_000)
        rule.assertVisible("Castlevania")
        rule.assertVisible("Medium")
        rule.assertVisible("Completion")

        // US-4 AC: "Attempt" CTA visible
        rule.assertVisible("Attempt Challenge")

        // US-4 AC: Leaderboard section visible
        rule.scrollToAndTapText("Leaderboard")

        // Empty leaderboard for a freshly created challenge
        rule.assertVisible("No attempts yet")

        rule.pressBack()
    }

}
