package com.spela.player.android

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

class TouchControlsTest : BaseE2ETest() {

    private fun setupGame() {
        rule.navigateToGameAndPlay()
    }

    @Test
    fun touchControlsVisible() {
        setupGame()

        rule.assertVisible("Touch controls")
        rule.assertVisible("D-pad Up")
        rule.assertVisible("D-pad Down")
        rule.assertVisible("D-pad Left")
        rule.assertVisible("D-pad Right")
        rule.assertVisible("Button A")
        rule.assertVisible("Button B")
        rule.assertVisible("Button Start")
        rule.assertVisible("Button Select")

        rule.openOverlayAndExit()
    }

    @Test
    fun actionButtonsTappable() {
        setupGame()

        rule.tapOn("Button A")
        rule.tapOn("Button B")
        rule.tapOn("Button Start")
        rule.tapOn("Button Select")

        rule.openOverlay()
        rule.assertTextVisible("Continue")

        rule.exitGame()
    }

    @Test
    fun dpadButtonsTappable() {
        setupGame()

        rule.tapOn("D-pad Up")
        rule.tapOn("D-pad Down")
        rule.tapOn("D-pad Left")
        rule.tapOn("D-pad Right")

        rule.openOverlay()
        rule.assertTextVisible("Continue")

        rule.exitGame()
    }

    @Test
    fun hiddenDuringOverlay() {
        setupGame()

        rule.assertVisible("Touch controls")
        rule.assertVisible("D-pad Up")
        rule.assertVisible("Button A")
        rule.assertVisible("Button Start")

        rule.openOverlay()

        rule.assertNotVisible("Touch controls")
        rule.assertNotVisible("D-pad Up")
        rule.assertNotVisible("Button A")
        rule.assertNotVisible("Button Start")

        rule.tapOn("Continue")
        rule.waitForTextNotVisible("Exit Game")

        rule.assertVisible("Touch controls")
        rule.assertVisible("D-pad Up")

        rule.openOverlayAndExit()
    }

    @Test
    fun surviveOverlayCycles() {
        setupGame()

        // Cycle 1
        rule.assertVisible("Touch controls")
        rule.assertVisible("D-pad Up")
        rule.assertVisible("Button A")

        rule.openOverlay()
        rule.assertNotVisible("Touch controls")

        rule.tapOn("Continue")
        rule.waitForTextNotVisible("Exit Game")

        rule.assertVisible("Touch controls")
        rule.assertVisible("D-pad Up")
        rule.assertVisible("Button A")

        // Cycle 2: dismiss via backdrop
        rule.openOverlay()
        rule.assertNotVisible("Touch controls")

        rule.tapAtPercent(50f, 10f)
        rule.waitForTextNotVisible("Exit Game")

        rule.assertVisible("Touch controls")
        rule.assertVisible("D-pad Down")
        rule.assertVisible("Button B")

        // Cycle 3
        rule.openOverlay()
        rule.assertNotVisible("Touch controls")

        rule.tapOn("Continue")
        rule.waitForTextNotVisible("Exit Game")

        rule.assertVisible("Touch controls")

        rule.openOverlayAndExit()
    }

    @Test
    fun dualScreenRegression() {
        setupGame()

        // All touch controls visible
        rule.assertVisible("Touch controls")
        rule.assertVisible("D-pad Up")
        rule.assertVisible("D-pad Down")
        rule.assertVisible("D-pad Left")
        rule.assertVisible("D-pad Right")
        rule.assertVisible("Button A")
        rule.assertVisible("Button B")
        rule.assertVisible("Button Start")
        rule.assertVisible("Button Select")

        // Open overlay, touch controls hide
        rule.openOverlay()
        rule.assertNotVisible("Touch controls")

        // Overlay has all controls
        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")
        rule.assertVisible("Load")
        rule.assertVisible("Screenshot")
        rule.assertVisible("Fast")
        rule.assertTextVisible("Exit Game")

        // Dismiss, touch controls return
        rule.tapOn("Continue")
        rule.waitForTextNotVisible("Exit Game")

        rule.assertVisible("Touch controls")
        rule.assertVisible("D-pad Up")
        rule.assertVisible("Button A")

        rule.openOverlayAndExit()
    }
}
