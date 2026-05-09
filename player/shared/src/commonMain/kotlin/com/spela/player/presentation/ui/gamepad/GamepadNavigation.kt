package com.spela.player.presentation.ui.gamepad

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.input.key.onKeyEvent
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

    // When focusResetKey changes, ensure the GamepadHandler Box has focus
    // so d-pad key events are received. Individual screens handle their own
    // focus acquisition via Modifier.focusRestoreItem(isDefault = true) on
    // their default focusable — this is just a fallback so the Box can
    // receive key events.
    if (focusResetKey != null) {
        LaunchedEffect(focusResetKey) {
            delay(500)
            try {
                if (!hasFocus) {
                    focusRequester.requestFocus()
                }
            } catch (_: Exception) {}
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

                // Non-directional keys handled here (capture phase).
                // Directional keys are handled in onKeyEvent (bubble phase)
                // so children like SpCarousel can intercept them first.
                when (event.key) {
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
            }
            .onKeyEvent { event ->
                // Directional keys in bubble phase — children (SpCarousel)
                // get first chance to handle via onPreviewKeyEvent.
                if (!enabled) return@onKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

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
                    else -> false
                }
            },
    ) {
        content()
    }
}

/**
 * Makes an element fully gamepad-focusable with focus ring, scroll-to-center,
 * and focusable modifier. Single source of truth for gamepad focus behavior.
 *
 * Combines: [centerOnFocus] + [spFocusRing] + `.focusable()`.
 *
 * Place after `.clickable()` in the modifier chain.
 *
 * @param shape Shape for the focus ring outline.
 * @param scaleOnFocus Whether to scale up slightly when focused.
 * @param interactionSource Optional shared interaction source for the focusable modifier.
 * @param addFocusable Whether to attach `.focusable()`. Set to `false` when the
 *   parent already supplies focusability — typically a `.clickable(...)` earlier
 *   in the modifier chain or a Material `Button`. Stacking a second focus target
 *   (the default) on top of an already-focusable parent on desktop swallows the
 *   first mouse click as a focus-acquisition event so the click never reaches
 *   the underlying handler. See #1096.
 */
fun Modifier.gamepadFocusable(
    shape: Shape = RoundedCornerShape(12.dp),
    scaleOnFocus: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    addFocusable: Boolean = true,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    this
        .centerOnFocus(interactionSource = source)
        .spFocusRing(shape = shape, scaleOnFocus = scaleOnFocus, interactionSource = source)
        .let { if (addFocusable) it.focusable(interactionSource = source) else it }
}

/**
 * Adds a visible focus ring to any focusable element.
 *
 * Uses [drawWithContent] to render the ring on top of the composable's content,
 * so it works even after `.clip()`.
 *
 * When [interactionSource] is provided, focus detection uses
 * `collectIsFocusedAsState()` which works regardless of modifier
 * chain position — essential when `.clickable()` creates a separate
 * focus target above this modifier.
 */
fun Modifier.spFocusRing(
    shape: Shape = RoundedCornerShape(12.dp),
    scaleOnFocus: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    var fallbackFocused by remember { mutableStateOf(false) }
    val isFocused = if (interactionSource != null) {
        interactionSource.collectIsFocusedAsState().value
    } else {
        fallbackFocused
    }

    val focusScale by animateFloatAsState(
        targetValue = if (isFocused && scaleOnFocus) 1.04f else 1f,
        animationSpec = tween(150),
    )

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    this
        .scale(focusScale)
        .then(
            if (interactionSource == null)
                Modifier.onFocusChanged { fallbackFocused = it.isFocused || it.hasFocus }
            else Modifier
        )
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
