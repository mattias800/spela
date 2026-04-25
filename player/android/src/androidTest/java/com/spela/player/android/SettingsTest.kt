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
import androidx.test.uiautomator.UiSelector
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
     * Navigate from the Per-Console category to ConsoleSettingsScreen
     * for NES. The Per-Console list is a scrollable LazyColumn and
     * NES isn't always above the fold. Use UiAutomator's
     * UiScrollable which scrolls until the target description is
     * found (handles bottom-of-list bounce, doesn't overshoot).
     */
    private fun tapNESConsole() {
        val desc = "Open Nintendo Entertainment System settings"
        // The Per-Console list in list-detail layout has the right
        // pane as a scrollable LazyColumn. UiScrollable can lock onto
        // the wrong scrollable on this layout, so we drive the
        // scrolling ourselves: scroll to the top, then sweep
        // downward checking after each swipe.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val x = (device.displayWidth * 0.7).toInt()
        // 1. Scroll to top of right pane (downward swipes from top→bottom).
        repeat(15) {
            device.swipe(
                x,
                (device.displayHeight * 0.3).toInt(),
                x,
                (device.displayHeight * 0.8).toInt(),
                10,
            )
        }
        rule.waitForIdle()

        // 2. Now sweep downward, checking for NES after each swipe.
        var nesRow: androidx.test.uiautomator.UiObject? = null
        for (attempt in 0..20) {
            val row = device.findObject(UiSelector().descriptionContains(desc))
            if (row.exists()) {
                nesRow = row
                break
            }
            device.swipe(
                x,
                (device.displayHeight * 0.7).toInt(),
                x,
                (device.displayHeight * 0.4).toInt(),
                10,
            )
            rule.waitForIdle()
        }
        check(nesRow != null) {
            "NES console row not found after scrolling. The Per-Console " +
                "list may not include NES on this device."
        }
        nesRow.click()
        Thread.sleep(500)
        rule.waitForText("Nintendo Entertainment System Settings", timeout = 8_000)
    }

    @Test
    fun shaderPreview() {
        // Set the global shader from Emulation → Video Filter section.
        rule.navigateToSettingsCategory("Emulation")
        rule.scrollToAndTapText("CRT Classic")

        // Switch to Per-Console category in the still-visible list.
        rule.navigateToSettingsCategory("Per-Console")
        tapNESConsole()

        // Scroll to the shader preview row on ConsoleSettingsScreen.
        scrollDownUntilContentDescription("Shader preview")

        // Open the fullscreen preview dialog and dismiss it.
        rule.onNodeWithContentDescription("Shader preview", substring = true).performClick()
        rule.waitForText("Tap to close", timeout = 3_000)
        rule.onNodeWithText("Tap to close").performClick()

        // Pop ConsoleSettingsScreen back to the Settings list-detail.
        rule.pressBack()
    }

    @Test
    fun consoleShaderPersists() {
        // Per-Console → NES → ConsoleSettingsScreen, set CRT Classic.
        rule.navigateToSettingsCategory("Per-Console")
        tapNESConsole()
        rule.scrollToAndTapText("CRT Classic")

        // Pop ConsoleSettingsScreen (it's a real sub-screen) back to
        // the Settings list-detail. Then leave Settings to Home so
        // the next navigateToSettingsCategory genuinely re-enters.
        rule.pressBack()
        rule.navigateBackToHome()
        rule.pollUntil(timeoutMillis = 5_000) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }

        // Re-enter Per-Console → verify NES row shows CRT Classic.
        rule.navigateToSettingsCategory("Per-Console")
        rule.waitForText("Nintendo Entertainment System", timeout = 10_000)
        rule.waitForText("CRT Classic")
    }

    @Test
    fun deviceShaderOverride() {
        // Settings is now a list-detail layout. On wide screens the
        // category list is always visible; "Video Filter" is a
        // section header inside the Emulation content (not a
        // sub-screen). Switching categories means tapping the
        // category in the list, not pressBack-then-tap.

        // Set the global shader from Emulation → Video Filter section.
        rule.navigateToSettingsCategory("Emulation")
        rule.scrollToAndTapText("CRT Classic")

        // Switch to Per-Console category by tapping its row in the list.
        rule.navigateToSettingsCategory("Per-Console")
        tapNESConsole()

        // Enable device override.
        rule.scrollToAndTapText("Override on this device only")

        // Select Smooth (Bilinear) as the device-specific override.
        rule.scrollToAndTapText("Smooth (Bilinear)")

        // Verify by leaving and re-entering Per-Console.
        rule.navigateBackToHome()
        rule.pollUntil(timeoutMillis = 5_000) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }
        rule.navigateToSettingsCategory("Per-Console")

        // NES row should show "Smooth" with the device-override indicator.
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
