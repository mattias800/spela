package com.spela.player.android

import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.presentation.App
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.LibretroButtons
import com.spela.player.presentation.viewmodel.LibretroController
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val libretroController: LibretroController by inject()
    private val androidController: AndroidLibretroController?
        get() = libretroController as? AndroidLibretroController

    private val navigationViewModel: NavigationViewModel by inject()
    private val emulationViewModel: EmulationViewModel by inject()

    /** True when gamepad input should go to libretro (game running, overlay not shown). */
    private val isEmulationConsuming: Boolean
        get() {
            val navState = navigationViewModel.state.value
            val emuState = emulationViewModel.state.value
            return navState.showInGameOverlay && emuState.isRunning && !emuState.showOverlay
        }

    // Track analog-to-dpad state for UI navigation
    private var analogDpadLeft = false
    private var analogDpadRight = false
    private var analogDpadUp = false
    private var analogDpadDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isEmulationConsuming) {
            val buttonId = GamepadMapping.mapKeyToLibretro(keyCode)
            if (buttonId != null) {
                androidController?.let {
                    it.setButton(0, buttonId, true)
                    it.notifyPhysicalControllerInput()
                }
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        // UI mode: remap gamepad buttons for Compose navigation
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> {
                val now = SystemClock.uptimeMillis()
                val remapped = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)
                return super.dispatchKeyEvent(remapped)
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                navigationViewModel.onIntent(NavigationIntent.GoBack)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                switchTab(-1)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                switchTab(1)
                return true
            }
        }

        // Let D-pad and other keys propagate to Compose
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isEmulationConsuming) {
            val buttonId = GamepadMapping.mapKeyToLibretro(keyCode)
            if (buttonId != null) {
                androidController?.let {
                    it.setButton(0, buttonId, false)
                    it.notifyPhysicalControllerInput()
                }
                return true
            }
            return super.onKeyUp(keyCode, event)
        }

        // UI mode: consume gamepad buttons we handled in onKeyDown
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> {
                val now = SystemClock.uptimeMillis()
                val remapped = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0)
                return super.dispatchKeyEvent(remapped)
            }
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1 -> return true
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onGenericMotionEvent(event)

        val isJoystickOrGamepad =
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD

        if (!isJoystickOrGamepad || event.action != MotionEvent.ACTION_MOVE) {
            return super.onGenericMotionEvent(event)
        }

        if (isEmulationConsuming) {
            val controller = androidController ?: return super.onGenericMotionEvent(event)
            controller.notifyPhysicalControllerInput()

            val leftX = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_X))
            val leftY = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_Y))
            val rightX = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_Z))
            val rightY = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_RZ))

            controller.setAnalog(0, 0, 0, leftX)
            controller.setAnalog(0, 0, 1, leftY)
            controller.setAnalog(0, 1, 0, rightX)
            controller.setAnalog(0, 1, 1, rightY)

            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            controller.setButton(0, LibretroButtons.LEFT, hatX < -0.5f)
            controller.setButton(0, LibretroButtons.RIGHT, hatX > 0.5f)
            controller.setButton(0, LibretroButtons.UP, hatY < -0.5f)
            controller.setButton(0, LibretroButtons.DOWN, hatY > 0.5f)

            return true
        }

        // UI mode: convert analog stick to D-pad events for focus navigation
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val threshold = 0.5f
        val nowLeft = x < -threshold || hatX < -threshold
        val nowRight = x > threshold || hatX > threshold
        val nowUp = y < -threshold || hatY < -threshold
        val nowDown = y > threshold || hatY > threshold

        if (nowLeft && !analogDpadLeft) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN)
        if (!nowLeft && analogDpadLeft) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_UP)
        if (nowRight && !analogDpadRight) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN)
        if (!nowRight && analogDpadRight) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_UP)
        if (nowUp && !analogDpadUp) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN)
        if (!nowUp && analogDpadUp) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_UP)
        if (nowDown && !analogDpadDown) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN)
        if (!nowDown && analogDpadDown) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_UP)

        analogDpadLeft = nowLeft
        analogDpadRight = nowRight
        analogDpadUp = nowUp
        analogDpadDown = nowDown

        return true
    }

    private fun dispatchDpadEvent(keyCode: Int, action: Int) {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, action, keyCode, 0)
        super.dispatchKeyEvent(event)
    }

    private fun switchTab(direction: Int) {
        val tabRoutes = listOf("home", "downloads", "settings")
        val navState = navigationViewModel.state.value
        val currentRoute = when (navState.currentScreen) {
            is SpScreen.Home -> "home"
            is SpScreen.Downloads -> "downloads"
            is SpScreen.Settings -> "settings"
            else -> return
        }
        val currentIndex = tabRoutes.indexOf(currentRoute)
        if (currentIndex < 0) return
        val newIndex = (currentIndex + direction).coerceIn(0, tabRoutes.lastIndex)
        if (newIndex != currentIndex) {
            navigationViewModel.onIntent(NavigationIntent.SwitchTab(tabRoutes[newIndex]))
        }
    }
}
