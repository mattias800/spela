package com.spela.player.domain.model

import androidx.compose.ui.input.key.Key
import com.spela.player.presentation.viewmodel.LibretroButtons

actual fun getPlatformPresets(): List<KeyMappingPreset> = listOf(
    KEYBOARD_ARROWS,
    KEYBOARD_WASD,
)

/** Default desktop preset: Arrow keys + Z/X/A/S (matches the hardcoded default). */
val KEYBOARD_ARROWS = KeyMappingPreset(
    id = "keyboard-arrows",
    displayName = "Keyboard (Arrows)",
    description = "Arrow keys for D-pad, Z/X for B/A, A/S for Y/X",
    bindings = mapOf(
        LibretroButtons.UP to Key.DirectionUp.keyCode.toInt(),
        LibretroButtons.DOWN to Key.DirectionDown.keyCode.toInt(),
        LibretroButtons.LEFT to Key.DirectionLeft.keyCode.toInt(),
        LibretroButtons.RIGHT to Key.DirectionRight.keyCode.toInt(),
        LibretroButtons.B to Key.Z.keyCode.toInt(),
        LibretroButtons.A to Key.X.keyCode.toInt(),
        LibretroButtons.Y to Key.A.keyCode.toInt(),
        LibretroButtons.X to Key.S.keyCode.toInt(),
        LibretroButtons.START to Key.Enter.keyCode.toInt(),
        LibretroButtons.SELECT to Key.ShiftRight.keyCode.toInt(),
        LibretroButtons.L to Key.Q.keyCode.toInt(),
        LibretroButtons.R to Key.W.keyCode.toInt(),
        LibretroButtons.L2 to Key.One.keyCode.toInt(),
        LibretroButtons.R2 to Key.Two.keyCode.toInt(),
    ),
)

/** WASD preset: WASD for D-pad, J/K for B/A, L/I for Y/X. */
val KEYBOARD_WASD = KeyMappingPreset(
    id = "keyboard-wasd",
    displayName = "Keyboard (WASD)",
    description = "WASD for D-pad, J/K for B/A, L/I for Y/X",
    bindings = mapOf(
        LibretroButtons.UP to Key.W.keyCode.toInt(),
        LibretroButtons.DOWN to Key.S.keyCode.toInt(),
        LibretroButtons.LEFT to Key.A.keyCode.toInt(),
        LibretroButtons.RIGHT to Key.D.keyCode.toInt(),
        LibretroButtons.B to Key.J.keyCode.toInt(),
        LibretroButtons.A to Key.K.keyCode.toInt(),
        LibretroButtons.Y to Key.L.keyCode.toInt(),
        LibretroButtons.X to Key.I.keyCode.toInt(),
        LibretroButtons.START to Key.Enter.keyCode.toInt(),
        LibretroButtons.SELECT to Key.ShiftRight.keyCode.toInt(),
        LibretroButtons.L to Key.U.keyCode.toInt(),
        LibretroButtons.R to Key.O.keyCode.toInt(),
        LibretroButtons.L2 to Key.Seven.keyCode.toInt(),
        LibretroButtons.R2 to Key.Eight.keyCode.toInt(),
    ),
)
