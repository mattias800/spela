package com.spela.player.android

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test

class SettingsTest : BaseE2ETest() {

    /** Scroll down in the LazyColumn until a node with the given contentDescription appears. */
    private fun scrollDownUntilContentDescription(description: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        for (attempt in 0..5) {
            try {
                if (rule.onAllNodesWithContentDescription(description, substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
                ) return
            } catch (_: IllegalStateException) { /* tree not ready */ }
            val centerX = device.displayWidth / 2
            val fromY = (device.displayHeight * 0.7).toInt()
            val toY = (device.displayHeight * 0.3).toInt()
            device.swipe(centerX, fromY, centerX, toY, 15)
            rule.waitForIdle()
        }
        // Final assertion — will throw a clear error if still not found
        rule.waitForContentDescription(description, timeout = 5_000)
    }

    /**
     * Navigate from the Per Console tab to ConsoleSettingsScreen for NES.
     * Uses hasClickAction() to find the clickable SpCard (not a child text node)
     * and performSemanticsAction to invoke onClick directly, bypassing touch
     * event handling that can be intercepted by the LazyColumn scroll handler.
     */
    private fun tapNESConsole() {
        val nesCard = hasText("Nintendo Entertainment System", substring = true) and
                hasText("Super", substring = true).not() and
                hasClickAction()
        rule.pollUntil(timeoutMillis = 10_000) {
            try {
                rule.onAllNodes(nesCard).fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) { false }
        }
        rule.onNode(nesCard).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.waitForText("Nintendo Entertainment System Settings", timeout = 8_000)
    }

    @Test
    fun shaderPreview() {

        // Navigate to Settings → Emulation (where Video Filter lives)
        rule.navigateToSettingsCategory("Emulation")

        // Scroll to Video Filter
        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")

        // Select CRT Classic
        rule.scrollToAndTapText("CRT Classic")

        // Navigate to Per-Console category for console-specific shaders
        rule.pressBack() // Back to category list
        rule.waitForText("General")
        rule.tapOn("Per-Console")

        // Tap NES console and wait for ConsoleSettingsScreen
        tapNESConsole()

        // Scroll to shader preview on ConsoleSettingsScreen
        scrollDownUntilContentDescription("Shader preview")

        // Test fullscreen preview dialog
        rule.onNodeWithContentDescription("Shader preview", substring = true).performClick()
        rule.waitForText("Tap to close", timeout = 3_000)

        // Dismiss dialog
        rule.onNodeWithText("Tap to close").performClick()

        // Navigate back to Settings
        rule.pressBack()
        rule.waitForText("General", timeout = 8_000)
    }

    @Test
    fun consoleShaderPersists() {

        // Navigate to Settings → Per-Console category
        rule.navigateToSettingsCategory("Per-Console")

        // Tap NES console and wait for ConsoleSettingsScreen
        tapNESConsole()

        // Select CRT Classic
        rule.scrollToAndTapText("CRT Classic")

        // Navigate back to Settings
        rule.pressBack()
        rule.waitForText("General", timeout = 8_000)

        // Navigate back to Home
        rule.pressBack()
        rule.waitForText("Spela", timeout = 3_000)

        // Return to Settings → Per-Console
        rule.navigateToSettingsCategory("Per-Console")

        // Verify NES still shows CRT Classic
        rule.waitForText("Nintendo Entertainment System", timeout = 10_000)
        rule.waitForText("CRT Classic")
    }

    @Test
    fun deviceShaderOverride() {

        // Navigate to Settings → Emulation → set global shader
        rule.navigateToSettingsCategory("Emulation")
        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")
        rule.scrollToAndTapText("CRT Classic")

        // Go to Per-Console category
        rule.pressBack() // Back to category list
        rule.waitForText("General")
        rule.tapOn("Per-Console")

        // Tap NES console and wait for ConsoleSettingsScreen
        tapNESConsole()

        // Enable device override
        rule.scrollToAndTapText("Override on this device only")

        // Select Smooth (Bilinear) as device override
        rule.scrollToAndTapText("Device Shader")
        rule.waitForText("Device Shader")
        rule.scrollToAndTapText("Smooth (Bilinear)")

        // Navigate back to Settings
        rule.pressBack()
        rule.waitForText("General", timeout = 8_000)

        // Navigate back to Home
        rule.pressBack()
        rule.waitForText("Spela", timeout = 3_000)

        // Return to Settings → Per-Console
        rule.navigateToSettingsCategory("Per-Console")

        // Verify NES shows Bilinear with device override indicator
        rule.waitForText("Nintendo Entertainment System", timeout = 10_000)
        rule.waitForText("Smooth")
    }

    @Test
    fun shaderSelectionPersists() {

        // Navigate to Settings → Emulation (where Video Filter lives)
        rule.navigateToSettingsCategory("Emulation")

        // Scroll to Video Filter
        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")

        // Select CRT Classic
        rule.scrollToAndTapText("CRT Classic")

        // Restart app
        rule.restartApp()

        // Session restored - expect Home screen. Use isOnHomeScreen()
        // (Compose + UiAutomator) instead of waitForText which can
        // miss the brand mark when activity routes to a non-primary
        // display after recreate.
        rule.pollUntil(timeoutMillis = 15_000) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }

        // Navigate to Settings → Emulation and verify shader persisted
        rule.navigateToSettingsCategory("Emulation")

        rule.scrollToAndTapText("Video Filter")
        rule.waitForText("Video Filter")
        rule.waitForText("CRT Classic")
    }

    @Test
    fun retroAchievementsSection() {

        // Navigate to Settings → Achievements category
        rule.navigateToSettingsCategory("Achievements")

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
