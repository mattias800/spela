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

        // Verify Home via any of the several indicators the screen
        // may show depending on whether the user has play history.
        rule.pollUntil(timeoutMillis = 8_000L) {
            rule.onAllNodesWithText("Spela")
                .fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Your library is empty", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Top Rated", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Continue Playing", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
