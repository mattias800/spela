package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.gamepad.LazyGridCenterInfo
import com.spela.player.presentation.ui.gamepad.LocalLazyGridCenterInfo
import com.spela.player.presentation.ui.gamepad.RightStickScroll

/**
 * Drop-in replacement for [LazyVerticalGrid] that provides
 * [LocalLazyGridCenterInfo] so [centerOnFocus] can scroll the
 * focused element to the viewport center during gamepad navigation.
 *
 * Use this wherever a [LazyVerticalGrid] contains focusable items
 * (game cards, challenge cards, etc.).
 */
@Composable
fun SpLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit,
) {
    val centerInfo = remember(state) { LazyGridCenterInfo(state) }
    CompositionLocalProvider(LocalLazyGridCenterInfo provides centerInfo) {
        // Continuous right-stick scrolling in gamepad mode (#1362) — wired here so
        // every game grid scrolls from the stick, not just SpScreen's column-scroll.
        // No-op in touch mode / without a gamepad source.
        RightStickScroll(state)
        LazyVerticalGrid(
            columns = columns,
            modifier = modifier.onGloballyPositioned { coordinates ->
                centerInfo.containerTopInRoot = coordinates.positionInRoot().y
            },
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
    }
}
