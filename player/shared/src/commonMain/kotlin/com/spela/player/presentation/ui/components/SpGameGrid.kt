package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.presentation.ui.theme.SpSpacing
import kotlin.math.max

/**
 * CONTENT component — a responsive non-lazy game card grid.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Uses the same adaptive column sizing as the LazyVerticalGrid in ConsoleGamesScreen
 * (GridCells.Adaptive with SpSpacing.GridCellMinWidth), but can be embedded
 * inside a LazyColumn item since it's not lazy itself.
 *
 * @param items List of composable content blocks, one per grid cell.
 * @param modifier Modifier for the grid container.
 */
@Composable
fun SpGameGrid(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    BoxWithConstraints(modifier = modifier) {
        val minCellWidth = SpSpacing.GridCellMinWidth
        val gap = SpSpacing.Medium
        val columns = max(1, ((maxWidth + gap) / (minCellWidth + gap)).toInt())
        val chunked = items.chunked(columns)

        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            chunked.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            item()
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
