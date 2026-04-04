package com.spela.player.presentation.ui.gamepad

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.spela.player.presentation.ui.components.LocalScrollState

/**
 * When this element gains focus, scrolls the nearest [SpScrollableContent]
 * so this element is vertically centered in the viewport.
 *
 * Uses the [interactionSource] for focus detection (same source shared
 * with `.clickable()` and `.focusable()`), so it detects focus regardless
 * of which node in the modifier chain holds it.
 *
 * Uses the [LocalScrollState] provided by [SpScrollableContent] to
 * calculate and animate the scroll position directly.
 */
fun Modifier.centerOnFocus(
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    val scrollState = LocalScrollState.current
    val isFocused by interactionSource.collectIsFocusedAsState()
    var positionInRoot = 0f
    var elementHeight = 0f

    if (scrollState == null) return@composed this

    LaunchedEffect(isFocused) {
        if (isFocused) {
            val currentScroll = scrollState.value
            val elementTop = positionInRoot + currentScroll
            val viewportHeight = scrollState.viewportSize.toFloat()

            val targetScroll = (elementTop - (viewportHeight / 2f) + (elementHeight / 2f))
                .toInt()
                .coerceIn(0, scrollState.maxValue)

            scrollState.animateScrollTo(targetScroll)
        }
    }

    this.onGloballyPositioned { coordinates ->
        positionInRoot = coordinates.positionInRoot().y
        elementHeight = coordinates.size.height.toFloat()
    }
}
