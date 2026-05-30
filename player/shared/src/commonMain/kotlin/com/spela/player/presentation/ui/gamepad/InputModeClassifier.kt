package com.spela.player.presentation.ui.gamepad

/**
 * Decides whether a keyboard-style navigation event (arrow keys, Enter, Escape,
 * etc. reaching [GamepadHandler]) should count as **gamepad** input or as
 * **real keyboard** input — which in turn drives [InputMode].
 *
 * This matters only on desktop, where gamepad navigation is implemented as
 * *synthesized* key events (see desktop `GamepadUiNavigator`): a real keyboard
 * press and a gamepad press are otherwise indistinguishable at the Compose
 * layer. Desktop installs a classifier that returns true only when the event
 * was just synthesized by the gamepad poller.
 *
 * Default is `true` (treat keyboard nav as gamepad), which preserves the
 * Android behavior where these events genuinely originate from the controller.
 */
object InputModeClassifier {
    @Volatile
    var isKeyboardNavFromGamepad: () -> Boolean = { true }
}
