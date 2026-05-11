package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.ui.theme.SpColor
import kotlin.time.Clock
import kotlinx.coroutines.delay

/**
 * Delay before a screen-level loading indicator becomes visible.
 *
 * Requests that complete inside this window never paint a spinner —
 * eliminating the one-frame "flash" of loading UI on screen
 * transitions whose data is already cached / fast (most local-DB
 * reads, memoised viewmodels, network responses under ~200ms on a
 * warm route).
 *
 * 500ms is the established UX threshold below which a loading
 * indicator does more harm than good (Nielsen Norman Group, "Response
 * Times: The 3 Important Limits"). Adjust here, not at call sites.
 */
const val ScreenLoadingDebounceMs: Long = 500

/**
 * Minimum time the loading indicator stays visible once it has been
 * shown. Prevents the "appeared for a frame and disappeared" flash
 * for requests that finish just after [ScreenLoadingDebounceMs] —
 * e.g. a 600ms response would otherwise paint dots for ~100ms.
 *
 * Anchored to the typical perceptual-glance threshold: <200ms feels
 * like a glitch; ≥300ms reads as "the app was actually loading". This
 * is the second half of the canonical "debounced spinner" UX rule
 * (Nielsen Norman, "Response Times").
 */
const val MinLoadingShownMs: Long = 300

/**
 * Role component for the "screen is loading" affordance.
 *
 * Wraps the Design-layer [SpLoadingIndicator] (the look) with two
 * things every screen-level loader needs:
 *
 *   1. **Debounce.** Painted only after [ScreenLoadingDebounceMs] of
 *      continuous loading. Fast responses never flash a spinner.
 *   2. **Tests.** When [LocalAnimationsEnabled] is `false` the
 *      debounce is skipped — the indicator paints immediately so
 *      Compose UI tests don't have to await the 500ms delay.
 *
 * Positioning stays with the caller's existing layout — this
 * component swaps in to existing centering Boxes / sized containers
 * one-to-one; nothing about its rendered size or position differs
 * from a bare [SpLoadingIndicator]. The debounce is what's new.
 *
 * Use this from screens. For inline / sub-region loaders (button
 * spinners, dialog content, in-page partial reloads), use
 * [SpLoadingIndicator] directly.
 */
@Composable
fun ScreenLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null,
    color: Color = SpColor.Primary,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    var visible by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            delay(ScreenLoadingDebounceMs)
            visible = true
        }
    }
    if (!visible) return

    SpLoadingIndicator(modifier = modifier, message = message, color = color)
}

/**
 * Two-stage state machine for screen-loading affordances:
 *
 *   1. **Debounce show.** When [source] becomes true, wait [showAfter]
 *      ms before returning true. Fast responses (< [showAfter]) never
 *      flip the output.
 *   2. **Minimum show time.** Once the output is true, keep it true
 *      for at least [minShownFor] ms — even if [source] flips back to
 *      false sooner. Prevents the 50–200ms "flash" for requests that
 *      land just past the debounce gate.
 *
 * Use for any spinner-shaped UI driven by a state Boolean —
 * [PullToRefreshBox]'s `isRefreshing`, or the parent conditional that
 * decides whether to mount [ScreenLoadingIndicator]. Both surfaces
 * benefit from the full state machine; without it, the indicator
 * appears for "a frame or so" before unmounting on responses in the
 * 500–800ms window.
 *
 * In test mode ([LocalAnimationsEnabled] false) the value passes
 * through unchanged so tests don't have to wait the timings.
 */
@Composable
fun rememberLoadingFlashDebounce(
    source: Boolean,
    showAfter: Long = ScreenLoadingDebounceMs,
    minShownFor: Long = MinLoadingShownMs,
): Boolean {
    if (!LocalAnimationsEnabled.current) return source
    var visible by remember { mutableStateOf(false) }
    var shownAtMs by remember { mutableStateOf(0L) }
    LaunchedEffect(source) {
        if (source) {
            // Already visible? Nothing to do — keep showing.
            if (!visible) {
                delay(showAfter)
                shownAtMs = Clock.System.now().toEpochMilliseconds()
                visible = true
            }
        } else {
            // Source went false. If we've started showing, respect
            // the minimum-shown-for window before hiding.
            if (visible) {
                val elapsed = Clock.System.now().toEpochMilliseconds() - shownAtMs
                val remaining = minShownFor - elapsed
                if (remaining > 0) delay(remaining)
                visible = false
            }
        }
    }
    return visible
}
