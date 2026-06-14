package com.spela.player.libretro

import com.spela.player.domain.model.GamepadPosition

/**
 * Pure resolution of the two-layer gamepad model's mapping step: given which
 * canonical [GamepadPosition]s are currently pressed (the input layer) and a
 * `GamepadPosition` → RetroPad-id mapping (the mapping layer), produce the
 * per-RetroPad-button pressed state to feed the core.
 *
 * **Fan-in safe:** if several positions map to the same RetroPad id, the result
 * is their OR — a press on any contributing position wins, and releasing one
 * mapped position never clobbers another's press. (A naive per-position
 * `setButton` would let a released position turn off a button another position
 * is still holding.)
 */
object GamepadButtonResolver {
    /** RetroPad face/dpad/shoulder/stick button count (ids 0..15). */
    const val RETRO_BUTTON_COUNT = 16

    /**
     * @param positionPressed pressed state indexed by [GamepadPosition.ordinal]
     * @param mapping position → RetroPad id (e.g. an effective console mapping)
     * @return BooleanArray of size [RETRO_BUTTON_COUNT]; index = RetroPad id
     */
    fun resolve(positionPressed: BooleanArray, mapping: Map<GamepadPosition, Int>): BooleanArray {
        val out = BooleanArray(RETRO_BUTTON_COUNT)
        for (position in GamepadPosition.entries) {
            if (positionPressed.getOrNull(position.ordinal) != true) continue
            val retroId = mapping[position] ?: continue
            if (retroId in 0 until RETRO_BUTTON_COUNT) out[retroId] = true
        }
        return out
    }
}
