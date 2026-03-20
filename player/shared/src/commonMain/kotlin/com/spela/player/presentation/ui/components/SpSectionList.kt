package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Standardized vertical list of sections with consistent gaps.
 *
 * This is the primary layout for screens that display a vertical list
 * of card sections (Explore, Console, Home, etc.). It enforces:
 * - Consistent vertical gap between all children (SpSpacing.Large = 20dp)
 * - Consistent horizontal padding (SpSpacing.ScreenHorizontal)
 *
 * Child items must NOT add their own outer vertical or horizontal spacing.
 */
@Composable
fun SpSectionList(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = SpSpacing.ScreenHorizontal,
            end = SpSpacing.ScreenHorizontal,
            top = topPadding,
            bottom = SpSpacing.XLarge,
        ),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
        content = content,
    )
}
