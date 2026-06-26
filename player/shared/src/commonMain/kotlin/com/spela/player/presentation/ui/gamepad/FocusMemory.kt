package com.spela.player.presentation.ui.gamepad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

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
 *    cleared scope), focus is requested on this element — the "sensible
 *    default focus on entry" behavior — **but only if the element is on
 *    screen at the time of firing**. Off-screen defaults would force the
 *    page to scroll on forward navigation (centerOnFocus pulls the focused
 *    element to viewport center), which is jarring when the user expects to
 *    land on a freshly-rendered page at the top. Restore (back navigation)
 *    is intentionally unaffected — the user remembers being there and
 *    expects to land back on the same element, scroll cost or no.
 *
 *    Without a default that fires, gamepad navigation still activates: the
 *    [GamepadHandler] wrapper Box self-focuses on screen mount, and the
 *    first d-pad press calls `moveFocus(Next)` to enter the content tree.
 *
 *    Only ONE element per scope should set [isDefault] = true; if multiple
 *    do, the first to fire claims focus and the rest skip.
 *
 * Call sites can pass an existing [requester] when they already manage one
 * (e.g. SpCarousel's per-item requesters used for left/right navigation).
 * Without [requester] the modifier owns its own.
 *
 * @param key Stable identifier for this element within its scope. For list
 *   items use a composite like `"$groupKey/$itemId"` so reorder is handled.
 * @param isDefault If true, this is the screen's default-focus element —
 *   it gets focus on first entry when no saved key exists AND the element
 *   is on screen.
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
    val inputModeManager = LocalInputModeManager.current

    // Window height for the viewport-visibility gate on Action.Default.
    // Read from LocalWindowInfo so it adapts to window resizes and
    // works the same on desktop / Android.
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    var elementY by remember { mutableStateOf(Float.NaN) }
    var elementHeight by remember { mutableStateOf(0) }

    // #1452: register with the enclosing verticalScroll viewport so a d-pad
    // press can re-acquire focus to a *visible* element when this one has been
    // scrolled off-screen, instead of spatially-moving from the off-screen
    // element and snap-scrolling the list back. Registration is on this
    // primitive (not on the inner focusable) because default/restore focus
    // lands on *this* node while moveFocus lands on the inner focusable — the
    // outer `onFocusChanged { hasFocus }` is the only place that sees both.
    // No-op outside SpScrollableContent (registry is null inside a LazyColumn,
    // which already disposes off-screen items). See [ScrollFocusRegistry].
    val registry = LocalScrollFocusRegistry.current
    var isFocused by remember { mutableStateOf(false) }
    if (registry != null) {
        val entry = remember(registry, fr) {
            ScrollFocusRegistry.Entry(
                requester = fr,
                topInRoot = { elementY },
                height = { elementHeight.toFloat() },
                isFocused = { isFocused },
            )
        }
        DisposableEffect(entry) {
            registry.register(entry)
            onDispose { registry.unregister(entry) }
        }
    }

    // Hybrid input mode focus restoration. On Android the framework
    // tracks a global "touch mode" — any screen touch (tap, swipe,
    // scroll) flips Compose's input mode to `InputMode.Touch` and its
    // FocusOwner explicitly drops the active focus path, because focus
    // rings are visual noise when the user is interacting directly
    // with their finger. When the user later picks up a controller or
    // presses an arrow key, the mode flips back to
    // `InputMode.Keyboard` — but the focus path is gone, so the key
    // event has no dispatch target and gamepad navigation freezes
    // until the user touches a focusable.
    //
    // We bridge that gap here: on every Touch → Keyboard transition,
    // the focusRestoreItem whose [key] matches the saved
    // `scope.value` re-acquires focus on its own requester. (For the
    // very first transition, when nothing has been focused yet,
    // `isDefault` items act as the fallback target.) `collectLatest`
    // ensures rapid mode flapping (mash-tap → mash-d-pad) cancels
    // stale restoration attempts and only the newest mode change
    // wins.
    //
    // requestFocus is wrapped in try/catch because the FocusRequester
    // may not be bound to a measured layout node if this item is
    // currently scrolled out of view in a LazyList — silent no-op is
    // the correct behavior there (the user will navigate from
    // whatever focusable IS currently visible after the mode flip).
    LaunchedEffect(Unit) {
        snapshotFlow { inputModeManager.inputMode }
            .collectLatest { mode ->
                if (mode != InputMode.Keyboard) return@collectLatest
                val shouldFire = scope.value == key ||
                    (scope.value.isEmpty() && isDefault)
                if (shouldFire) {
                    try { fr.requestFocus() } catch (_: Exception) {}
                }
            }
    }

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
            if (!stillValid) return@LaunchedEffect

            // Default-focus only fires when the target is on screen at
            // firing time. See the kdoc above for why. Action.Restore
            // skips this gate — back-nav should always land on the
            // remembered element, even if it requires scrolling.
            if (initialAction == Action.Default) {
                val measured = !elementY.isNaN()
                val topInside = measured && elementY in 0f..windowHeightPx
                val bottomInside = measured &&
                    (elementY + elementHeight) in 0f..windowHeightPx
                if (!measured || (!topInside && !bottomInside)) {
                    return@LaunchedEffect
                }
            }

            try { fr.requestFocus() } catch (_: Exception) {}
        }
    }

    this
        .focusRequester(fr)
        .onGloballyPositioned { coords ->
            // Captured for the Default-action viewport gate above. The
            // value updates every layout pass; the LaunchedEffect reads
            // whatever is current at the 120 ms firing point.
            elementY = coords.positionInRoot().y
            elementHeight = coords.size.height
        }
        .onFocusChanged { state ->
            isFocused = state.hasFocus
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
