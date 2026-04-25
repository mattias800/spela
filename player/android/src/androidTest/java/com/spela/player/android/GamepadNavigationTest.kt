package com.spela.player.android

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * D-pad / gamepad navigation tests use Instrumentation.sendKeyDownUpSync,
 * which targets the focused window on display 0. On multi-display
 * hardware (e.g. AYN Thor's secondary "Screen-2" presentation display)
 * the activity sometimes sits on display 4 and the injected keys never
 * reach it — the navigation never happens, the test times out.
 *
 * Real users with a real gamepad on those devices have no such problem;
 * this is a limitation of test-framework key injection, not the app.
 *
 * Skip the whole class on multi-display setups. Single-display
 * emulators and phones run all three tests normally.
 */
class GamepadNavigationTest : BaseE2ETest() {

    @Before
    fun skipOnMultiDisplay() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val onDisplays = dm.displays.count { it.state == Display.STATE_ON }
        assumeTrue(
            "gamepad-navigation tests can't reliably target the focused window on multi-display hardware",
            onDisplays <= 1,
        )
    }

    @Test
    fun dpadHomeNavigation() {

        // Navigate with D-pad: move focus down to a console card
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.DOWN)

        // Select with DPAD_CENTER
        rule.sendDpad(DpadDirection.CENTER)

        // Verify we navigated to a console screen
        rule.waitForVisible("Go back", timeout = 3_000)

        // Navigate back
        rule.pressBack()
        rule.waitForText("Spela", timeout = 3_000)

        // Verify D-pad RIGHT also works
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.RIGHT)

        // Select with DPAD_CENTER
        rule.sendDpad(DpadDirection.CENTER)

        // Verify navigation happened
        rule.waitForVisible("Go back", timeout = 3_000)

        // Go back
        rule.pressBack()
        rule.waitForText("Spela", timeout = 3_000)
    }

    @Test
    fun dpadOverlayInteraction() {
        rule.navigateToGameAndPlay()

        // Open overlay
        rule.openOverlay()
        rule.assertTextVisible("Continue")
        rule.assertTextVisible("Exit Game")

        // Send D-pad events while overlay is open
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.UP)
        rule.sendDpad(DpadDirection.LEFT)
        rule.sendDpad(DpadDirection.RIGHT)

        // Verify overlay still showing (D-pad didn't dismiss it)
        rule.assertTextVisible("Continue")
        rule.assertTextVisible("Exit Game")

        // Dismiss overlay
        rule.tapOn("Continue")

        // Verify overlay dismissed and game resumed
        rule.waitForTextNotVisible("Continue")
        rule.waitForContentDescription("FPS", timeout = 5_000)

        // Reopen and exit
        rule.openOverlay()
        rule.exitGame()
    }

    @Test
    fun dpadSettingsNavigation() {

        // Navigate to Settings
        rule.navigateToSettings()

        // Scroll to Video Filter section
        rule.scrollToAndTapText("Video Filter")
        // We just need to scroll to it, not tap it - let me just wait for it
        rule.waitForText("Video Filter")

        // Use D-pad to navigate through shader options
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.DOWN)

        // Activate a shader option
        rule.sendDpad(DpadDirection.CENTER)

        // Navigate down more
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.DOWN)

        // Activate another option
        rule.sendDpad(DpadDirection.CENTER)

        // Navigate down further
        rule.sendDpad(DpadDirection.DOWN)
        rule.sendDpad(DpadDirection.DOWN)

        // Verify still on Settings
        rule.assertVisible("Settings")
    }
}
