package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.gamepad.LazyListCenterInfo
import com.spela.player.presentation.ui.gamepad.LocalLazyListCenterInfo
import com.spela.player.presentation.ui.gamepad.RightStickScroll

/**
 * Drop-in replacement for [LazyColumn] that wires continuous right-stick
 * scrolling in gamepad mode (#1362) and provides [LocalLazyListCenterInfo] so
 * [com.spela.player.presentation.ui.gamepad.centerOnFocus] can scroll the
 * focused element to the viewport center during gamepad navigation. The
 * `LazyColumn` counterpart of [SpLazyVerticalGrid].
 *
 * Use this wherever a screen's main scroller is a [LazyColumn] of content.
 * No-op in touch mode / without a gamepad source.
 */
@Composable
fun SpScreenContentList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val centerInfo = remember(state) { LazyListCenterInfo(state) }
    CompositionLocalProvider(LocalLazyListCenterInfo provides centerInfo) {
        RightStickScroll(state)
        LazyColumn(
            modifier = modifier.onGloballyPositioned { coordinates ->
                centerInfo.containerTopInRoot = coordinates.positionInRoot().y
            },
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
    }
}
