package com.spela.player.libretro

import com.spela.player.presentation.viewmodel.LibretroAnalog

/**
 * Result of an analog axis update — tells the caller which stick/axis to set.
 */
data class AxisUpdate(
    val stickIndex: Int,
    val axisId: Int,
    val value: Short,
)

/**
 * Tracks which virtual analog direction keys are currently pressed and
 * computes the resulting axis values with opposing-direction cancellation.
 *
 * When both directions on the same axis are pressed, the value is 0.
 * When one direction is pressed, the value is full deflection (±32767).
 */
class AnalogAxisTracker {

    private val pressed = mutableSetOf<Int>()

    /**
     * Updates the pressed state for a button ID.
     * Returns an [AxisUpdate] if the button is an analog virtual button, or null otherwise.
     */
    fun update(buttonId: Int, isPressed: Boolean): AxisUpdate? {
        val decomposed = LibretroAnalog.decompose(buttonId) ?: return null
        val (stickIndex, axisId, _) = decomposed

        if (isPressed) {
            pressed.add(buttonId)
        } else {
            pressed.remove(buttonId)
        }

        val value = computeAxisValue(stickIndex, axisId)
        return AxisUpdate(stickIndex, axisId, value)
    }

    fun clear() {
        pressed.clear()
    }

    private fun computeAxisValue(stickIndex: Int, axisId: Int): Short {
        var value = 0
        for (id in pressed) {
            val (s, a, sign) = LibretroAnalog.decompose(id) ?: continue
            if (s == stickIndex && a == axisId) {
                value += sign
            }
        }
        // Clamp to -1..1 then scale to full deflection
        val clamped = value.coerceIn(-1, 1)
        return (clamped * 32767).toShort()
    }
}
