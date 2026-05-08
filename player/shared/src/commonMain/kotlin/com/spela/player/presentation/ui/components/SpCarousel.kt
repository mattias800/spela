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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.spela.player.presentation.ui.gamepad.LocalActiveCarouselKey
import com.spela.player.presentation.ui.gamepad.LocalIsForwardNavigation
import com.spela.player.presentation.ui.theme.SpSpacing
import kotlinx.coroutines.delay

/**
 * Horizontal carousel with explicit per-item focus management and
 * horizontal centering.
 *
 * - Left/right uses FocusRequester per item — no spatial guessing.
 * - Stops at first and last item.
 * - Focused item is scrolled to the horizontal center of the carousel.
 * - Focus index syncs automatically when entering from any direction.
 *
 * @param memoryKey Optional unique key. When set, the carousel persists the
 *   focused item across screen navigation (via [rememberSaveable]) and
 *   restores focus on back navigation.
 * @param itemKey Optional stable per-item key (e.g. game id). Used together
 *   with [memoryKey] so the saved item can be matched even if the underlying
 *   list reorders. Falls back to index-as-string when null.
 */
@Composable
fun SpCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    memoryKey: String? = null,
    itemKey: ((index: Int) -> String)? = null,
    content: @Composable (index: Int, focusRequester: FocusRequester) -> Unit,
) {
    val scrollState = rememberScrollState()
    val requesters = remember(itemCount) { List(itemCount) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }

    // Persisted focused item key — survives screen disposal/restore via
    // SaveableStateHolder when [memoryKey] is set. "" means no saved item.
    val savedFocusKey = if (memoryKey != null) {
        rememberSaveable(memoryKey) { mutableStateOf("") }
    } else null

    // When provided by the screen, identifies which carousel last owned
    // focus. Used to gate restoration so only that carousel restores —
    // sibling carousels don't race.
    val activeCarouselKey = LocalActiveCarouselKey.current

    // Track each item's position and width within the Row
    val itemPositions = remember(itemCount) { FloatArray(itemCount) }
    val itemWidths = remember(itemCount) { FloatArray(itemCount) }

    // Track rapid key presses for instant vs animated scroll
    var lastFocusChangeTime by remember { mutableLongStateOf(0L) }

    // Restore focus to the previously focused item on back navigation.
    // Runs once when the carousel enters composition; gated on:
    //   - memoryKey set and a non-empty saved key
    //   - currently arriving via back/tab-switch (not forward push)
    //   - itemCount > 0 (data loaded)
    val isForward = LocalIsForwardNavigation.current
    LaunchedEffect(Unit) {
        val savedKey = savedFocusKey?.value ?: return@LaunchedEffect
        if (savedKey.isEmpty() || isForward || itemCount == 0) return@LaunchedEffect
        // Multi-carousel scope: only the carousel that most recently owned
        // focus is allowed to restore. Sibling carousels skip even if they
        // have a saved item. Without an active-key provider, every carousel
        // restores independently (single-carousel screens behave as before).
        val active = activeCarouselKey
        if (active != null && active.value != memoryKey) return@LaunchedEffect
        val targetIndex = if (itemKey != null) {
            (0 until itemCount).firstOrNull { itemKey(it) == savedKey }
        } else {
            savedKey.toIntOrNull()?.takeIf { it in 0 until itemCount }
        } ?: return@LaunchedEffect
        // Brief delay so the carousel layout has measured before requesting
        // focus. Without it, the FocusRequester may not yet be bound.
        delay(120)
        try { requesters[targetIndex].requestFocus() } catch (_: Exception) {}
    }

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
            Box(
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        itemPositions[i] = coordinates.positionInParent().x
                        itemWidths[i] = coordinates.size.width.toFloat()
                    }
                    .onFocusChanged { state ->
                        if (state.hasFocus) {
                            focusedIndex = i
                            savedFocusKey?.value = itemKey?.invoke(i) ?: i.toString()
                            if (memoryKey != null) {
                                activeCarouselKey?.value = memoryKey
                            }
                        }
                    }
            ) {
                content(i, requesters[i])
            }
        }
    }
}
