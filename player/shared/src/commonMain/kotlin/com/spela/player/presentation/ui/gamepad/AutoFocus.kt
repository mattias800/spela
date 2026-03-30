package com.spela.player.presentation.ui.gamepad

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay

/**
 * Whether the current screen was reached via forward navigation (not back/tab switch).
 * Set by SpelaApp when rendering each screen.
 */
val LocalIsForwardNavigation = compositionLocalOf { false }

/**
 * Requests focus on this element when it first mounts during forward navigation.
 *
 * Apply to exactly one element per screen — the element that should receive
 * initial focus when navigating to the screen with a gamepad. On back
 * navigation, this modifier does nothing (scroll position and spatial focus
 * handle restoration).
 *
 * If the target is a focus group (e.g. a list), focus is moved into the
 * first focusable child automatically.
 *
 * Only activates in gamepad mode.
 */
fun Modifier.autoFocus(): Modifier = composed {
    val isForward = LocalIsForwardNavigation.current
    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD
    val focusManager = LocalFocusManager.current

    if (isForward && isGamepad) {
        val focusRequester = FocusRequester()
        LaunchedEffect(Unit) {
            delay(200)
            try {
                focusRequester.requestFocus()
                // If this is a focus group, enter the first child
                focusManager.moveFocus(FocusDirection.Enter)
            } catch (_: Exception) {}
        }
        this.focusRequester(focusRequester)
    } else {
        this
    }
}
