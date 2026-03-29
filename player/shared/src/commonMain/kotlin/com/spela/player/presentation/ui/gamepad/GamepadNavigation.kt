package com.spela.player.presentation.ui.gamepad

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Wraps content with gamepad/keyboard navigation key handling.
 *
 * Handles:
 * - D-pad / arrow keys: spatial focus navigation via FocusManager
 * - Escape: back navigation
 *
 * Platform-specific gamepad buttons (A, B) are handled at the platform layer:
 * - Android: MainActivity remaps BUTTON_A -> DPAD_CENTER, BUTTON_B -> BACK.
 * - Desktop: Arrow keys + Enter/Space + Escape cover all navigation needs.
 *
 * @param onGamepadInput Called on any handled D-pad/keyboard navigation input.
 *   Used to signal that the user is using gamepad-style input (for input mode detection).
 * @param focusResetKey When this value changes (e.g. on section switch), focus is
 *   cleared and moved to the first focusable element after recomposition.
 */
@Composable
fun GamepadHandler(
    enabled: Boolean = true,
    onBack: (() -> Unit)? = null,
    onNextSection: (() -> Unit)? = null,
    onPreviousSection: (() -> Unit)? = null,
    onGamepadInput: (() -> Unit)? = null,
    focusResetKey: Any? = null,
    isGoingBack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    // Track focus state. isSelfFocused = the wrapper Box has direct focus
    // (not a child). hasFocus = anything in the tree has focus.
    var hasFocus by remember { mutableStateOf(false) }
    var isSelfFocused by remember { mutableStateOf(false) }

    // When focusResetKey changes (e.g. tab switch or screen navigation),
    // auto-focus the first visible focusable element.
    //
    // Forward navigation: focus the first element on the new screen.
    // Back navigation: focus the first VISIBLE element — scroll position is
    // restored by saveableStateHolder, so this lands near where the user was.
    if (focusResetKey != null) {
        LaunchedEffect(focusResetKey) {
            // Wait for new content to compose
            delay(100)
            // Try to focus the first element. Retry up to 10 times for
            // screens with async data loading (API calls may take 1-2s).
            // Total window: 100ms initial + 10 × 200ms = 2.1 seconds.
            try {
                focusRequester.requestFocus()
                repeat(10) {
                    if (focusManager.moveFocus(FocusDirection.Next)) return@LaunchedEffect
                    delay(200)
                }
            } catch (_: Exception) {
                // FocusRequester may not be attached yet during transitions
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { state ->
                hasFocus = state.hasFocus
                isSelfFocused = state.isFocused
            }
            .onPreviewKeyEvent { event ->
                if (!enabled) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // When the wrapper Box itself has focus (no child focused yet),
                // any d-pad press should enter the content via moveFocus(Next).
                // Directional moves don't work from a full-screen Box (nothing
                // is spatially above/below/left/right of it).
                if (isSelfFocused || !hasFocus) {
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown,
                        Key.DirectionLeft, Key.DirectionRight -> {
                            focusManager.moveFocus(FocusDirection.Next)
                            onGamepadInput?.invoke()
                            return@onPreviewKeyEvent true
                        }
                        else -> {}
                    }
                }

                when (event.key) {
                    Key.DirectionUp -> {
                        focusManager.moveFocus(FocusDirection.Up)
                        onGamepadInput?.invoke()
                        true
                    }
                    Key.DirectionDown -> {
                        focusManager.moveFocus(FocusDirection.Down)
                        onGamepadInput?.invoke()
                        true
                    }
                    Key.DirectionLeft -> {
                        focusManager.moveFocus(FocusDirection.Left)
                        onGamepadInput?.invoke()
                        true
                    }
                    Key.DirectionRight -> {
                        focusManager.moveFocus(FocusDirection.Right)
                        onGamepadInput?.invoke()
                        true
                    }
                    Key.Escape -> {
                        onBack?.invoke()
                        onBack != null
                    }
                    Key.RightBracket -> {
                        focusManager.clearFocus(force = true)
                        hasFocus = false
                        onNextSection?.invoke()
                        onGamepadInput?.invoke()
                        onNextSection != null
                    }
                    Key.LeftBracket -> {
                        focusManager.clearFocus(force = true)
                        hasFocus = false
                        onPreviousSection?.invoke()
                        onGamepadInput?.invoke()
                        onPreviousSection != null
                    }
                    Key.Tab -> {
                        // Only consume Tab for section navigation when sections exist.
                        // Otherwise let it do default focus movement (e.g., login form).
                        if (event.isShiftPressed && onPreviousSection != null) {
                            focusManager.clearFocus(force = true)
                            hasFocus = false
                            onPreviousSection.invoke()
                            onGamepadInput?.invoke()
                            true
                        } else if (!event.isShiftPressed && onNextSection != null) {
                            focusManager.clearFocus(force = true)
                            hasFocus = false
                            onNextSection.invoke()
                            onGamepadInput?.invoke()
                            true
                        } else {
                            false // Let default Tab focus navigation work
                        }
                    }
                    else -> false
                }
            },
    ) {
        content()
    }
}

/**
 * Adds a visible focus ring to any focusable element.
 *
 * Uses [drawWithContent] to render the ring on top of the composable's content,
 * so it works even after `.clip()`.
 *
 * **Placement:** Must be right before `.focusable()` in the modifier chain.
 * Do NOT place it before `.clickable()` — the clickable modifier intercepts
 * focus events and prevents [onFocusChanged] from firing.
 */
fun Modifier.spFocusRing(
    shape: Shape = RoundedCornerShape(12.dp),
    scaleOnFocus: Boolean = false,
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }

    val focusScale by animateFloatAsState(
        targetValue = if (isFocused && scaleOnFocus) 1.04f else 1f,
        animationSpec = tween(150),
    )

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    this
        .scale(focusScale)
        .onFocusChanged { isFocused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (isFocused) {
                val strokeWidth = 3.dp.toPx()
                val halfStroke = strokeWidth / 2f
                val insetSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val outline = shape.createOutline(insetSize, layoutDirection, density)
                translate(left = halfStroke, top = halfStroke) {
                    drawOutline(
                        outline = outline,
                        color = Color.White.copy(alpha = 0.85f),
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
        }
}
