package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Vertical list of sections with consistent spacing between them.
 *
 * This is a simple Column with [SpSpacing.Large] vertical gaps.
 * It does NOT scroll (the parent [SpScreen] handles scrolling)
 * and does NOT add padding (the parent [SpScreenPadding] handles that).
 *
 * Typically contains [SpTitledSection] items.
 */
@Composable
fun SpSectionList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
        content = content,
    )
}
