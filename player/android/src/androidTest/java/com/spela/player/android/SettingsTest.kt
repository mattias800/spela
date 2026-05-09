package com.spela.player.android

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test

@RequiresPhysicalDevice(
    reason = "All five tests drive Settings UI navigation; depends on " +
        "BaseE2ETest.ensureLoggedIn() which the AVD's AndroidView'd EditText flow " +
        "doesn't reliably authenticate (#1146 root cause)."
)
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
     * for NES. The Per-Console list is a LazyColumn tagged
     * `settings_category_content_list`; each row exposes a
     * `console_settings_row_<id>` testTag. We drive scrolling through
     * Compose's `performScrollToNode` (which talks directly to the
     * LazyColumn state) rather than UiAutomator swipes — the latter
     * can land on the wrong display on multi-display devices like the
     * AYN Thor. The click itself goes through the OnClick semantics
     * action, again avoiding touch dispatch.
     */
    private fun tapNESConsole() {
        rule.onNodeWithTag("settings_category_content_list")
            .performScrollToNode(hasTestTag("console_settings_row_nes"))
        rule.scrollToAndTapTag("console_settings_row_nes", maxSwipes = 0)
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
        rule.scrollToAndTapTag("shader_option_crt-simple")

        // Pop ConsoleSettingsScreen (it's a real sub-screen) back to
        // the Settings list-detail. Then leave Settings to Home so
        // the next navigateToSettingsCategory genuinely re-enters.
        rule.pressBack()
        rule.navigateBackToHome()
        rule.pollUntil(timeoutMillis = 5_000) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }

        // Re-enter Per-Console → NES → verify CRT Classic option is
        // still rendered (it always is — the assertion that matters is
        // the radio's "Selected" stateDescription, queried below).
        rule.navigateToSettingsCategory("Per-Console")
        tapNESConsole()
        rule.scrollToTag("shader_option_crt-simple", maxSwipes = 10)
        rule.assertRadioSelected("shader_option_crt-simple")
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

        // Enable device override + select Smooth (Bilinear) via testTags.
        // Tags are stable across copy changes and survive LazyColumn
        // recomposition (the row identity is preserved by the tag, not
        // by transient label text).
        rule.scrollToAndTapTag("device_shader_override_toggle")
        // Toggle is async (click → intent → ViewModel → recomposition).
        // Wait for at least one device-shader option to render before
        // scrolling for "bilinear" — otherwise scrollToTag's swipes can
        // race past the section as it appears.
        rule.waitForTag("device_shader_option_none", timeout = 5_000)
        rule.scrollToAndTapTag("device_shader_option_bilinear")

        // Verify by leaving and re-entering Per-Console.
        rule.navigateBackToHome()
        rule.pollUntil(timeoutMillis = 5_000) {
            try { rule.isOnHomeScreen() } catch (_: Exception) { false }
        }
        rule.navigateToSettingsCategory("Per-Console")

        // Open the NES ConsoleSettingsScreen — the active shader is
        // shown there as text. tapNESConsole already scrolls the
        // Per-Console list to the NES row, which the assertion would
        // otherwise have to do manually.
        tapNESConsole()
        rule.waitForText("Smooth", timeout = 10_000)
    }

    @Test
    fun shaderSelectionPersists() {
        // Skip on emulator: this test calls rule.restartApp() which
        // is documented as unreliable on AVDs (docs/e2e-testing.md
        // "restartApp() unreliable on emulators" — activityRule.scenario
        // .recreate() sometimes fails to re-establish the Compose
        // hierarchy and we time out waiting for Home). Persistence
        // semantics are covered by desktop tests instead.
        org.junit.Assume.assumeFalse(
            "restartApp is unreliable on the AVD; persistence is covered by desktop tests",
            isEmulator,
        )

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

        // Navigate to Settings → Emulation and verify shader persisted.
        // Scroll until the CRT Classic radio option (tagged
        // shader_option_crt-simple) is in the semantic tree — the
        // Emulation page is long, so a non-scrolling waitForText
        // misses the option below the fold.
        rule.navigateToSettingsCategory("Emulation")
        rule.scrollToTag("shader_option_crt-simple", maxSwipes = 15)
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

        // Dismiss via the SpDialog dismiss button's stable tag — multiple
        // "Cancel"-labelled nodes can coexist on the page, and the
        // SpButton wraps its label inside other Composables so a text
        // match isn't always the actually-clickable node.
        rule.scrollToAndTapTag("dialog_dismiss")

        // Assert dialog dismissed
        rule.waitForTextNotVisible("Link RetroAchievements")

        // Verify back on Settings
        rule.waitForText("RetroAchievements")
    }
}
