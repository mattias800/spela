package com.spela.player.presentation.ui.gamepad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
 * Remembers focus for this element across navigation.
 *
 * When this element gains focus, its [key] is saved to the nearest
 * [LocalFocusMemory]. When the screen is restored after back navigation,
 * the element whose key matches the saved value auto-focuses.
 *
 * @param key Unique identifier for this element within its focus memory scope.
 */
fun Modifier.rememberFocus(key: String): Modifier = composed {
    val focusMemory = LocalFocusMemory.current
    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    if (focusMemory == null || !isGamepad) return@composed this

    val shouldRestore = focusMemory.value == key
    val isForward = LocalIsForwardNavigation.current
    val focusRequester = remember { FocusRequester() }

    if (shouldRestore && !isForward) {
        LaunchedEffect(Unit) {
            delay(600)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    this
        .focusRequester(focusRequester)
        .onFocusChanged { state ->
            if (state.hasFocus) {
                focusMemory.value = key
            }
        }
}
