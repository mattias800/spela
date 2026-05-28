package com.spela.player.presentation.ui.gamepad

/**
 * Bridge that lets platform-level key handlers (e.g. Android
 * `MainActivity.onKeyDown`) reach into Compose to force an input-mode
 * switch.
 *
 * Why: on AYN Thor (and likely other Android handhelds with combined
 * touch + gamepad), hardware d-pad events arrive with `source=0`
 * (SOURCE_UNKNOWN). Compose's `ComposeView.dispatchKeyEvent` sees
 * these but doesn't update `inputModeManager.inputMode` to
 * `InputMode.Keyboard` for them — verified via diagnostic logging
 * (`[ImDbg]` lines are present for gamepad button A but absent for
 * d-pad). Without the mode flip, the `snapshotFlow` listener in
 * `focusRestoreItem` doesn't fire, so focus is never restored after a
 * touch interaction. This bridge lets `MainActivity` flip the mode
 * itself when it sees a hardware d-pad event, which triggers the
 * listener and restores focus.
 *
 * Single-instance assumption: there is at most one [GamepadHandler]
 * active at a time (it wraps the whole app). The active handler
 * registers itself via [DisposableEffect] and clears the slot on
 * dispose.
 */
object ComposeFocusBridge {
    /**
     * Callback set by [GamepadHandler] that flips Compose's input mode
     * to `InputMode.Keyboard`. Returns true if the mode change was
     * accepted, false if rejected by the platform.
     *
     * Null when no [GamepadHandler] is currently composed (app
     * startup before first composition completes, or test contexts
     * without the handler).
     */
    @Volatile
    var requestKeyboardMode: (() -> Boolean)? = null
}
