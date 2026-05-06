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
@RequiresPhysicalDevice(reason = "Drives the UI add-server + login flow — SpTextField → AndroidView'd EditText doesn't surface reliably on the GHA AVD's small viewport. Covered by desktop tests.")
class EstablishSessionTest : BaseE2ETest() {

    @Before
    override fun baseSetUp() {
        // The class-level @RequiresPhysicalDevice gate from
        // BaseE2ETest.baseSetUp() is bypassed because we override
        // without super() — re-check it inline.
        val annotation = this::class.java.getAnnotation(RequiresPhysicalDevice::class.java)
        if (annotation != null) {
            org.junit.Assume.assumeFalse(
                "Skipping on emulator (@RequiresPhysicalDevice): ${annotation.reason}",
                isEmulator,
            )
        }

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
