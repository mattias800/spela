package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shaderPreview() {
        rule.startLoggedIn()

        // Navigate to Settings
        rule.navigateToSettings()

        // Scroll to Video Filter
        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")

        // Select CRT Classic
        rule.scrollToAndTapText("CRT Classic")

        // Scroll to shader preview (contentDescription, not text)
        rule.waitForContentDescription("Shader preview", timeout = 8_000)
        rule.onNodeWithContentDescription("Shader preview", substring = true).performScrollTo()

        // Navigate to Per Console tab
        rule.onNodeWithText("Per Console").performScrollTo()
        rule.onNodeWithText("Per Console").performClick()

        // Wait for console list to load
        rule.waitForText("Nintendo Entertainment System", timeout = 10_000)

        // Tap NES console
        rule.scrollToAndTapText("Nintendo Entertainment System")

        // Wait for ConsoleSettingsScreen
        rule.waitForText("Video Filter")

        // Scroll to shader preview on ConsoleSettingsScreen
        rule.waitForContentDescription("Shader preview", timeout = 5_000)
        rule.onNodeWithContentDescription("Shader preview", substring = true).performScrollTo()

        // Test fullscreen preview dialog
        rule.onNodeWithContentDescription("Shader preview", substring = true).performClick()
        rule.waitForText("Tap to close", timeout = 3_000)

        // Dismiss dialog
        rule.onNodeWithText("Tap to close").performClick()

        // Navigate back to Settings
        rule.pressBack()
        rule.waitForText("Account", timeout = 3_000)
    }

    @Test
    fun consoleShaderPersists() {
        rule.startLoggedIn()

        // Navigate to Settings
        rule.navigateToSettings()

        // Open Video Filter section, then switch to Per Console tab
        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")
        rule.onNodeWithText("Per Console").performScrollTo()
        rule.onNodeWithText("Per Console").performClick()

        // Wait for console list to load, then tap NES
        rule.waitForText("Nintendo Entertainment System", timeout = 10_000)
        rule.scrollToAndTapText("Nintendo Entertainment System")

        // Wait for ConsoleSettingsScreen
        rule.waitForText("Video Filter")

        // Select CRT Classic
        rule.scrollToAndTapText("CRT Classic")

        // Navigate back to Settings
        rule.pressBack()
        rule.waitForText("Account")

        // Navigate back to Home
        rule.pressBack()
        rule.waitForText("Consoles", timeout = 3_000)

        // Return to Settings
        rule.navigateToSettings()

        // Open Video Filter section and go to Per Console tab
        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")
        rule.onNodeWithText("Per Console").performScrollTo()
        rule.onNodeWithText("Per Console").performClick()

        // Verify NES still shows CRT Classic
        rule.waitForText("Nintendo Entertainment System", timeout = 10_000)
        rule.waitForText("CRT Classic")
    }

    @Test
    fun deviceShaderOverride() {
        rule.startLoggedIn()

        // Navigate to Settings
        rule.navigateToSettings()

        // Set global shader to CRT Classic
        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")
        rule.scrollToAndTapText("CRT Classic")

        // Switch to Per Console tab
        rule.onNodeWithText("Per Console").performScrollTo()
        rule.onNodeWithText("Per Console").performClick()

        // Wait for console list to load, then tap NES
        rule.waitForText("Nintendo Entertainment System", timeout = 10_000)
        rule.scrollToAndTapText("Nintendo Entertainment System")

        // Wait for ConsoleSettingsScreen
        rule.waitForText("Video Filter")

        // Enable device override
        rule.scrollToAndTapText("Override on this device only")

        // Select Smooth (Bilinear) as device override
        rule.scrollToAndTapText("Device Shader")
        rule.waitForText("Device Shader")
        rule.scrollToAndTapText("Smooth (Bilinear)")

        // Navigate back to Settings
        rule.pressBack()
        rule.waitForText("Account")

        // Navigate back to Home
        rule.pressBack()
        rule.waitForText("Consoles", timeout = 3_000)

        // Return to Settings
        rule.navigateToSettings()

        // Open Video Filter and go to Per Console tab
        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")
        rule.onNodeWithText("Per Console").performScrollTo()
        rule.onNodeWithText("Per Console").performClick()

        // Verify NES shows Bilinear with device override indicator
        rule.waitForText("Nintendo Entertainment System", timeout = 10_000)
        rule.waitForText("Smooth")
    }

    @Test
    fun shaderSelectionPersists() {
        rule.ensureLoggedIn()

        // Navigate to Settings
        rule.navigateToSettings()

        // Scroll to Video Filter
        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")

        // Select CRT Classic
        rule.scrollToAndTapText("CRT Classic")

        // Restart app
        rule.restartApp()

        // Session restored - expect Home screen
        rule.waitForText("Consoles", timeout = 15_000)

        // Navigate to Settings and verify shader persisted
        rule.navigateToSettings()

        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")
        rule.waitForText("CRT Classic")
    }

    @Test
    fun retroAchievementsSection() {
        rule.ensureLoggedIn()

        // Navigate to Settings
        rule.navigateToSettings()

        // Scroll to RetroAchievements section
        rule.scrollToAndTapText("RetroAchievements")
        rule.waitForText("RetroAchievements")

        // Assert Link Account button visible
        rule.waitForText("Link Account")

        // Assert description text
        rule.waitForText("earn achievements")

        // Tap Link Account
        rule.onNodeWithText("Link Account").performClick()

        // Assert link dialog appears
        rule.waitForText("Link RetroAchievements", timeout = 3_000)

        // Assert dialog has username and password fields
        rule.waitForText("Username")
        rule.waitForText("Password")

        // Dismiss dialog
        rule.onNodeWithText("Cancel").performClick()

        // Assert dialog dismissed
        rule.waitForTextNotVisible("Link RetroAchievements")

        // Verify back on Settings
        rule.waitForText("RetroAchievements")
    }
}
