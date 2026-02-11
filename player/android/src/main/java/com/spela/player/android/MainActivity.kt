package com.spela.player.android

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.presentation.App
import com.spela.player.presentation.viewmodel.LibretroButtons
import com.spela.player.presentation.viewmodel.LibretroController
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val libretroController: LibretroController by inject()
    private val androidController: AndroidLibretroController?
        get() = libretroController as? AndroidLibretroController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Check if the keycode maps to a libretro button regardless of input source.
        // Many built-in controllers (e.g. Odin 2) report with varying source flags.
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

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
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

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onGenericMotionEvent(event)

        val isJoystickOrGamepad =
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD

        if (isJoystickOrGamepad && event.action == MotionEvent.ACTION_MOVE) {
            val controller = androidController ?: return super.onGenericMotionEvent(event)
            controller.notifyPhysicalControllerInput()

            // Analog sticks
            val leftX = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_X))
            val leftY = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_Y))
            val rightX = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_Z))
            val rightY = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_RZ))

            controller.setAnalog(0, 0, 0, leftX)
            controller.setAnalog(0, 0, 1, leftY)
            controller.setAnalog(0, 1, 0, rightX)
            controller.setAnalog(0, 1, 1, rightY)

            // Hat switch D-pad (used by many built-in controllers instead of KEYCODE_DPAD_*)
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            controller.setButton(0, LibretroButtons.LEFT, hatX < -0.5f)
            controller.setButton(0, LibretroButtons.RIGHT, hatX > 0.5f)
            controller.setButton(0, LibretroButtons.UP, hatY < -0.5f)
            controller.setButton(0, LibretroButtons.DOWN, hatY > 0.5f)

            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
