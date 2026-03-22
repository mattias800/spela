package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.ui.theme.SpColor

/**
 * ROLE component — a chip that indicates a status (e.g. "Current", "Active").
 *
 * Layer 3 in the component hierarchy (Design → Content → Role).
 * Thin wrapper around [SpChip] with filled/selected styling.
 * Use for status labels on list items (sessions, downloads, etc.).
 */
@Composable
fun SpStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SpColor.Success,
) {
    SpChip(
        text = text,
        color = color,
        isSelected = true,
        modifier = modifier,
    )
}
