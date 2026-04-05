package com.spela.player.presentation.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Horizontal carousel with explicit per-item focus management.
 *
 * Each item gets a [FocusRequester] via the [content] lambda's index parameter.
 * Left/right navigation uses `requestFocus()` on the target item — no spatial
 * focus guessing. Stops at first and last item.
 *
 * Usage:
 * ```
 * SpCarousel(itemCount = games.size) { index, focusRequester ->
 *     GameCard(modifier = Modifier.focusRequester(focusRequester))
 * }
 * ```
 *
 * @param itemCount Number of items in the carousel.
 * @param content Lambda receiving (index, FocusRequester) for each item.
 */
@Composable
fun SpCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, focusRequester: FocusRequester) -> Unit,
) {
    val scrollState = rememberScrollState()
    val requesters = remember(itemCount) { List(itemCount) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }

    Row(
        modifier = modifier
            .focusGroup()
            .horizontalScroll(scrollState)
            .padding(horizontal = SpSpacing.ScreenHorizontal)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (focusedIndex > 0) {
                            try {
                                requesters[focusedIndex - 1].requestFocus()
                            } catch (_: Exception) {}
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (focusedIndex < itemCount - 1) {
                            try {
                                requesters[focusedIndex + 1].requestFocus()
                            } catch (_: Exception) {}
                        }
                        true
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        for (i in 0 until itemCount) {
            Box(
                modifier = Modifier.onFocusChanged { state ->
                    if (state.hasFocus) {
                        focusedIndex = i
                    }
                }
            ) {
                content(i, requesters[i])
            }
        }
    }
}
