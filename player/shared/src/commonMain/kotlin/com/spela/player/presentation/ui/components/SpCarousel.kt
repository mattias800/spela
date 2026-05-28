package com.spela.player.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import kotlinx.coroutines.launch
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
    //
    // Sequence:
    //   1. requestFocus on the target's requester. For the common
    //      adjacent-step case (d-pad right/left from a visible item)
    //      the target sits in LazyRow's prefetch window, so its
    //      requester is bound to a layout node and this works
    //      synchronously — the focus ring snaps before any scroll
    //      animation starts.
    //   2. animateScrollBy enough to bring the focused item's centre
    //      to the centre of the viewport. We read the item's offset
    //      and size from layoutInfo.visibleItemsInfo, which is the
    //      LazyRow equivalent of the old onGloballyPositioned tracking.
    //   3. Slow path for off-screen jumps (focus restoration uses its
    //      own LaunchedEffect above; this only fires when the key
    //      handler itself targets something not yet composed): scroll
    //      it in first, yield a frame, then requestFocus.
    //
    // Previously this LaunchedEffect did scrollToItem THEN requestFocus
    // — the user saw the list pan first, then the focus ring catch up,
    // and the focused item always settled on the viewport's left edge
    // because scrollToItem(i) anchors to viewport start. Fixed in
    // #1180 follow-up to #1168.
    LaunchedEffect(pendingTarget) {
        val target = pendingTarget
        if (target < 0) return@LaunchedEffect
        val now = System.currentTimeMillis()
        // Three-tier classifier keyed on the time gap since the last
        // d-pad press. The user's intent is the signal: a single
        // deliberate press gets the full spring; a few quick taps get
        // a snappy linear tween; a held d-pad fires events too fast to
        // animate at all, so we snap. Thresholds are tuned for typical
        // gamepad / keyboard cadence (≈150–250ms held, ≈300–500ms
        // deliberate).
        val gap = now - lastFocusChangeTime
        lastFocusChangeTime = now
        val mode = when {
            gap < 100  -> ScrollMode.Snap
            gap < 250  -> ScrollMode.Fast
            else       -> ScrollMode.Slow
        }

        // Centring math. For animateScrollToItem (Snap + Slow) we need
        // a scrollOffset such that the item lands at viewport centre:
        // scrollOffset = -((viewportSize - itemSize) / 2). Per Compose
        // convention positive scrollOffset shifts the item further
        // toward viewport start; negative shifts it further from start,
        // which is what we want for centring.
        //
        // For Fast we use animateScrollBy(delta, tween) — animateScroll-
        // ToItem doesn't expose an AnimationSpec parameter, and we need
        // one to dial the duration down to ~120 ms. The delta math is
        // the natural sibling: delta = itemCenter - viewportCenter,
        // where positive delta scrolls forward (items move left in
        // viewport, item shifts toward centre when it was to the
        // right). scrollBy auto-clamps at the list ends, same as
        // scrollToItem does.
        val info = listState.layoutInfo
        val viewportSize = info.viewportEndOffset - info.viewportStartOffset
        val targetItem = info.visibleItemsInfo.firstOrNull { it.index == target }
        val itemSize = targetItem?.size
            ?: info.visibleItemsInfo.firstOrNull()?.size
            ?: 0
        val centerOffset = if (itemSize in 1..<viewportSize) {
            -((viewportSize - itemSize) / 2)
        } else 0
        val targetVisible = targetItem != null

        // Goal: focus ring snaps INSTANTLY to the new item, AND the
        // carousel animates to centre it.
        //
        // Naive `requestFocus` then `animateScrollToItem` doesn't work
        // on Android. The focusable's automatic bring-into-view scroll
        // fires after the focus change and competes with our centring
        // scroll for the LazyList scroll mutex. Both use the default
        // mutate priority and bring-into-view often wins, edge-aligning
        // the item instead of centring it.
        //
        // Fix: run our centring scroll inside `listState.scroll(
        // MutatePriority.UserInput) { ... }` and animate within the
        // block via an Animatable. `UserInput` priority preempts any
        // in-flight `Default`-priority scroll (which is what
        // bring-into-view uses), so even if bring-into-view managed to
        // grab the mutex first, our scroll cancels it the moment we
        // start. Crucially, we kick off the scroll first via
        // `launch { }` so it grabs the mutex before `requestFocus`
        // triggers bring-into-view; then the focus ring snaps
        // immediately while the scroll continues to centre.
        //
        // Off-screen targets (`targetVisible == false`) skip the
        // centring delta — we don't know the position yet — and use
        // animateScrollToItem instead. focus still goes through the
        // same launch + yield path so it's instant there too.
        val scrollJob = launch {
            listState.scroll(MutatePriority.UserInput) {
                val animatable = Animatable(0f)
                var lastValue = 0f
                if (targetVisible) {
                    val delta = centerDelta(info, target) ?: 0f
                    when (mode) {
                        ScrollMode.Snap -> scrollBy(delta)
                        ScrollMode.Fast -> animatable.animateTo(
                            delta,
                            tween(120, easing = LinearEasing),
                        ) {
                            scrollBy(value - lastValue)
                            lastValue = value
                        }
                        ScrollMode.Slow -> animatable.animateTo(delta) {
                            scrollBy(value - lastValue)
                            lastValue = value
                        }
                    }
                } else {
                    // Target not in prefetch window — we can't compute a
                    // centring delta yet. Fall back to animating toward
                    // the item, which composes it; once on-screen the
                    // user's next press will pick up the centring path.
                    // Plain animateScrollToItem can't run inside this
                    // scroll block (it would re-acquire the mutex), so
                    // we approximate by scrolling by a viewport-sized
                    // chunk in the right direction.
                    val direction = if (target > focusCursor[0]) 1 else -1
                    val chunk = (viewportSize * 0.8f) * direction
                    animatable.animateTo(chunk) {
                        scrollBy(value - lastValue)
                        lastValue = value
                    }
                }
            }
        }
        kotlinx.coroutines.yield()
        try { requesters[target].requestFocus() } catch (_: Exception) {}
        scrollJob.join()
    }

    // Focus restoration: if [LocalFocusMemory]'s saved key belongs to
    // one of this carousel's items AT SCREEN ENTRY, scroll the LazyRow
    // so the item composes. The 120 ms layout-settle delay in
    // focusRestoreItem then fires after composition catches up, and
    // requestFocus binds the requester.
    //
    // The saved key is captured ONCE at first composition. We must NOT
    // re-fire this effect when [focusMemory.value] changes during the
    // session — focusRestoreItem writes the scope on every focus
    // change, and re-firing here would race against the d-pad
    // keyhandler's animated centring scroll, clobbering it with an
    // instant scrollToItem-to-viewport-start. The previous version
    // keyed on `focusMemory.value` and was the cause of "carousel
    // never animates + focused item always anchored to left edge".
    val focusMemory = LocalFocusMemory.current
    val savedKeyAtEntry = remember { focusMemory?.value }
    LaunchedEffect(itemCount, memoryKey) {
        val savedKey = savedKeyAtEntry
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
        // Pre-compute keys so we can verify uniqueness — duplicate
        // keys crash LazyRow with "Key '<x>' was already used". Most
        // shelf data is naturally unique-by-id but social shelves
        // ("recently reviewed" etc.) can show the same game multiple
        // times. When we detect a collision we fall back to positional
        // keys (null), which loses cross-recomposition item identity
        // but keeps the screen alive. The call site should fix its
        // itemKey to be properly unique (composite); the fallback is
        // just a "don't crash production" safety net.
        val keys: List<Any>? = if (itemKey != null) {
            val list = ArrayList<Any>(itemCount)
            for (i in 0 until itemCount) list.add(itemKey(i))
            if (list.toSet().size == list.size) list else null
        } else null

        items(
            count = itemCount,
            key = if (keys != null) { i -> keys[i] } else null,
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

/**
 * Scroll animation tier picked by the gap since the last d-pad press.
 *
 * - [Slow] — single deliberate press (>250 ms gap). Uses the default
 *   spring animation via `animateScrollToItem`.
 * - [Fast] — a few quick taps (100–250 ms gap). Uses `animateScrollBy`
 *   with a 120 ms linear tween for a snappy, businesslike feel.
 * - [Snap] — held d-pad / repeating keys (<100 ms gap). No animation
 *   at all; each press jumps instantly to the centred target.
 */
private enum class ScrollMode { Slow, Fast, Snap }

/**
 * The horizontal `scrollBy` delta that would centre the item at
 * [targetIndex] in the LazyRow's viewport, or null when the item isn't
 * currently measured (typically: outside the prefetch window).
 *
 * Positive delta scrolls the list forward (items move left in
 * viewport) — appropriate when the item is to the right of viewport
 * centre. Negative delta scrolls backward. `LazyListState.scrollBy`
 * auto-clamps at list ends, so we don't need to special-case the
 * leftmost / rightmost items.
 */
private fun centerDelta(
    info: androidx.compose.foundation.lazy.LazyListLayoutInfo,
    targetIndex: Int,
): Float? {
    val item = info.visibleItemsInfo.firstOrNull { it.index == targetIndex } ?: return null
    val viewportSize = info.viewportEndOffset - info.viewportStartOffset
    if (viewportSize <= 0) return null
    val itemCenter = item.offset + item.size / 2f
    val viewportCenter = info.viewportStartOffset + viewportSize / 2f
    return itemCenter - viewportCenter
}


