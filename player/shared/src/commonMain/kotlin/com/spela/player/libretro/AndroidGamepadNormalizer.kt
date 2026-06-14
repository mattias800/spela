package com.spela.player.libretro

import com.spela.player.domain.model.GamepadPosition

/**
 * Normalizes an Android `KeyEvent.KEYCODE_*` to a canonical [GamepadPosition]
 * (the input layer of the two-layer model, #1334). Android gamepad key codes
 * are nominally positional — `KEYCODE_BUTTON_A` is the bottom face button,
 * `KEYCODE_BUTTON_B` the right, etc. — so the standard mapping below holds for
 * spec-compliant controllers of every brand, which is what makes "same physical
 * button = same console action on any pad" work once the positional mapping
 * layer is applied.
 *
 * Pure + platform-agnostic (the key-code integers are inlined so this lives in
 * commonMain and is desktop-unit-testable). Constants mirror
 * `android.view.KeyEvent`.
 *
 * Brand note: a few controllers genuinely report swapped face-button codes. We
 * deliberately do NOT guess per-brand swaps here (that risks breaking compliant
 * pads); precise per-controller correction belongs to the editable input-layer
 * calibration (Phase 4), where the user confirms it on real hardware.
 */
object AndroidGamepadNormalizer {
    // android.view.KeyEvent constants.
    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_DOWN = 20
    private const val KEYCODE_DPAD_LEFT = 21
    private const val KEYCODE_DPAD_RIGHT = 22
    private const val KEYCODE_BUTTON_A = 96
    private const val KEYCODE_BUTTON_B = 97
    private const val KEYCODE_BUTTON_X = 99
    private const val KEYCODE_BUTTON_Y = 100
    private const val KEYCODE_BUTTON_L1 = 102
    private const val KEYCODE_BUTTON_R1 = 103
    private const val KEYCODE_BUTTON_L2 = 104
    private const val KEYCODE_BUTTON_R2 = 105
    private const val KEYCODE_BUTTON_THUMBL = 106
    private const val KEYCODE_BUTTON_THUMBR = 107
    private const val KEYCODE_BUTTON_START = 108
    private const val KEYCODE_BUTTON_SELECT = 109

    /** Returns the canonical position for [keyCode], or null if it isn't a gamepad button. */
    fun normalize(keyCode: Int): GamepadPosition? = when (keyCode) {
        KEYCODE_DPAD_UP -> GamepadPosition.DPAD_UP
        KEYCODE_DPAD_DOWN -> GamepadPosition.DPAD_DOWN
        KEYCODE_DPAD_LEFT -> GamepadPosition.DPAD_LEFT
        KEYCODE_DPAD_RIGHT -> GamepadPosition.DPAD_RIGHT
        KEYCODE_BUTTON_A -> GamepadPosition.SOUTH
        KEYCODE_BUTTON_B -> GamepadPosition.EAST
        KEYCODE_BUTTON_X -> GamepadPosition.WEST
        KEYCODE_BUTTON_Y -> GamepadPosition.NORTH
        KEYCODE_BUTTON_L1 -> GamepadPosition.L1
        KEYCODE_BUTTON_R1 -> GamepadPosition.R1
        KEYCODE_BUTTON_L2 -> GamepadPosition.L2
        KEYCODE_BUTTON_R2 -> GamepadPosition.R2
        KEYCODE_BUTTON_THUMBL -> GamepadPosition.L3
        KEYCODE_BUTTON_THUMBR -> GamepadPosition.R3
        KEYCODE_BUTTON_START -> GamepadPosition.START
        KEYCODE_BUTTON_SELECT -> GamepadPosition.SELECT
        else -> null
    }
}
