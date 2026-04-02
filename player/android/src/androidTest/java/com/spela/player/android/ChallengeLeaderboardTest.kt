package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E tests for challenge leaderboards (US-6).
 *
 * Leaderboard shows ranked results: fastest time first, one entry per user (best attempt).
 * Top 3 get gold/silver/bronze badges. Current user's rank is highlighted.
 * Time displayed as M:SS.s in monospace.
 *
 * Prerequisites:
 * - Server running with seeded data (player/player123 user, Castlevania game)
 */
@RunWith(AndroidJUnit4::class)
class ChallengeLeaderboardTest {

    

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    // ── US-6 AC: Leaderboard visible on challenge detail ──

    @Test
    fun leaderboardSectionVisibleOnDetail() {
        // Create a challenge to view
        rule.ensureChallengeExists("Leaderboard Section Test")

        // Navigate to challenge detail
        rule.navigateToChallengeList()
        rule.waitForText("Leaderboard Section Test", timeout = 8_000)
        rule.tapOn("Leaderboard Section Test")
        rule.waitForText("Leaderboard", timeout = 5_000)

        // Leaderboard section should be present
        rule.scrollToAndTapText("Leaderboard")

        rule.pressBack()
    }

    // ── US-6 AC: Empty leaderboard shows "No attempts yet" ──

    @Test
    fun emptyLeaderboardShowsPrompt() {
        // Create a fresh challenge (no attempts)
        rule.ensureChallengeExists("Empty Leaderboard Test")

        rule.navigateToChallengeList()
        rule.waitForText("Empty Leaderboard Test", timeout = 8_000)
        rule.tapOn("Empty Leaderboard Test")
        rule.waitForText("Leaderboard", timeout = 5_000)

        rule.scrollToAndTapText("Leaderboard")

        // US-6 AC: empty state from SpEmptyStates.NoAttempts()
        rule.assertTextVisible("No attempts yet")
        rule.assertVisible("Be the first")

        rule.pressBack()
    }

    // ── US-6 AC: Leaderboard shows entry after completing an attempt ──

    @Test
    fun leaderboardShowsEntryAfterCompletion() {
        // Create challenge
        rule.startLoggedIn()
        rule.navigateToGameAndPlay()
        rule.createChallengeFromOverlay("Leaderboard Entry Test")
        rule.openOverlayAndExit()
        rule.waitForText("About", timeout = 8_000)

        // Navigate to challenge detail and start attempt
        rule.navigateToChallengeList()
        rule.waitForText("Leaderboard Entry Test", timeout = 8_000)
        rule.tapOn("Leaderboard Entry Test")
        rule.waitForText("Attempt Challenge", timeout = 5_000)

        rule.tapOn("Attempt Challenge")
        rule.waitForVisible("Game running", timeout = 15_000)

        // Let some time pass, then complete
        Thread.sleep(1_500)
        rule.completeChallenge()
        rule.waitForText("Challenge Complete", timeout = 8_000)

        // Dismiss result — goes back to previous screen
        rule.tapOn("Done")
        rule.waitForIdle()

        // Navigate back to the challenge detail to check leaderboard
        // (Done exits the game, we should be back on the challenge detail or need to re-navigate)
        rule.waitForText("Leaderboard", timeout = 8_000)

        // Scroll to leaderboard section
        rule.scrollToAndTapText("Leaderboard")

        // Current user (player) should now appear in leaderboard
        // LeaderboardRow contentDescription: "Rank 1: {username}, {duration}"
        rule.assertVisible("player")
        rule.assertVisible("Rank 1")

        rule.pressBack()
    }

    // ── US-6 AC: Top 3 get gold/silver/bronze badges ──
    // Note: Testing visual badge rendering (gold/silver/bronze colors) is not feasible
    // via Compose UI tests (which test semantics, not visual styling). This is a visual
    // QA concern. We verify the rank numbers are correct instead.
    // Full visual validation deferred to screenshot comparison tests or manual QA.
}
