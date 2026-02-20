package com.spela.player.domain.model

import android.view.KeyEvent
import com.spela.player.presentation.viewmodel.LibretroButtons

actual fun getPlatformPresets(): List<KeyMappingPreset> = listOf(
    XBOX_STANDARD,
    PLAYSTATION_STANDARD,
    NINTENDO_STANDARD,
)

/** Xbox / Standard gamepad: XInput layout. Most Android gamepads follow this. */
val XBOX_STANDARD = KeyMappingPreset(
    id = "xbox-standard",
    displayName = "Xbox / Standard Gamepad",
    description = "Standard XInput layout used by most Android gamepads",
    bindings = mapOf(
        LibretroButtons.UP to KeyEvent.KEYCODE_DPAD_UP,
        LibretroButtons.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
        LibretroButtons.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
        LibretroButtons.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
        LibretroButtons.B to KeyEvent.KEYCODE_BUTTON_A,
        LibretroButtons.A to KeyEvent.KEYCODE_BUTTON_B,
        LibretroButtons.Y to KeyEvent.KEYCODE_BUTTON_X,
        LibretroButtons.X to KeyEvent.KEYCODE_BUTTON_Y,
        LibretroButtons.START to KeyEvent.KEYCODE_BUTTON_START,
        LibretroButtons.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT,
        LibretroButtons.L to KeyEvent.KEYCODE_BUTTON_L1,
        LibretroButtons.R to KeyEvent.KEYCODE_BUTTON_R1,
        LibretroButtons.L2 to KeyEvent.KEYCODE_BUTTON_L2,
        LibretroButtons.R2 to KeyEvent.KEYCODE_BUTTON_R2,
        LibretroButtons.L3 to KeyEvent.KEYCODE_BUTTON_THUMBL,
        LibretroButtons.R3 to KeyEvent.KEYCODE_BUTTON_THUMBR,
    ),
)

/** PlayStation layout: Cross/Circle mapped to match PS convention. */
val PLAYSTATION_STANDARD = KeyMappingPreset(
    id = "playstation-standard",
    displayName = "PlayStation Gamepad",
    description = "Cross=B, Circle=A (PlayStation button convention)",
    bindings = mapOf(
        LibretroButtons.UP to KeyEvent.KEYCODE_DPAD_UP,
        LibretroButtons.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
        LibretroButtons.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
        LibretroButtons.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
        LibretroButtons.B to KeyEvent.KEYCODE_BUTTON_A, // Cross
        LibretroButtons.A to KeyEvent.KEYCODE_BUTTON_B, // Circle
        LibretroButtons.Y to KeyEvent.KEYCODE_BUTTON_X, // Square
        LibretroButtons.X to KeyEvent.KEYCODE_BUTTON_Y, // Triangle
        LibretroButtons.START to KeyEvent.KEYCODE_BUTTON_START,
        LibretroButtons.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT,
        LibretroButtons.L to KeyEvent.KEYCODE_BUTTON_L1,
        LibretroButtons.R to KeyEvent.KEYCODE_BUTTON_R1,
        LibretroButtons.L2 to KeyEvent.KEYCODE_BUTTON_L2,
        LibretroButtons.R2 to KeyEvent.KEYCODE_BUTTON_R2,
        LibretroButtons.L3 to KeyEvent.KEYCODE_BUTTON_THUMBL,
        LibretroButtons.R3 to KeyEvent.KEYCODE_BUTTON_THUMBR,
    ),
)

/** Nintendo Pro Controller: A=East, B=South (Nintendo convention). */
val NINTENDO_STANDARD = KeyMappingPreset(
    id = "nintendo-standard",
    displayName = "Nintendo Pro Controller",
    description = "A=East, B=South (Nintendo button convention)",
    bindings = mapOf(
        LibretroButtons.UP to KeyEvent.KEYCODE_DPAD_UP,
        LibretroButtons.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
        LibretroButtons.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
        LibretroButtons.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
        LibretroButtons.B to KeyEvent.KEYCODE_BUTTON_B, // South = B
        LibretroButtons.A to KeyEvent.KEYCODE_BUTTON_A, // East = A
        LibretroButtons.Y to KeyEvent.KEYCODE_BUTTON_Y, // West = Y
        LibretroButtons.X to KeyEvent.KEYCODE_BUTTON_X, // North = X
        LibretroButtons.START to KeyEvent.KEYCODE_BUTTON_START,
        LibretroButtons.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT,
        LibretroButtons.L to KeyEvent.KEYCODE_BUTTON_L1,
        LibretroButtons.R to KeyEvent.KEYCODE_BUTTON_R1,
        LibretroButtons.L2 to KeyEvent.KEYCODE_BUTTON_L2,
        LibretroButtons.R2 to KeyEvent.KEYCODE_BUTTON_R2,
        LibretroButtons.L3 to KeyEvent.KEYCODE_BUTTON_THUMBL,
        LibretroButtons.R3 to KeyEvent.KEYCODE_BUTTON_THUMBR,
    ),
)
