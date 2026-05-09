package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Before
import org.junit.Test

/**
 * Tests the user's first-install experience: add server, log in,
 * land on Home. Overrides the base class's "you start logged in"
 * contract because the whole point of this test is the pre-login
 * UX.
 *
 * Skipped on the GH Actions AVD: the entire test exercises the UI
 * add-server + login flow, and that flow is unreliable on the small
 * x86_64 viewport — SpTextField wraps real Android EditTexts via
 * AndroidView and Compose UI Test taps don't always reach the click
 * handler (#1146 root cause). The user flow has full coverage in
 * desktop E2E tests where the harness drives Compose state directly
 * (no real EditText), per CLAUDE.md's "Player App Testing Strategy".
 *
 * Re-enable when run-e2e adopts a larger AVD profile that keeps the
 * inputs above the fold, or when SpTextField goes back to a pure
 * Compose TextField on Android (#1113 follow-up).
 */
@RequiresPhysicalDevice(
    reason = "Drives the UI add-server + login flow; unreliable on the GHA x86_64 AVD's " +
        "small viewport with AndroidView'd EditTexts. Covered by desktop E2E tests."
)
class EstablishSessionTest : BaseE2ETest() {

    @Before
    override fun baseSetUp() {
        // Still reset the backend — user-generated data from prior
        // tests must not influence the login flow.
        resetServerState()

        // Make sure we're logged in first (ensureLoggedIn handles
        // arbitrary entry state), then explicitly sign out so the
        // test actually exercises the server-connect screen. This
        // mirrors loginAsPlayer()/loginAsAdmin() in TestHelpers.kt
        // which already rely on signOutIfLoggedIn.
        rule.ensureLoggedIn()
        rule.signOutIfLoggedIn()

        // Skip assertOnHome — we're deliberately NOT on Home here.
    }

    @Test
    fun establishSession() {
        // App is on the server-connect screen. Drive the full flow:
        // add server → log in → land on Home.
        rule.addServerAndLogin(PLAYER_USERNAME, PLAYER_PASSWORD)

        // Verify Home via the canonical helper, which checks both the
        // SCREEN_HOME testTag, the "Spela" brand mark text, and (newly)
        // the "Spela" contentDescription that the AVD surfaces.
        rule.pollUntil(timeoutMillis = 8_000L) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }
    }
}
