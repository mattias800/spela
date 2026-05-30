package com.spela.player.libretro

import com.spela.player.presentation.navigation.NavigationEvent
import com.spela.player.presentation.navigation.NavigationEventBus
import com.spela.player.presentation.viewmodel.LibretroButtons
import java.awt.Robot
import java.awt.event.KeyEvent

/**
 * Translates player-1 gamepad input into UI navigation when not in a game:
 * L1/R1 switch sections (via the navigation bus), the d-pad drives focus with
 * keyboard-style auto-repeat, and the face buttons confirm/back.
 *
 * Everything is emitted as synthesized key events so the existing keyboard-based
 * `GamepadHandler` is the single navigation layer in the UI (parity with how
 * Android's MainActivity feeds gamepad input into Compose).
 *
 * Kept separate from [DesktopGamepadPoller] so it has no native (JNI) dependency
 * and can be unit-tested directly with an injected key sink.
 */
class GamepadUiNavigator(
    private val navigationEventBus: NavigationEventBus?,
    /**
     * True whenever a game is open (the in-game screen/overlay is showing).
     * UI navigation is suppressed then: `GamepadHandler` is disabled in-game, so
     * synth keys can't drive menu focus and would leak into the emulator. The
     * gamepad still controls the game directly via the poller's button routing.
     */
    private val isInGame: () -> Boolean,
    /**
     * Key-event sink. Defaults to an AWT [Robot] that synthesizes real key
     * presses; tests inject a fake to assert the button -> key-code mapping and
     * auto-repeat timing without a real Robot.
     */
    private val synthesizeKey: ((Int) -> Unit)? = null,
) {
    companion object {
        /**
         * D-pad auto-repeat timing, in poll ticks (one tick per poll). Mirrors the
         * keyboard / Android feel: an initial hold delay, then a steady repeat.
         */
        private const val REPEAT_INITIAL_DELAY_TICKS = 45 // ~360ms at the 8ms poll rate
        private const val REPEAT_INTERVAL_TICKS = 8       // ~64ms between repeats
    }

    private var prevLeftShoulder = false
    private var prevRightShoulder = false
    private var prevConfirm = false
    private var prevBack = false

    /** Poll ticks each d-pad direction has been held (0 = released), for auto-repeat. */
    private val dpadHoldTicks = mutableMapOf(
        LibretroButtons.UP to 0,
        LibretroButtons.DOWN to 0,
        LibretroButtons.LEFT to 0,
        LibretroButtons.RIGHT to 0,
    )

    /** Lazily-created AWT Robot used when no [synthesizeKey] sink is injected. */
    private val defaultRobot: Robot? by lazy {
        try {
            Robot()
        } catch (e: Exception) {
            println("[GamepadUiNavigator] AWT Robot unavailable; UI gamepad navigation disabled: ${e.message}")
            null
        }
    }

    /** Processes one poll frame of controller [states] (player 1 = `states[0]`). */
    fun handle(states: Array<GamepadState>) {
        if (isInGame() || navigationEventBus == null || states.isEmpty()) {
            // Reset auto-repeat so a held direction doesn't carry into a game.
            dpadHoldTicks.keys.forEach { dpadHoldTicks[it] = 0 }
            return
        }
        val first = states[0]

        // L1/R1 -> section switching (via the navigation bus).
        // buttons is indexed by libretro button id, so use L (10) / R (11).
        val leftShoulder = first.buttons.getOrNull(LibretroButtons.L) == true
        val rightShoulder = first.buttons.getOrNull(LibretroButtons.R) == true
        if (leftShoulder && !prevLeftShoulder) {
            navigationEventBus.emit(NavigationEvent.PreviousSection)
        }
        if (rightShoulder && !prevRightShoulder) {
            navigationEventBus.emit(NavigationEvent.NextSection)
        }
        prevLeftShoulder = leftShoulder
        prevRightShoulder = rightShoulder

        // D-pad -> arrow keys with hold-to-repeat. Confirm (bottom face button =
        // RETRO_B) -> Enter and back (right face button = RETRO_A) -> Escape are
        // single-press only.
        repeatOnHold(first, LibretroButtons.UP, KeyEvent.VK_UP)
        repeatOnHold(first, LibretroButtons.DOWN, KeyEvent.VK_DOWN)
        repeatOnHold(first, LibretroButtons.LEFT, KeyEvent.VK_LEFT)
        repeatOnHold(first, LibretroButtons.RIGHT, KeyEvent.VK_RIGHT)
        prevConfirm = synthOnEdge(first, LibretroButtons.B, prevConfirm, KeyEvent.VK_ENTER)
        prevBack = synthOnEdge(first, LibretroButtons.A, prevBack, KeyEvent.VK_ESCAPE)
    }

    /**
     * Fires [keyCode] for a directional button with keyboard-style auto-repeat:
     * once on the initial press, then (after [REPEAT_INITIAL_DELAY_TICKS]) every
     * [REPEAT_INTERVAL_TICKS] ticks while held.
     */
    private fun repeatOnHold(state: GamepadState, buttonId: Int, keyCode: Int) {
        val pressed = state.buttons.getOrNull(buttonId) == true
        if (!pressed) {
            dpadHoldTicks[buttonId] = 0
            return
        }
        val held = dpadHoldTicks.getValue(buttonId)
        val fire = held == 0 ||
            (held >= REPEAT_INITIAL_DELAY_TICKS &&
                (held - REPEAT_INITIAL_DELAY_TICKS) % REPEAT_INTERVAL_TICKS == 0)
        if (fire) synthKey(keyCode)
        dpadHoldTicks[buttonId] = held + 1
    }

    /** Synthesizes [keyCode] on the rising edge of a button (press only, not held). */
    private fun synthOnEdge(state: GamepadState, buttonId: Int, prev: Boolean, keyCode: Int): Boolean {
        val pressed = state.buttons.getOrNull(buttonId) == true
        if (pressed && !prev) synthKey(keyCode)
        return pressed
    }

    /** Emits a synthesized key press+release via the injected sink or the AWT Robot. */
    private fun synthKey(keyCode: Int) {
        val sink = synthesizeKey
        if (sink != null) {
            sink(keyCode)
            return
        }
        val robot = defaultRobot ?: return
        try {
            robot.keyPress(keyCode)
            robot.keyRelease(keyCode)
        } catch (_: Exception) {
        }
    }
}
