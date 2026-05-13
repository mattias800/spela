package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fires [onApproach] exactly once when the modified composable is
 * within [buffer] dp of the window's bottom edge — i.e. has either
 * entered the viewport or is just about to.
 *
 * Use for lazy data fetches that should only run when the user has
 * actually scrolled (or is about to scroll) the section into view —
 * the canonical example is `/api/games/{id}/similar` on the
 * game-detail screen, which sits below screenshots and isn't
 * something most users see.
 *
 * Implementation: an [onGloballyPositioned] callback reads the
 * composable's [positionInRoot] Y and compares it against the
 * window height from [LocalWindowInfo]. The callback de-dupes
 * itself with a `fired` flag so [onApproach] only ever runs once
 * per composition lifetime — re-mount (e.g. screen re-entered)
 * resets the flag.
 */
fun Modifier.onApproachingVisible(
    buffer: Dp = 200.dp,
    onApproach: () -> Unit,
): Modifier = composed {
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    val bufferPx = with(density) { buffer.toPx() }
    var fired by remember { mutableStateOf(false) }

    onGloballyPositioned { coords ->
        if (fired) return@onGloballyPositioned
        // positionInRoot is the composable's top-left in window
        // coordinates. If that y is <= viewportHeight + buffer the
        // composable is within (or just past) the visible region.
        // We compare top edge only — for "section is about to enter
        // the viewport from below" that's exactly the right signal.
        val topY = coords.positionInRoot().y
        if (topY <= windowHeightPx + bufferPx) {
            fired = true
            onApproach()
        }
    }
}
