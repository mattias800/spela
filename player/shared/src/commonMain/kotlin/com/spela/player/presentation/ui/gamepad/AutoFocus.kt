package com.spela.player.presentation.ui.gamepad

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay

/**
 * Whether the current screen was reached via forward navigation (not back/tab switch).
 * Set by SpelaApp when rendering each screen.
 */
val LocalIsForwardNavigation = compositionLocalOf { false }

/**
 * Requests focus on this element when it mounts during forward navigation
 * in gamepad mode.
 *
 * Apply to the first meaningful focusable element on each screen — the
 * element that should receive initial gamepad focus. Each screen is
 * responsible for placing this modifier; this is the primary mechanism
 * for focus acquisition on navigation.
 *
 * On back navigation, this modifier does nothing — the GamepadHandler
 * Box retains focus and the first d-pad press enters content near the
 * restored scroll position.
 *
 * The 500ms delay ensures the AnimatedContent exit transition has
 * completed so focus doesn't land on the outgoing screen.
 */
fun Modifier.autoFocus(): Modifier = composed {
    val isForward = LocalIsForwardNavigation.current
    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    if (isForward && isGamepad) {
        val focusRequester = FocusRequester()
        LaunchedEffect(Unit) {
            // Wait for AnimatedContent exit transition to complete
            delay(500)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
        this.focusRequester(focusRequester)
    } else {
        this
    }
}
