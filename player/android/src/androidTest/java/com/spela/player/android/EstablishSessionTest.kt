package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Before
import org.junit.Test

/**
 * Tests the user's first-install experience: add server, log in,
 * land on Home. Overrides the base class's "you start logged in"
 * contract because the whole point of this test is the pre-login
 * UX.
 */
class EstablishSessionTest : BaseE2ETest() {

    @Before
    override fun baseSetUp() {
        // The whole purpose of this test is the UI add-server +
        // login flow. On the GH Actions x86_64 AVD that flow is
        // unreliable: SpTextField wraps real Android EditTexts via
        // AndroidView and the small viewport pushes inputs below
        // the fold; Compose UI Test taps fail to inject. The same
        // user flow is covered exhaustively by desktop E2E tests
        // (per CLAUDE.md "Player App Testing Strategy" — UI lives
        // in commonMain so it belongs on the desktop suite). Skip
        // here; the emulator suite focuses on integration paths the
        // desktop can't cover (real network, real lifecycle).
        org.junit.Assume.assumeFalse(
            "EstablishSession exercises the UI add-server + login flow; " +
                "the GH Actions AVD's small viewport + AndroidView'd " +
                "EditTexts can't reliably drive that flow. Covered by " +
                "desktop tests.",
            isEmulator,
        )

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
