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
 * Debounces a transient-true Boolean so consumers see `true` only
 * after [delayMs] of continuous truthy input. Reverts to `false`
 * immediately when the source flips back.
 *
 * Use for other loading-state surfaces that bypass
 * [ScreenLoadingIndicator] — most often [PullToRefreshBox]'s
 * `isRefreshing` flag, which has its own spinner that draws around
 * the screen content. Without this, a cache-hit response transitioning
 * through a momentary `isLoading=true` state would still flash the
 * pull-to-refresh spinner even though [ScreenLoadingIndicator]
 * correctly stayed hidden.
 *
 * In test mode ([LocalAnimationsEnabled] false) the value passes
 * through unchanged so tests don't have to wait 500ms.
 */
@Composable
fun rememberLoadingFlashDebounce(
    source: Boolean,
    delayMs: Long = ScreenLoadingDebounceMs,
): Boolean {
    if (!LocalAnimationsEnabled.current) return source
    var debounced by remember { mutableStateOf(false) }
    LaunchedEffect(source) {
        if (source) {
            delay(delayMs)
            debounced = true
        } else {
            debounced = false
        }
    }
    return debounced
}
