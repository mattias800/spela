package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EstablishSessionTest {

    

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun establishSession() {
        // Use ensureLoggedIn which handles any starting state
        composeTestRule.ensureLoggedIn()

        // Verify we're on the Home screen — check multiple indicators since the
        // Home screen may show "Your library is empty" for users with no play history
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Spela")
                .fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Your library is empty", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Top Rated", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Continue Playing", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
