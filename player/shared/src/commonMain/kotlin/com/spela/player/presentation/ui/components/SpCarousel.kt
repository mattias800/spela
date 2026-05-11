package com.spela.player.presentation.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Horizontal carousel with explicit per-item focus management and
 * horizontal centering.
 *
 * Built on `LazyRow` — at first paint only the visible window + a small
 * prefetch buffer are composed, even if [itemCount] is large. This is
 * the load-shaping pivot from #1168: the screen-level outer column is
 * still eager (which the user prefers — no vertical scroll freeze), but
 * each shelf's horizontal axis is lazy, so a cold-paint of a 19-section
 * Explore page composes ~5 cards per shelf instead of ~10–20 cards per
 * shelf as before.
 *
 * - Left / Right keys move the focused index by one; we
 *   [LazyListState.animateScrollToItem] the target so it composes, then
 *   request focus once it's in the viewport.
 * - Stops at first and last item.
 * - Focus restoration: if [LocalFocusMemory]'s saved key matches one of
 *   this carousel's items at entry time, the list is scrolled to that
 *   index BEFORE [focusRestoreItem]'s ~120 ms layout-settle delay fires,
 *   so the saved item has been composed and its FocusRequester is bound
 *   when `requestFocus` is finally called.
 * - Each item delegates focus management to [focusRestoreItem]; no
 *   special-casing on top.
 *
 * @param memoryKey Optional unique group key. When set together with
 *   [itemKey], each item participates in screen-scoped focus restoration
 *   via `focusRestoreItem("$memoryKey/$itemKey")`. The enclosing screen
 *   must provide [LocalFocusMemory] for restoration to take effect.
 * @param itemKey Optional stable per-item key (e.g. game id). Combined
 *   with [memoryKey] into the per-item focus-restore key so the saved
 *   item can be matched even if the underlying list reorders.
 * @param isDefaultFocusGroup When true, item 0 of this carousel is the
 *   screen's default focus on first entry (when nothing else is saved).
 *   Apply to the *first* meaningful carousel on a screen — never more
 *   than one per screen.
 */
@Composable
fun SpCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    memoryKey: String? = null,
    itemKey: ((index: Int) -> String)? = null,
    isDefaultFocusGroup: Boolean = false,
    content: @Composable (index: Int, focusRequester: FocusRequester) -> Unit,
) {
    val listState = rememberLazyListState()
    // FocusRequesters live in a list keyed by index. Only the requester
    // for a *currently composed* item is bound to a layout node — the
    // rest are unbound and a no-op until the LazyRow composes their
    // item. We re-allocate when itemCount changes.
    val requesters = remember(itemCount) { List(itemCount) { FocusRequester() } }
    // Non-reactive cursor for the focus position. The key handler reads
    // and writes this synchronously per press — using a Compose state
    // would route the write through snapshot machinery, and a burst of
    // presses inside a single performKeyInput block (or a real-device
    // held d-pad firing events at >100 Hz) would all read the same
    // stale value because the snapshot hasn't committed between events.
    // [onFocusChanged] writes the same cursor when actual focus lands,
    // keeping it in sync if focus moved without a key event (mouse
    // click, focus restoration, etc.).
    val focusCursor = remember { intArrayOf(0) }

    // The latest "please move focus here" request from the key handler.
    // Driven via state (not a coroutine.launch per key) so a burst of
    // key presses naturally coalesces: each new value cancels the
    // previous LaunchedEffect, and only the final target's scroll +
    // requestFocus actually runs to completion. -1 = no pending target.
    var pendingTarget by remember { mutableIntStateOf(-1) }

    // Throttle window for "rapid key repeat" — when the user holds the
    // d-pad and we get many keypresses in fast succession, skip the
    // smooth scroll and snap. Otherwise the scroll animation queues up
    // and feels laggy.
    var lastFocusChangeTime by remember { mutableLongStateOf(0L) }

    // Apply the latest pending target. Keyed on pendingTarget so a
    // mid-flight scroll is cancelled the moment the user advances
    // further along the carousel.
    LaunchedEffect(pendingTarget) {
        val target = pendingTarget
        if (target < 0) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val isRapid = now - lastFocusChangeTime < 100
        lastFocusChangeTime = now
        if (isRapid) {
            listState.scrollToItem(target)
        } else {
            listState.animateScrollToItem(target)
        }
        kotlinx.coroutines.yield()
        try { requesters[target].requestFocus() } catch (_: Exception) {}
    }

    // Focus restoration: if the saved focus key belongs to one of this
    // carousel's items, scroll the LazyRow so the item composes. The
    // 120 ms layout-settle delay in focusRestoreItem then fires after
    // composition has caught up, and requestFocus binds the requester.
    //
    // [itemKey] is intentionally NOT in the key list — it's a lambda
    // and lambdas compare by referential equality, so a fresh instance
    // per recomposition would cancel + restart the effect on every
    // frame, abort the in-flight `scrollToItem`, and leave the saved
    // item uncomposed when focusRestoreItem's 120 ms timer fires.
    val focusMemory = LocalFocusMemory.current
    LaunchedEffect(focusMemory?.value, itemCount, memoryKey) {
        val savedKey = focusMemory?.value
        val keyFn = itemKey
        if (
            savedKey.isNullOrEmpty() ||
            memoryKey == null ||
            keyFn == null ||
            itemCount <= 0
        ) {
            return@LaunchedEffect
        }
        val prefix = "$memoryKey/"
        if (!savedKey.startsWith(prefix)) return@LaunchedEffect
        val target = savedKey.removePrefix(prefix)
        val targetIndex = (0 until itemCount).firstOrNull { keyFn(it) == target }
            ?: return@LaunchedEffect
        // scrollToItem (not animate) — we want this complete by the
        // time focusRestoreItem's 120 ms timer expires.
        listState.scrollToItem(targetIndex)
        focusCursor[0] = targetIndex
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> +1
                    else -> return@onPreviewKeyEvent false
                }
                val target = focusCursor[0] + delta
                if (target < 0 || target >= itemCount) return@onPreviewKeyEvent true
                focusCursor[0] = target
                pendingTarget = target
                true
            },
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(
            count = itemCount,
            key = if (itemKey != null) { i -> itemKey(i) } else null,
        ) { i ->
            val restoreKey = if (memoryKey != null && itemKey != null) {
                "$memoryKey/${itemKey(i)}"
            } else null

            Box(
                modifier = Modifier
                    .onFocusChanged { state ->
                        if (state.hasFocus) {
                            focusCursor[0] = i
                        }
                    }
                    .let { base ->
                        if (restoreKey != null) {
                            // The per-item FocusRequester is attached by the
                            // content lambda below to the inner focusable
                            // target. focusRestoreItem owns its own requester
                            // on this outer Box; calling requestFocus there
                            // propagates down to the first focusable
                            // descendant (the card). Don't share requesters[i]
                            // here, which would attach the same requester to
                            // two layout nodes and break the propagation.
                            base.focusRestoreItem(
                                key = restoreKey,
                                isDefault = isDefaultFocusGroup && i == 0,
                            )
                        } else {
                            base
                        }
                    },
            ) {
                content(i, requesters[i])
            }
        }
    }
}

