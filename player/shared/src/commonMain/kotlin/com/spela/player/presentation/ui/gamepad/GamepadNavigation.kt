package com.spela.player.presentation.ui.gamepad

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
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
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // Track whether any element currently has focus. When nothing is focused,
    // D-pad presses should acquire focus on the first element (recovery).
    // When something IS focused, failed directional moves should NOT wrap.
    var hasFocus by remember { mutableStateOf(false) }

    // When focusResetKey changes (e.g. section switch in gamepad mode),
    // clear focus and move to the first focusable element on the new page.
    // Retries focus acquisition because screens with async data loading may
    // not have focusable elements in the compose tree on the first attempt.
    if (focusResetKey != null) {
        LaunchedEffect(focusResetKey) {
            delay(150)
            focusManager.clearFocus(force = true)
            // Retry up to 15 times (200ms apart, ~3s total) to find a focusable element.
            // Screens with async data may need time before focusable items are composed.
            repeat(15) {
                if (focusManager.moveFocus(FocusDirection.Next)) return@LaunchedEffect
                delay(200)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { state -> hasFocus = state.hasFocus }
            .onPreviewKeyEvent { event ->
                if (!enabled) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.DirectionUp -> {
                        val hadFocus = hasFocus
                        if (!focusManager.moveFocus(FocusDirection.Up)) {
                            // Recovery: re-acquire focus when nothing is focused,
                            // or when focus escaped the visible area at a boundary.
                            if (!hadFocus || !hasFocus) {
                                focusManager.moveFocus(FocusDirection.Next)
                            }
                        }
                        onGamepadInput?.invoke()
                        true
                    }
                    Key.DirectionDown -> {
                        val hadFocus = hasFocus
                        if (!focusManager.moveFocus(FocusDirection.Down)) {
                            if (!hadFocus || !hasFocus) {
                                focusManager.moveFocus(FocusDirection.Next)
                            }
                        }
                        onGamepadInput?.invoke()
                        true
                    }
                    Key.DirectionLeft -> {
                        val hadFocus = hasFocus
                        if (!focusManager.moveFocus(FocusDirection.Left)) {
                            if (!hadFocus || !hasFocus) {
                                focusManager.moveFocus(FocusDirection.Next)
                            }
                        }
                        onGamepadInput?.invoke()
                        true
                    }
                    Key.DirectionRight -> {
                        val hadFocus = hasFocus
                        if (!focusManager.moveFocus(FocusDirection.Right)) {
                            if (!hadFocus || !hasFocus) {
                                focusManager.moveFocus(FocusDirection.Next)
                            }
                        }
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
 * The element must already be focusable (via focusable() or clickable()).
 * Place this modifier BEFORE focusable()/clickable() in the chain.
 */
fun Modifier.spFocusRing(
    shape: Shape = RoundedCornerShape(12.dp),
    scaleOnFocus: Boolean = false,
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = tween(150),
    )

    val focusScale by animateFloatAsState(
        targetValue = if (isFocused && scaleOnFocus) 1.04f else 1f,
        animationSpec = tween(150),
    )

    this
        .scale(focusScale)
        .onFocusChanged { state -> isFocused = state.isFocused || state.hasFocus }
        .border(
            width = 2.dp,
            color = borderColor,
            shape = shape,
        )
}
