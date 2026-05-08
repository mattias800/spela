package com.spela.player.presentation.ui.gamepad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/**
 * Holds the key of the last focused item within a focus memory scope.
 * Survives unmount/remount via [rememberSaveable].
 */
val LocalFocusMemory = compositionLocalOf<MutableState<String>?> { null }

/**
 * Holds the [com.spela.player.presentation.ui.components.SpCarousel.memoryKey]
 * of the carousel that most recently owned focus inside this scope.
 *
 * When provided, carousels gate their back-nav focus restoration on a
 * match — only the carousel that previously owned focus restores its
 * saved item. Without this, multiple carousels on the same screen each
 * try to restore independently and race; the bottom-most wins.
 */
val LocalActiveCarouselKey = compositionLocalOf<MutableState<String>?> { null }

/**
 * Creates a focus memory scope. Items inside can use [Modifier.rememberFocus]
 * to participate in focus save/restore across navigation.
 *
 * Place this around any list or container whose items should remember
 * which one had focus when the user navigates away and comes back.
 */
@Composable
fun rememberFocusMemoryState(): MutableState<String> {
    return rememberSaveable { mutableStateOf("") }
}

/**
 * Creates an active-carousel scope. Provide via [LocalActiveCarouselKey]
 * around any region with two or more `SpCarousel`s so back-nav focus
 * restoration goes to the right one instead of racing.
 */
@Composable
fun rememberActiveCarouselKeyState(): MutableState<String> {
    return rememberSaveable { mutableStateOf("") }
}

/**
 * Remembers focus for this element across navigation.
 *
 * When this element (or any descendant) gains focus, its [key] is saved
 * to the nearest [LocalFocusMemory]. When the screen is restored after
 * back navigation, the element whose key matches the saved value auto-focuses.
 *
 * Acts as a section-level fallback. If a more specific restorer (e.g.
 * [SpCarousel]'s `memoryKey`) has already placed focus inside this section
 * by the time this restorer fires, the request is skipped to avoid
 * clobbering item-level focus.
 *
 * @param key Unique identifier for this element within its focus memory scope.
 */
fun Modifier.rememberFocus(key: String): Modifier = composed {
    val focusMemory = LocalFocusMemory.current ?: return@composed this

    val shouldRestore = focusMemory.value == key
    val isForward = LocalIsForwardNavigation.current
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }

    if (shouldRestore && !isForward) {
        LaunchedEffect(Unit) {
            delay(600)
            // Skip if a finer-grained restorer (e.g. SpCarousel's per-item
            // memory) already focused something inside this section.
            if (!hasFocus) {
                try { focusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    this
        .focusRequester(focusRequester)
        .onFocusChanged { state ->
            hasFocus = state.hasFocus
            if (state.hasFocus) {
                focusMemory.value = key
            }
        }
}
