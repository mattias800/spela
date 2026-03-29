package com.spela.player.presentation.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import com.spela.player.presentation.ui.theme.SpSpacing
import kotlinx.coroutines.launch

/**
 * Horizontal carousel with built-in focus restoration and wrap-around
 * navigation for gamepad.
 *
 * - Focus restoration: when focus leaves (d-pad down) and returns (d-pad up),
 *   the previously focused item is re-focused.
 * - Wrap-around: d-pad left on the first item wraps to the last item,
 *   and d-pad right on the last item wraps to the first.
 *
 * Drop-in replacement for LazyRow in any section that contains focusable items.
 */
@Composable
fun SpCarousel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(SpSpacing.Medium),
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    LazyRow(
        state = listState,
        modifier = modifier
            .focusGroup()
            .focusRestorer()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        // If moveFocus fails, we're at the start — wrap to end
                        if (!focusManager.moveFocus(FocusDirection.Left)) {
                            val lastIndex = listState.layoutInfo.totalItemsCount - 1
                            if (lastIndex >= 0) {
                                scope.launch {
                                    listState.scrollToItem(lastIndex)
                                    focusManager.moveFocus(FocusDirection.Left)
                                }
                            }
                            true
                        } else true
                    }
                    Key.DirectionRight -> {
                        // If moveFocus fails, we're at the end — wrap to start
                        if (!focusManager.moveFocus(FocusDirection.Right)) {
                            scope.launch {
                                listState.scrollToItem(0)
                                focusManager.moveFocus(FocusDirection.Right)
                            }
                            true
                        } else true
                    }
                    else -> false
                }
            },
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}
