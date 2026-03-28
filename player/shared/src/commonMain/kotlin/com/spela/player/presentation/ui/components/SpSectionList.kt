package com.spela.player.presentation.ui.components

import androidx.compose.foundation.focusGroup
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
 * CONTENT component — standardized vertical list of sections.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * The primary layout for screens that show a vertical list of card
 * sections (Explore, Console, Home). Enforces:
 * - Consistent vertical gap between all children via `spacedBy`
 * - Consistent horizontal padding via `contentPadding`
 *
 * Child items must NOT add their own outer spacing — this component
 * is the single source of truth for inter-section gaps and alignment.
 *
 * Note: items that render nothing (empty data) should be guarded with
 * a condition OUTSIDE the `item {}` call to avoid ghost gaps.
 */
@Composable
fun SpSectionList(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.focusGroup(),
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
