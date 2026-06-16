package com.spela.player.presentation.ui.gamepad

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/**
 * The right analog stick's normalized vertical position (-1 fully up .. +1 fully
 * down, 0 at rest after deadzone), published by the platform input layers
 * (#1362). Continuous viewport scrolling reads this; null when no gamepad source
 * is wired (e.g. previews/tests).
 */
val LocalRightStickScroll = staticCompositionLocalOf<StateFlow<Float>?> { null }

/** Pixels scrolled per second at full stick deflection. */
private const val SCROLL_PX_PER_SECOND = 2600f

/** Below this magnitude the stick is treated as at-rest (belt-and-suspenders; the
 *  input layer already applies its own deadzone before publishing). */
private const val DEADZONE = 0.02f

/**
 * Continuously scrolls [scrollable] from the right analog stick while in gamepad
 * mode (#1362) — like a scroll wheel, independent of D-pad focus traversal. Reads
 * the latest stick value each frame and applies a frame-rate-independent delta, so
 * holding the stick scrolls smoothly and releasing it stops.
 *
 * [scrollable] is the common supertype of both `ScrollState` (verticalScroll) and
 * `LazyListState` (LazyColumn), so this drives either. Place it inside the
 * scrollable container's composition. No-op in touch mode or without a source.
 */
@Composable
fun RightStickScroll(scrollable: ScrollableState) {
    val flow = LocalRightStickScroll.current ?: return
    val gamepadMode = LocalInputMode.current == InputMode.GAMEPAD
    LaunchedEffect(scrollable, flow, gamepadMode) {
        if (!gamepadMode) return@LaunchedEffect
        // collectLatest cancels the per-deflection frame loop when the stick value
        // changes. At rest (|v| <= deadzone, the common case) we return immediately
        // and stay suspended on the flow — requesting NO frames, so Compose can go
        // idle (otherwise an always-on withFrameNanos loop hangs waitForIdle in
        // tests and renders forever on-device).
        flow.collectLatest { v ->
            if (abs(v) <= DEADZONE) return@collectLatest
            var lastFrame = 0L
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (lastFrame == 0L) 0f else (now - lastFrame) / 1_000_000_000f
                lastFrame = now
                // +v (stick down) scrolls toward the end of the content (down).
                if (dt > 0f) scrollable.scrollBy(v * SCROLL_PX_PER_SECOND * dt)
            }
        }
    }
}
