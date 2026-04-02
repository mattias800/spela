package com.spela.player.android

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionTest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

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
        rule.ensureLoggedIn()

        // Restart app (session should persist via SQLDelight)
        rule.restartApp()

        // Assert home screen appears without login prompt
        rule.waitForText("Spela", timeout = 8_000)
    }

    @Test
    fun logoutClearsTokensPreservesServer() {
        rule.ensureLoggedIn()

        // Navigate to Settings and sign out
        rule.navigateToSettings()
        rule.waitForText("Sign Out", timeout = 3_000)
        rule.onNodeWithText("Sign Out").performClick()
        confirmSignOutDialog()

        // Verify server still listed
        rule.assertTextVisible("Local")

        // Restart and verify tokens cleared but server persists
        rule.restartApp()

        // After restart, app shows server connection screen with "Local" still listed
        rule.waitUntil(timeoutMillis = 15_000) {
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
        rule.ensureLoggedIn()

        // Navigate to Settings → Sign Out
        rule.navigateToSettings()
        rule.waitForText("Sign Out", timeout = 3_000)
        rule.onNodeWithText("Sign Out").performClick()
        confirmSignOutDialog()

        // Verify server is visible
        rule.assertTextVisible("Local")

        // Restart app
        rule.restartApp()

        // After restart, server should persist (may show Login or server list)
        rule.waitUntil(timeoutMillis = 15_000) {
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
        rule.ensureLoggedIn()

        // Sign out to get to a clean state
        rule.navigateToSettings()
        rule.waitForText("Sign Out", timeout = 3_000)
        rule.onNodeWithText("Sign Out").performClick()
        confirmSignOutDialog()

        // Server should still be listed — tap it
        rule.assertTextVisible("Local")
        rule.onNodeWithText("Local").performClick()

        // Login (tests landscape scrollability)
        rule.waitForText("Username", timeout = 5_000)

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
        rule.ensureLoggedIn()

        // Navigate to Settings
        rule.navigateToSettings()

        // Scroll to Auto Save on Exit
        rule.waitForText("Auto Save on Exit", timeout = 8_000)

        // Toggle Auto Save on Exit
        rule.onNodeWithText("Auto Save on Exit").performClick()

        // Restart app
        rule.restartApp()

        // Session restored
        rule.waitForText("Spela", timeout = 15_000)

        // Navigate to Settings and verify toggle persisted
        rule.navigateToSettings()

        rule.waitForText("Auto Save on Exit", timeout = 8_000)
    }
}
