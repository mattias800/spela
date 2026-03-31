package com.spela.player.presentation.ui.gamepad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Manages focus requesters for grid items so d-pad navigation moves
 * by row/column index instead of spatial position.
 *
 * Usage:
 * ```
 * val gridNav = rememberGridNavigation(itemCount = games.size, columns = columns)
 *
 * games.forEachIndexed { index, game ->
 *     GameCard(modifier = Modifier.gridFocusTarget(gridNav, index))
 * }
 * ```
 */
class GridNavigationState(
    val itemCount: Int,
    val columns: Int,
) {
    val focusRequesters = List(itemCount) { FocusRequester() }

    fun indexAbove(current: Int): Int? {
        val target = current - columns
        return if (target >= 0) target else null
    }

    fun indexBelow(current: Int): Int? {
        val target = current + columns
        return if (target < itemCount) target else null
    }

    fun indexLeft(current: Int): Int? {
        val col = current % columns
        return if (col > 0) current - 1 else null
    }

    fun indexRight(current: Int): Int? {
        val col = current % columns
        return if (col < columns - 1 && current + 1 < itemCount) current + 1 else null
    }
}

@Composable
fun rememberGridNavigation(itemCount: Int, columns: Int): GridNavigationState {
    return remember(itemCount, columns) { GridNavigationState(itemCount, columns) }
}

/**
 * Makes this element a grid navigation target at the given [index].
 * D-pad up/down/left/right navigate by grid position instead of spatial proximity.
 */
fun Modifier.gridFocusTarget(state: GridNavigationState, index: Int): Modifier {
    if (index >= state.focusRequesters.size) return this

    return this
        .focusRequester(state.focusRequesters[index])
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val targetIndex = when (event.key) {
                Key.DirectionUp -> state.indexAbove(index)
                Key.DirectionDown -> state.indexBelow(index)
                Key.DirectionLeft -> state.indexLeft(index)
                Key.DirectionRight -> state.indexRight(index)
                else -> null
            }
            if (targetIndex != null) {
                try {
                    state.focusRequesters[targetIndex].requestFocus()
                    true
                } catch (_: Exception) { false }
            } else false
        }
}
