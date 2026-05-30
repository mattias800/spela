package com.spela.player.libretro

/**
 * Lets Compose tell synthesized gamepad key events apart from real keyboard
 * input. [GamepadUiNavigator] calls [mark] right before it injects a synthetic
 * key; [wasRecent] stays true for a short window afterward. The desktop
 * `InputModeClassifier` uses this so gamepad navigation keeps the app in GAMEPAD
 * input mode while genuine keyboard input falls through to TOUCH.
 */
object DesktopGamepadSynth {
    /** How long after a synth a key event is still attributed to the gamepad. */
    private const val WINDOW_NANOS = 120_000_000L // 120 ms

    @Volatile
    private var lastSynthNanos = 0L

    fun mark() {
        lastSynthNanos = System.nanoTime()
    }

    fun wasRecent(): Boolean = (System.nanoTime() - lastSynthNanos) < WINDOW_NANOS
}
