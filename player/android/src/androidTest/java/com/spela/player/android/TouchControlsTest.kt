package com.spela.player.android

import android.content.Context
import android.view.InputDevice
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/**
 * Touch-control tests are inherently incompatible with gamepad-attached
 * hardware — InGameOverlay hides the on-screen touch overlay whenever
 * a gamepad is connected (the user has real buttons; the touch buttons
 * would just clutter the screen). Every assertion in this file checks
 * that the touch overlay IS visible, so on the AYN Thor (clamshell with
 * always-connected gamepad) the entire class would fail spuriously.
 *
 * Skip the whole class via JUnit's Assume on devices with a gamepad
 * detected. On a non-gamepad emulator (the recommended setup per
 * docs/e2e-testing.md), all tests run.
 */
@RequiresPhysicalDevice(reason = "Touch controls overlay requires libretro game running with on-screen D-pad rendered; the emulator's UiAutomator missed the D-pad nodes")
class TouchControlsTest : BaseE2ETest() {

    @Before
    fun skipWhenGamepadConnected() {
        assumeFalse("touch-control tests require no gamepad connected", hasGamepad())
    }

    private fun setupGame() {
        rule.navigateToGameAndPlay()
    }

    private fun hasGamepad(): Boolean {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return ctx.getSystemService(Context.INPUT_SERVICE)
            ?.let { it as android.hardware.input.InputManager }
            ?.inputDeviceIds?.any { id ->
                val dev = InputDevice.getDevice(id) ?: return@any false
                (dev.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (dev.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            } ?: false
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
