package com.spela.player.presentation.ui.gamepad

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalInputModeManager
import com.spela.player.presentation.ui.components.LocalScrollState

/**
 * Holds the [LazyGridState] and the grid container's vertical position
 * in root coordinates. Provided by [SpLazyVerticalGrid] via [LocalLazyGridCenterInfo].
 */
class LazyGridCenterInfo(
    val state: LazyGridState,
) {
    /** Updated by [SpLazyVerticalGrid] via onGloballyPositioned. */
    var containerTopInRoot: Float = 0f
}

/**
 * Provides [LazyGridCenterInfo] to descendants so [centerOnFocus] can
 * scroll a [LazyVerticalGrid] to center the focused element.
 */
val LocalLazyGridCenterInfo = compositionLocalOf<LazyGridCenterInfo?> { null }

/**
 * Holds the [LazyListState] of a vertically-scrolling `LazyColumn` and
 * its top position in root coordinates. The screen-level scrollable
 * (e.g. the LazyColumn in `GameDetailLayout`) provides this so
 * [centerOnFocus] can scroll the column to vertically centre whatever
 * the user focused — a card inside a carousel, a metadata link, a top
 * player row, etc.
 *
 * Mirrors [LazyGridCenterInfo] for the LazyList case. Both exist
 * because Compose's `LazyListState` and `LazyGridState` are distinct
 * types; pick the one the screen actually uses.
 */
class LazyListCenterInfo(
    val state: LazyListState,
) {
    /** Updated by the LazyColumn host via `onGloballyPositioned`. */
    var containerTopInRoot: Float = 0f
}

/**
 * Provides [LazyListCenterInfo] to descendants so [centerOnFocus] can
 * scroll a `LazyColumn` to centre the focused element.
 */
val LocalLazyListCenterInfo = compositionLocalOf<LazyListCenterInfo?> { null }

/**
 * When this element gains focus, scrolls the nearest scrollable parent
 * so this element is vertically centered in the viewport.
 *
 * Supports two scrollable types:
 * - [LocalScrollState] from [SpScrollableContent] (regular Column + verticalScroll)
 * - [LocalLazyGridCenterInfo] from [SpLazyVerticalGrid] (lazy grid)
 *
 * Uses the [interactionSource] for focus detection (same source shared
 * with `.clickable()` and `.focusable()`), so it detects focus regardless
 * of which node in the modifier chain holds it.
 *
 * During rapid focus changes (key repeat, <200ms between changes),
 * scrolls instantly instead of animating for responsive feel.
 */
fun Modifier.centerOnFocus(
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    val scrollState = LocalScrollState.current
    val lazyGridInfo = LocalLazyGridCenterInfo.current
    val lazyListInfo = LocalLazyListCenterInfo.current
    val isFocused by interactionSource.collectIsFocusedAsState()
    var positionInRoot = 0f
    var elementHeight = 0f
    var lastFocusTime by remember { mutableLongStateOf(0L) }

    if (scrollState == null && lazyGridInfo == null && lazyListInfo == null) return@composed this

    val inputModeManager = LocalInputModeManager.current

    LaunchedEffect(isFocused) {
        if (!isFocused) return@LaunchedEffect

        // Centring-on-focus is a gamepad / arrow-key affordance — it
        // pulls the focused element to the viewport centre so the user
        // can see what they just navigated to with the d-pad. When the
        // focus change comes from a pointer (mouse click, touch tap,
        // `ComposeUiTest.performClick`), the element is already on
        // screen by definition, so there's nothing to scroll TO — and
        // running a scroll here has been observed to drop the
        // accompanying click action on Compose Multiplatform 1.10
        // (see #1196). Skip when the input mode is Touch.
        if (inputModeManager.inputMode != InputMode.Keyboard) return@LaunchedEffect

        val now = System.currentTimeMillis()
        val isRapid = now - lastFocusTime < 100
        lastFocusTime = now

        if (scrollState != null) {
            // Regular ScrollState: compute absolute target scroll position
            val currentScroll = scrollState.value
            val elementTop = positionInRoot + currentScroll
            val viewportHeight = scrollState.viewportSize.toFloat()

            val targetScroll = (elementTop - (viewportHeight / 2f) + (elementHeight / 2f))
                .toInt()
                .coerceIn(0, scrollState.maxValue)

            if (isRapid) {
                scrollState.scrollTo(targetScroll)
            } else {
                scrollState.animateScrollTo(targetScroll)
            }
        } else if (lazyGridInfo != null) {
            // LazyGridState: compute scroll delta from element's screen position
            val viewportHeight = lazyGridInfo.state.layoutInfo.viewportSize.height.toFloat()
            val elementCenterOnScreen = positionInRoot + elementHeight / 2f
            val viewportCenterOnScreen = lazyGridInfo.containerTopInRoot + viewportHeight / 2f
            val delta = elementCenterOnScreen - viewportCenterOnScreen

            if (isRapid) {
                lazyGridInfo.state.scrollBy(delta)
            } else {
                lazyGridInfo.state.animateScrollBy(delta)
            }
        } else if (lazyListInfo != null) {
            // LazyListState (vertical LazyColumn): same math as the
            // LazyGrid branch. We don't try to scroll the inner
            // horizontal LazyRow of a SpCarousel here — that's handled
            // by SpCarousel's own keyhandler. This branch only moves
            // the OUTER column so the focused element's row sits in
            // the centre of the screen vertically.
            val viewportHeight = lazyListInfo.state.layoutInfo.viewportSize.height.toFloat()
            val elementCenterOnScreen = positionInRoot + elementHeight / 2f
            val viewportCenterOnScreen = lazyListInfo.containerTopInRoot + viewportHeight / 2f
            val delta = elementCenterOnScreen - viewportCenterOnScreen

            if (isRapid) {
                lazyListInfo.state.scrollBy(delta)
            } else {
                lazyListInfo.state.animateScrollBy(delta)
            }
        }
    }

    this.onGloballyPositioned { coordinates ->
        positionInRoot = coordinates.positionInRoot().y
        elementHeight = coordinates.size.height.toFloat()
    }
}
