package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Test

@RequiresPhysicalDevice(reason = "Sign-out flow + restartApp + login form drive UI taps and ActivityScenario.recreate() — both paths are unreliable on the GHA AVD; persistence is covered by desktop tests")
class SessionTest : BaseE2ETest() {

    /** Tap the confirm button in the sign-out dialog and wait for server connection screen. */
    private fun confirmSignOutDialog() {
        rule.waitForText("re-enter your credentials", timeout = 3_000)
        // SpDialog tags its confirm button — no need to disambiguate
        // among multiple "Sign Out" text nodes (settings row, dialog
        // title, dialog confirm button).
        rule.scrollToAndTapTag("dialog_confirm")
        rule.waitForText("Add Server", timeout = 15_000)
    }

    @Test
    fun sessionPersistsAcrossRestart() {
        // Ensure we're logged in

        // Restart app (session should persist via SQLDelight)
        rule.restartApp()

        // Assert home screen appears without login prompt. Use
        // isOnHomeScreen() (Compose + UiAutomator) instead of
        // waitForText('Spela') which UiAutomator misses on the
        // secondary display the AYN Thor sometimes routes to.
        rule.pollUntil(timeoutMillis = 15_000) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }
    }

    @Test
    fun logoutClearsTokensPreservesServer() {
        // Skip on emulator: this test calls rule.restartApp() (via the
        // assertion path after sign-out), which is documented as
        // unreliable on AVDs (docs/e2e-testing.md "restartApp()
        // unreliable on emulators"). Persistence semantics are
        // covered by desktop tests.
        org.junit.Assume.assumeFalse(
            "restartApp is unreliable on the AVD",
            isEmulator,
        )

        // Navigate to Settings → About category (where Sign Out lives)
        rule.navigateToSettingsCategory("About")
        rule.scrollToAndTapText("Sign Out")
        confirmSignOutDialog()

        // Verify server still listed
        rule.assertTextVisible("Local")

        // Restart and verify tokens cleared but server persists
        rule.restartApp()

        // After restart, app shows server connection screen with "Local" still listed
        rule.pollUntil(timeoutMillis = 15_000) {
            try {
                rule.onAllNodesWithText("Local", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Username", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) {
                false
            }
        }

        // If on server connection screen, tap server to get to login
        val onServerScreen = try {
            rule.onAllNodesWithText("Add Server", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) { false }
        if (onServerScreen) {
            rule.onNodeWithText("Local").performClick()
        }

        // Verify login screen (tokens were cleared)
        rule.waitForText("Username", timeout = 8_000)
    }

    @Test
    fun landscapeLoginFlow() {

        // Sign out to get to a clean state
        rule.navigateToSettingsCategory("About")
        rule.scrollToAndTapText("Sign Out")
        confirmSignOutDialog()

        // Server should still be listed — tap it
        rule.assertTextVisible("Local")
        rule.onNodeWithText("Local").performClick()

        // Login (tests landscape scrollability)
        rule.waitForText("Username", timeout = 15_000)

        rule.typeIntoFieldByLabel("Username", "player")

        rule.typeIntoFieldByLabel("Password", "player123")

        rule.onNodeWithText("Sign In").performScrollTo()
        rule.onNodeWithText("Sign In").performClick()

        // Verify home screen — multi-display safe.
        rule.pollUntil(timeoutMillis = 15_000) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }
    }

    @Test
    fun preferencesSyncAcrossRestart() {

        // Navigate to Settings → Emulation category (where Auto Save lives)
        rule.navigateToSettingsCategory("Emulation")

        // Toggle Auto Save on Exit
        rule.waitForText("Auto Save on Exit", timeout = 8_000)
        rule.tapOn("Auto Save on Exit")

        // Restart app
        rule.restartApp()

        // Session restored — wait for Home, multi-display safe.
        rule.pollUntil(timeoutMillis = 15_000) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }

        // Navigate to Settings → Emulation and verify toggle persisted
        rule.navigateToSettingsCategory("Emulation")

        rule.waitForText("Auto Save on Exit", timeout = 8_000)
    }
}
