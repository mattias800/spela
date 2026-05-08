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
 * Holds the key of the last-focused element within a focus-memory scope.
 *
 * Provided once per screen via [rememberFocusMemoryState] and consumed by
 * leaf elements through [Modifier.focusRestoreItem]. The value survives
 * screen disposal/restore via [rememberSaveable], so back navigation lands
 * on the same element the user last focused.
 */
val LocalFocusMemory = compositionLocalOf<MutableState<String>?> { null }

/**
 * Creates a focus-memory scope. Provide via [LocalFocusMemory] at the root
 * of any screen whose focusable elements opt into [Modifier.focusRestoreItem].
 *
 * One scope per screen is the intended granularity — there's a single saved
 * key per scope, so the most recently focused element wins on restore.
 * Multiple carousels, grids, or buttons on the same screen all share the
 * same scope without racing.
 */
@Composable
fun rememberFocusMemoryState(): MutableState<String> {
    return rememberSaveable { mutableStateOf("") }
}

/**
 * The universal focus-restoration primitive. Apply to any focusable element
 * (or container that has focusable descendants) that should participate in
 * screen-scoped focus memory.
 *
 * Behavior:
 *  - When this element (or any descendant) gains focus, [key] is saved to
 *    the enclosing [LocalFocusMemory].
 *  - On back/tab-switch entry, if the saved key matches [key], focus is
 *    requested on this element after a brief layout-settle delay.
 *  - When [isDefault] is true and the saved key is empty (first visit, or
 *    cleared scope), focus is requested on this element instead — the
 *    "sensible default focus on entry" behavior. Only ONE element per
 *    scope should set [isDefault] = true; if multiple do, the first to
 *    fire claims focus and the rest skip.
 *
 * Call sites can pass an existing [requester] when they already manage one
 * (e.g. SpCarousel's per-item requesters used for left/right navigation).
 * Without [requester] the modifier owns its own.
 *
 * @param key Stable identifier for this element within its scope. For list
 *   items use a composite like `"$groupKey/$itemId"` so reorder is handled.
 * @param isDefault If true, this is the screen's default-focus element —
 *   it gets focus on first entry when no saved key exists.
 * @param requester Optional shared [FocusRequester]. If null, one is created.
 */
fun Modifier.focusRestoreItem(
    key: String,
    isDefault: Boolean = false,
    requester: FocusRequester? = null,
): Modifier = composed {
    val scope = LocalFocusMemory.current ?: return@composed this
    val isForward = LocalIsForwardNavigation.current
    val fr = requester ?: remember { FocusRequester() }

    // Capture the firing decision exactly once at first composition. We must
    // NOT re-evaluate later — if `isForward` flips during a back transition
    // (the screen is sliding out while a new screen slides in), an
    // already-fired element would otherwise satisfy `shouldRestore` again,
    // re-enter composition with a fresh LaunchedEffect, and steal focus
    // back from the destination screen. Once is enough.
    val initialAction = remember {
        when {
            scope.value == key && !isForward -> Action.Restore
            scope.value.isEmpty() && isDefault -> Action.Default
            else -> Action.None
        }
    }

    if (initialAction != Action.None) {
        LaunchedEffect(Unit) {
            // Brief delay so the FocusRequester is bound to a measured layout.
            delay(120)
            // Re-validate at firing time: a faster sibling or the user's
            // own action may have already changed scope; don't clobber.
            val current = scope.value
            val stillValid = when (initialAction) {
                Action.Restore -> current == key
                Action.Default -> current.isEmpty()
                Action.None -> false
            }
            if (stillValid) {
                try { fr.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    this
        .focusRequester(fr)
        .onFocusChanged { state ->
            if (state.hasFocus) {
                scope.value = key
            }
        }
}

private enum class Action { None, Restore, Default }

// ── Legacy section-level API ──────────────────────────────────────────────
// `Modifier.rememberFocus(key)` was the original, pre-leaf-modifier mechanism
// that lived on the section's outer container. It's kept for existing call
// sites but is restoration-only: it does NOT save its key to the scope when
// a descendant gains focus. Saving is the job of leaf [focusRestoreItem]
// callers, which fire first (inner-to-outer) and write more specific keys.
// Without this asymmetry, the section's onFocusChanged would clobber the
// item-level key whenever any descendant focused.

/**
 * Section-level fallback restorer. Apply to a section's outer container; on
 * back navigation, if [key] matches the saved scope value, focus is requested
 * on this container (which propagates to the first focusable descendant).
 *
 * Restoration-only: does not save to the scope. Use [Modifier.focusRestoreItem]
 * on individual focusable elements (or via SpCarousel's memoryKey) to record
 * which item should be restored.
 */
fun Modifier.rememberFocus(key: String): Modifier = composed {
    val scope = LocalFocusMemory.current ?: return@composed this
    val isForward = LocalIsForwardNavigation.current
    val focusRequester = remember { FocusRequester() }

    val shouldRestore = scope.value == key && !isForward
    if (shouldRestore) {
        LaunchedEffect(Unit) {
            delay(120)
            if (scope.value == key) {
                try { focusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    this.focusRequester(focusRequester)
}
