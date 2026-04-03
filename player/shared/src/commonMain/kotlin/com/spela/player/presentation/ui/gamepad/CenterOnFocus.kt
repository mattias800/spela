package com.spela.player.presentation.ui.gamepad

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * When this element gains focus, scrolls it toward the vertical center
 * of the scrollable parent rather than the edge.
 *
 * Works by expanding the "bring into view" rectangle with padding above
 * and below, which tricks the scroll system into scrolling further than
 * it normally would.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.centerOnFocus(): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    if (!isGamepad) return@composed this

    var elementHeight = 0f

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onGloballyPositioned { coordinates ->
            elementHeight = coordinates.size.height.toFloat()
        }
        .onFocusChanged { state ->
            if (state.hasFocus) {
                scope.launch {
                    // Request a tall rectangle centered on the element.
                    // The extra padding makes the scroll system overshoot,
                    // placing the element near the vertical center.
                    val verticalPadding = with(density) { 200.dp.toPx() }
                    bringIntoViewRequester.bringIntoView(
                        Rect(
                            left = 0f,
                            top = -verticalPadding,
                            right = 0f,
                            bottom = elementHeight + verticalPadding,
                        )
                    )
                }
            }
        }
}
