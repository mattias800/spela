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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * When this element gains focus, scrolls it toward the center of
 * the scrollable parent — both vertically and horizontally.
 *
 * Works by expanding the "bring into view" rectangle with padding
 * in all directions. The scroll system tries to show the full
 * rectangle, which overshoots and places the element near center.
 *
 * This propagates to all scrollable ancestors:
 * - Vertical padding centers within Column(verticalScroll)
 * - Horizontal padding centers within LazyRow/SpCarousel
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.centerOnFocus(): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var elementWidth = 0f
    var elementHeight = 0f

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onGloballyPositioned { coordinates ->
            elementWidth = coordinates.size.width.toFloat()
            elementHeight = coordinates.size.height.toFloat()
        }
        .onFocusChanged { state ->
            if (state.hasFocus) {
                scope.launch {
                    val verticalPadding = with(density) { 200.dp.toPx() }
                    val horizontalPadding = with(density) { 300.dp.toPx() }
                    bringIntoViewRequester.bringIntoView(
                        Rect(
                            left = -horizontalPadding,
                            top = -verticalPadding,
                            right = elementWidth + horizontalPadding,
                            bottom = elementHeight + verticalPadding,
                        )
                    )
                }
            }
        }
}
