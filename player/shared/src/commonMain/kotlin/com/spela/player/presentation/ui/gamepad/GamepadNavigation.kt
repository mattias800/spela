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

    // When focusResetKey changes (e.g. section switch in gamepad mode),
    // clear focus and move to the first focusable element on the new page.
    if (focusResetKey != null) {
        LaunchedEffect(focusResetKey) {
            // Wait for new content to compose
            delay(100)
            focusManager.clearFocus(force = true)
            focusManager.moveFocus(FocusDirection.Next)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (!enabled) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

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
                        onNextSection?.invoke()
                        onGamepadInput?.invoke()
                        onNextSection != null
                    }
                    Key.LeftBracket -> {
                        focusManager.clearFocus(force = true)
                        onPreviousSection?.invoke()
                        onGamepadInput?.invoke()
                        onPreviousSection != null
                    }
                    Key.Tab -> {
                        if (event.isShiftPressed) {
                            focusManager.clearFocus(force = true)
                            onPreviousSection?.invoke()
                            onGamepadInput?.invoke()
                            onPreviousSection != null
                        } else {
                            focusManager.clearFocus(force = true)
                            onNextSection?.invoke()
                            onGamepadInput?.invoke()
                            onNextSection != null
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
        targetValue = if (isFocused) SpColor.Primary.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = tween(150),
    )

    val focusScale by animateFloatAsState(
        targetValue = if (isFocused && scaleOnFocus) 1.04f else 1f,
        animationSpec = tween(150),
    )

    this
        .scale(focusScale)
        .onFocusChanged { state -> isFocused = state.isFocused }
        .border(
            width = 2.dp,
            color = borderColor,
            shape = shape,
        )
}
