package com.spela.player.android

import android.view.KeyEvent
import com.spela.player.presentation.viewmodel.LibretroButtons

/**
 * Maps Android gamepad key codes to libretro joypad button IDs.
 *
 * Button layout follows the standard mapping where Android's physical A/B buttons
 * map to libretro's B/A respectively (Nintendo-style layout: right button = A action).
 */
object GamepadMapping {

    /**
     * Maps an Android [KeyEvent] key code to a libretro button ID.
     * Returns null if the key code is not a recognized gamepad button.
     */
    fun mapKeyToLibretro(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> LibretroButtons.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> LibretroButtons.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> LibretroButtons.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> LibretroButtons.RIGHT
        KeyEvent.KEYCODE_BUTTON_A -> LibretroButtons.B
        KeyEvent.KEYCODE_BUTTON_B -> LibretroButtons.A
        KeyEvent.KEYCODE_BUTTON_X -> LibretroButtons.Y
        KeyEvent.KEYCODE_BUTTON_Y -> LibretroButtons.X
        KeyEvent.KEYCODE_BUTTON_START -> LibretroButtons.START
        KeyEvent.KEYCODE_BUTTON_SELECT -> LibretroButtons.SELECT
        KeyEvent.KEYCODE_BUTTON_L1 -> LibretroButtons.L
        KeyEvent.KEYCODE_BUTTON_R1 -> LibretroButtons.R
        KeyEvent.KEYCODE_BUTTON_L2 -> LibretroButtons.L2
        KeyEvent.KEYCODE_BUTTON_R2 -> LibretroButtons.R2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> LibretroButtons.L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> LibretroButtons.R3
        else -> null
    }

    /**
     * Normalizes an Android analog axis value (range -1.0..1.0) to a libretro
     * int16 value (range -32768..32767), applying a dead zone to filter noise.
     */
    fun normalizeAxis(value: Float, deadZone: Float = 0.1f): Short {
        val adjusted = if (kotlin.math.abs(value) < deadZone) 0f else value
        return (adjusted * Short.MAX_VALUE).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}
