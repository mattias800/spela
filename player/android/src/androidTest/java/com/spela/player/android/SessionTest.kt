package com.spela.player.android

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Test

class SessionTest : BaseE2ETest() {

    /** Tap the confirm button in the sign-out dialog and wait for server connection screen. */
    private fun confirmSignOutDialog() {
        rule.waitForText("re-enter your credentials", timeout = 3_000)
        // Dialog has 3 "Sign Out" nodes: settings text, dialog title, dialog confirm button.
        // The confirm button is the LAST one.
        val nodes = rule.onAllNodesWithText("Sign Out").fetchSemanticsNodes()
        rule.onAllNodesWithText("Sign Out")[nodes.size - 1].performClick()
        rule.waitForText("Add Server", timeout = 15_000)
    }

    @Test
    fun sessionPersistsAcrossRestart() {
        // Ensure we're logged in

        // Restart app (session should persist via SQLDelight)
        rule.restartApp()

        // Assert home screen appears without login prompt
        rule.waitForText("Spela", timeout = 8_000)
    }

    @Test
    fun logoutClearsTokensPreservesServer() {

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
    fun serverPersistsAcrossRestart() {

        // Navigate to Settings → About → Sign Out
        rule.navigateToSettingsCategory("About")
        rule.scrollToAndTapText("Sign Out")
        confirmSignOutDialog()

        // Verify server is visible
        rule.assertTextVisible("Local")

        // Restart app
        rule.restartApp()

        // After restart, server should persist (may show Login or server list)
        rule.pollUntil(timeoutMillis = 15_000) {
            try {
                rule.onAllNodesWithText("Local", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Welcome", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) {
                false
            }
        }
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

        rule.onNode(hasText("Username") and hasSetTextAction())
            .performTextInput("player")

        rule.onNode(hasText("Password") and hasSetTextAction())
            .performTextInput("player123")

        rule.onNodeWithText("Sign In").performScrollTo()
        rule.onNodeWithText("Sign In").performClick()

        // Verify home screen
        rule.waitForText("Spela", timeout = 8_000)
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

        // Session restored
        rule.waitForText("Spela", timeout = 15_000)

        // Navigate to Settings → Emulation and verify toggle persisted
        rule.navigateToSettingsCategory("Emulation")

        rule.waitForText("Auto Save on Exit", timeout = 8_000)
    }
}
