package com.spela.player.presentation.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Horizontal carousel with explicit per-item focus management and
 * horizontal centering.
 *
 * - Left/right uses FocusRequester per item — no spatial guessing.
 * - Stops at first and last item.
 * - Focused item is scrolled to the horizontal center of the carousel.
 * - Focus index syncs automatically when entering from any direction.
 *
 * @param memoryKey Optional unique group key. When set together with
 *   [itemKey], each item participates in screen-scoped focus restoration
 *   via `focusRestoreItem("$memoryKey/$itemKey")`. The enclosing screen
 *   must provide [com.spela.player.presentation.ui.gamepad.LocalFocusMemory]
 *   for restoration to take effect.
 * @param itemKey Optional stable per-item key (e.g. game id). Combined
 *   with [memoryKey] into the per-item focus-restore key so the saved
 *   item can be matched even if the underlying list reorders.
 * @param isDefaultFocusGroup When true, item 0 of this carousel is the
 *   screen's default focus on first entry (when nothing else is saved).
 *   Apply to the *first* meaningful carousel on a screen — never more
 *   than one per screen.
 */
@Composable
fun SpCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    memoryKey: String? = null,
    itemKey: ((index: Int) -> String)? = null,
    isDefaultFocusGroup: Boolean = false,
    content: @Composable (index: Int, focusRequester: FocusRequester) -> Unit,
) {
    val scrollState = rememberScrollState()
    val requesters = remember(itemCount) { List(itemCount) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }

    // Track each item's position and width within the Row
    val itemPositions = remember(itemCount) { FloatArray(itemCount) }
    val itemWidths = remember(itemCount) { FloatArray(itemCount) }

    // Track rapid key presses for instant vs animated scroll
    var lastFocusChangeTime by remember { mutableLongStateOf(0L) }

    // Center the focused item horizontally when focusedIndex changes
    LaunchedEffect(focusedIndex) {
        val itemX = itemPositions[focusedIndex]
        val itemWidth = itemWidths[focusedIndex]
        if (itemWidth <= 0f) return@LaunchedEffect

        val viewportWidth = scrollState.viewportSize.toFloat()
        val itemCenter = itemX + itemWidth / 2f
        val targetScroll = (itemCenter - viewportWidth / 2f)
            .toInt()
            .coerceIn(0, scrollState.maxValue)

        val now = System.currentTimeMillis()
        val isRapid = now - lastFocusChangeTime < 100
        lastFocusChangeTime = now

        if (isRapid) {
            scrollState.scrollTo(targetScroll)
        } else {
            scrollState.animateScrollTo(targetScroll)
        }
    }

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
            val restoreKey = if (memoryKey != null && itemKey != null) {
                "$memoryKey/${itemKey(i)}"
            } else null

            Box(
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        itemPositions[i] = coordinates.positionInParent().x
                        itemWidths[i] = coordinates.size.width.toFloat()
                    }
                    .onFocusChanged { state ->
                        if (state.hasFocus) {
                            focusedIndex = i
                        }
                    }
                    .let { base ->
                        if (restoreKey != null) {
                            // Don't share `requesters[i]` here — it's already
                            // attached by the content lambda below to the inner
                            // focusable target. focusRestoreItem owns its own
                            // requester on the outer Box; calling requestFocus
                            // there propagates down to the first focusable
                            // descendant (the card).
                            base.focusRestoreItem(
                                key = restoreKey,
                                isDefault = isDefaultFocusGroup && i == 0,
                            )
                        } else {
                            base
                        }
                    }
            ) {
                content(i, requesters[i])
            }
        }
    }
}
