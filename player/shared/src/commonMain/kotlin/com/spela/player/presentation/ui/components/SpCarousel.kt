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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import com.spela.player.presentation.ui.theme.SpSpacing
import kotlinx.coroutines.launch

/**
 * Horizontal carousel with gamepad navigation:
 *
 * - **Left/right**: navigates between items. Wraps around at edges.
 * - **Up/down**: always exits the carousel to the previous/next section.
 *   Never moves to a neighbor card in the same row — prevents the
 *   confusing jump to a taller card.
 *
 * Combined with [centerOnFocus] on each card, the focused item is
 * always horizontally centered. When returning to a carousel via
 * spatial focus (up/down), the visually closest item is naturally
 * the one that was previously centered — no explicit focus memory needed.
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
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
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
