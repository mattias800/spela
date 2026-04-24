package com.spela.player.android

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

class GamepadNavigationTest : BaseE2ETest() {

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
