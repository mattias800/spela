package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EstablishSessionTest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun establishSession() {
        // Use ensureLoggedIn which handles any starting state
        composeTestRule.ensureLoggedIn()

        // Verify we're on the Home screen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Consoles")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
