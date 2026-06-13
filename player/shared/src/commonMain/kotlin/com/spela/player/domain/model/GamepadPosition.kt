package com.spela.player.domain.model

import com.spela.player.presentation.viewmodel.LibretroButtons

/**
 * Canonical, brand- and device-independent physical control position on a
 * gamepad — *where you press* (SOUTH = bottom face button on any pad), as
 * distinct from the RetroPad output id — *what the core receives* (#1334).
 * Conflating the two is the trap that produced the original A/B confusion, so
 * the input position and the RetroPad output are separate types.
 *
 * **Ordinal contract (load-bearing):** the declaration order below is mirrored
 * by the desktop SDL3 bridge (`player/native/src/gamepad_sdl3.c`), which reports
 * its per-frame button array indexed by these ordinals. Reordering or inserting
 * here WITHOUT updating the C `sdl_button_to_position` table silently corrupts
 * desktop gamepad input. Keep the two in lockstep.
 */
enum class GamepadPosition {
    SOUTH,      // 0  bottom face
    EAST,       // 1  right face
    WEST,       // 2  left face
    NORTH,      // 3  top face
    DPAD_UP,    // 4
    DPAD_DOWN,  // 5
    DPAD_LEFT,  // 6
    DPAD_RIGHT, // 7
    L1,         // 8  left shoulder
    R1,         // 9  right shoulder
    L2,         // 10 left trigger (digital)
    R2,         // 11 right trigger (digital)
    L3,         // 12 left stick click
    R3,         // 13 right stick click
    START,      // 14
    SELECT,     // 15
    ;

    /**
     * Brand-neutral human label for the mapping UI. Deliberately positional
     * (no Xbox/Nintendo/PlayStation glyphs or letters) — that neutrality is the
     * whole point of the input layer (#1334).
     */
    val displayName: String
        get() = when (this) {
            SOUTH -> "Bottom button"
            EAST -> "Right button"
            WEST -> "Left button"
            NORTH -> "Top button"
            DPAD_UP -> "D-pad Up"
            DPAD_DOWN -> "D-pad Down"
            DPAD_LEFT -> "D-pad Left"
            DPAD_RIGHT -> "D-pad Right"
            L1 -> "L1"
            R1 -> "R1"
            L2 -> "L2"
            R2 -> "R2"
            L3 -> "L3 (left stick)"
            R3 -> "R3 (right stick)"
            START -> "Start"
            SELECT -> "Select"
        }
}

/**
 * The default `GamepadPosition` → RetroPad mapping. It reproduces the historical
 * fixed SDL3 desktop behavior exactly, so adopting the configurable two-layer
 * model is a no-op until the user rebinds a console. RetroPad face ids are
 * *named* positionally (B≈south, A≈east) but here they play the **output** role.
 */
object DefaultGamepadMapping {
    val POSITION_TO_RETRO: Map<GamepadPosition, Int> = mapOf(
        GamepadPosition.SOUTH to LibretroButtons.B,
        GamepadPosition.EAST to LibretroButtons.A,
        GamepadPosition.WEST to LibretroButtons.Y,
        GamepadPosition.NORTH to LibretroButtons.X,
        GamepadPosition.DPAD_UP to LibretroButtons.UP,
        GamepadPosition.DPAD_DOWN to LibretroButtons.DOWN,
        GamepadPosition.DPAD_LEFT to LibretroButtons.LEFT,
        GamepadPosition.DPAD_RIGHT to LibretroButtons.RIGHT,
        GamepadPosition.L1 to LibretroButtons.L,
        GamepadPosition.R1 to LibretroButtons.R,
        GamepadPosition.L2 to LibretroButtons.L2,
        GamepadPosition.R2 to LibretroButtons.R2,
        GamepadPosition.L3 to LibretroButtons.L3,
        GamepadPosition.R3 to LibretroButtons.R3,
        GamepadPosition.START to LibretroButtons.START,
        GamepadPosition.SELECT to LibretroButtons.SELECT,
    )
}
