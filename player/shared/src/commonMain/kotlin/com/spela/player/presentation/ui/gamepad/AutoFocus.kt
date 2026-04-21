package com.spela.player.presentation.ui.gamepad

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
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
 * (or a bottom-nav tab switch) in gamepad mode.
 *
 * Apply to the first meaningful focusable element on each screen — the
 * element that should receive initial gamepad focus. Each screen is
 * responsible for placing this modifier; this is the primary mechanism
 * for focus acquisition on navigation.
 *
 * On back navigation, this modifier does nothing — focus-memory
 * restoration in [Modifier.rememberFocus] takes over so the user lands
 * back on the element they last focused.
 *
 * The 500ms delay ensures the AnimatedContent exit transition has
 * completed so focus doesn't land on the outgoing screen.
 */
fun Modifier.autoFocus(): Modifier = composed {
    val isForward = LocalIsForwardNavigation.current
    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD
    // Tab-switches via L1/R1 are not forward navigations (no back stack
    // entry) but also not back navigations. Treat them like forward
    // navigations for focus purposes — the destination screen has no
    // focus memory yet, so autoFocus is the only mechanism that can
    // place initial focus on a useful element.
    val isTabSwitch = LocalIsTabSwitch.current

    if ((isForward || isTabSwitch) && isGamepad) {
        val focusRequester = remember { FocusRequester() }
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

/**
 * Whether the current screen was reached via a bottom-nav tab switch
 * (L1/R1 on gamepads, or a tab tap on touch). Set by SpelaApp alongside
 * [LocalIsForwardNavigation] when rendering each screen.
 */
val LocalIsTabSwitch = compositionLocalOf { false }
