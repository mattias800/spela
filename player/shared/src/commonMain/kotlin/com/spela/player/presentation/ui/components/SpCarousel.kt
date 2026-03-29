package com.spela.player.presentation.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Horizontal carousel with built-in focus restoration for gamepad navigation.
 *
 * When focus leaves the carousel (e.g. d-pad down) and returns (d-pad up),
 * the previously focused item is automatically re-focused.
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
    LazyRow(
        modifier = modifier.focusGroup().focusRestorer(),
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}
