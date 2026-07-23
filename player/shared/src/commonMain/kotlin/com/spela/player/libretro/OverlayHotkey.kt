package com.spela.player.libretro

import com.spela.player.domain.model.GamepadPosition

/**
 * The gamepad hotkey that opens the in-game overlay: Select + Start held
 * together (#1682).
 *
 * Without it there is no gamepad route into the overlay — desktop has no
 * system back button and Escape needs a keyboard, so Big Picture / Steam Deck
 * players could not pause, save or exit a running game. RetroArch uses the
 * same combo, so the muscle memory already exists.
 *
 * Detection is positional ([GamepadPosition]) rather than by raw key code, so
 * it follows the user's calibrated input layer on every controller style.
 */
object OverlayHotkey {
    /**
     * How long the combo must be held before it fires. The delay is what makes
     * the hotkey safe on the handful of games that use Select+Start together
     * (soft reset): a quick simultaneous tap still reaches the core.
     */
    const val HOLD_MILLIS = 300L

    val POSITIONS = setOf(GamepadPosition.SELECT, GamepadPosition.START)

    fun isCombo(pressed: Set<GamepadPosition>): Boolean = POSITIONS.all { it in pressed }
}

/**
 * Edge detector for the [OverlayHotkey] combo, driven by the desktop poller
 * (one instance per controller). Kept free of platform APIs so the hold
 * semantics are unit-testable with injected timestamps.
 */
class OverlayHotkeyDetector(private val holdMillis: Long = OverlayHotkey.HOLD_MILLIS) {

    private var heldSinceMillis: Long? = null
    private var latched = false

    /**
     * True once the combo has fired and while it is still held. Callers mask
     * these buttons from the emulation core so the game never sees a stuck
     * Select+Start while the overlay is open.
     */
    val isLatched: Boolean get() = latched

    /**
     * Feeds one poll frame. Returns true exactly once per hold — on the frame
     * the hold threshold is crossed — and false otherwise.
     */
    fun update(nowMillis: Long, comboHeld: Boolean): Boolean {
        if (!comboHeld) {
            reset()
            return false
        }
        val since = heldSinceMillis ?: nowMillis.also { heldSinceMillis = it }
        if (latched || nowMillis - since < holdMillis) return false
        latched = true
        return true
    }

    fun reset() {
        heldSinceMillis = null
        latched = false
    }
}
