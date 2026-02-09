package com.spela.player.libretro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Libretro joypad button IDs matching libretro.h RETRO_DEVICE_ID_JOYPAD_* constants.
 */
private object RetroButton {
    const val B = 0
    const val SELECT = 2
    const val START = 3
    const val UP = 4
    const val DOWN = 5
    const val LEFT = 6
    const val RIGHT = 7
    const val A = 8
}

private val IdleAlpha = 0.30f
private val PressedAlpha = 0.60f

/**
 * Touch-screen gamepad overlay for Android.
 *
 * Renders semi-transparent D-pad, A/B, and Start/Select buttons over the
 * emulation surface. Supports multi-touch so users can press multiple
 * buttons simultaneously (e.g. D-pad + A).
 *
 * All buttons call [AndroidLibretroController.setButton] on press/release.
 */
@Composable
fun TouchGamepadOverlay(
    controller: AndroidLibretroController,
    modifier: Modifier = Modifier,
) {
    val buttonColor = Color.White.copy(alpha = IdleAlpha)
    val pressedColor = Color.White.copy(alpha = PressedAlpha)
    val textColor = Color.White.copy(alpha = 0.85f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Touch controls" },
    ) {
        val edgePadding = 32.dp
        val bottomPadding = 32.dp

        // D-pad - bottom left (~120dp total diameter)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = edgePadding, bottom = bottomPadding),
        ) {
            DPad(
                controller = controller,
                buttonColor = buttonColor,
                pressedColor = pressedColor,
                textColor = textColor,
            )
        }

        // A/B action buttons - bottom right (diagonal: B left, A right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = edgePadding, bottom = bottomPadding),
        ) {
            ActionButtons(
                controller = controller,
                buttonColor = buttonColor,
                pressedColor = pressedColor,
                textColor = textColor,
            )
        }

        // Start/Select - bottom center
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding + 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GamepadButton(
                label = "SELECT",
                accessibilityLabel = "Button Select",
                controller = controller,
                buttonId = RetroButton.SELECT,
                buttonColor = buttonColor,
                pressedColor = pressedColor,
                textColor = textColor,
                width = 64.dp,
                height = 40.dp,
                shape = RoundedCornerShape(12.dp),
                fontSize = 10,
            )
            Spacer(Modifier.width(20.dp))
            GamepadButton(
                label = "START",
                accessibilityLabel = "Button Start",
                controller = controller,
                buttonId = RetroButton.START,
                buttonColor = buttonColor,
                pressedColor = pressedColor,
                textColor = textColor,
                width = 64.dp,
                height = 40.dp,
                shape = RoundedCornerShape(12.dp),
                fontSize = 10,
            )
        }
    }
}

/**
 * D-pad with four directional buttons arranged in a cross pattern.
 * Total size is approximately 120dp x 120dp (3 buttons of 38dp + 2 gaps).
 */
@Composable
private fun DPad(
    controller: AndroidLibretroController,
    buttonColor: Color,
    pressedColor: Color,
    textColor: Color,
) {
    val btnSize = 38.dp
    val gap = 2.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Up
        GamepadButton(
            label = "\u25B2",
            accessibilityLabel = "D-pad Up",
            controller = controller,
            buttonId = RetroButton.UP,
            buttonColor = buttonColor,
            pressedColor = pressedColor,
            textColor = textColor,
            width = btnSize,
            height = btnSize,
            shape = RoundedCornerShape(
                topStart = 12.dp, topEnd = 12.dp,
                bottomStart = 4.dp, bottomEnd = 4.dp,
            ),
        )
        Spacer(Modifier.height(gap))
        // Left - Center - Right
        Row(verticalAlignment = Alignment.CenterVertically) {
            GamepadButton(
                label = "\u25C0",
                accessibilityLabel = "D-pad Left",
                controller = controller,
                buttonId = RetroButton.LEFT,
                buttonColor = buttonColor,
                pressedColor = pressedColor,
                textColor = textColor,
                width = btnSize,
                height = btnSize,
                shape = RoundedCornerShape(
                    topStart = 12.dp, bottomStart = 12.dp,
                    topEnd = 4.dp, bottomEnd = 4.dp,
                ),
            )
            Spacer(Modifier.width(gap))
            // Center filler
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(RoundedCornerShape(4.dp))
                    .background(buttonColor.copy(alpha = buttonColor.alpha * 0.5f)),
            )
            Spacer(Modifier.width(gap))
            GamepadButton(
                label = "\u25B6",
                accessibilityLabel = "D-pad Right",
                controller = controller,
                buttonId = RetroButton.RIGHT,
                buttonColor = buttonColor,
                pressedColor = pressedColor,
                textColor = textColor,
                width = btnSize,
                height = btnSize,
                shape = RoundedCornerShape(
                    topEnd = 12.dp, bottomEnd = 12.dp,
                    topStart = 4.dp, bottomStart = 4.dp,
                ),
            )
        }
        Spacer(Modifier.height(gap))
        // Down
        GamepadButton(
            label = "\u25BC",
            accessibilityLabel = "D-pad Down",
            controller = controller,
            buttonId = RetroButton.DOWN,
            buttonColor = buttonColor,
            pressedColor = pressedColor,
            textColor = textColor,
            width = btnSize,
            height = btnSize,
            shape = RoundedCornerShape(
                bottomStart = 12.dp, bottomEnd = 12.dp,
                topStart = 4.dp, topEnd = 4.dp,
            ),
        )
    }
}

/**
 * A/B action buttons in a diagonal layout matching NES/SNES convention:
 * B on the left (lower), A on the right (upper).
 */
@Composable
private fun ActionButtons(
    controller: AndroidLibretroController,
    buttonColor: Color,
    pressedColor: Color,
    textColor: Color,
) {
    val btnSize = 56.dp
    val gap = 12.dp

    Row(verticalAlignment = Alignment.Bottom) {
        // B button (left, lower position)
        Box(modifier = Modifier.padding(top = 28.dp)) {
            GamepadButton(
                label = "B",
                accessibilityLabel = "Button B",
                controller = controller,
                buttonId = RetroButton.B,
                buttonColor = buttonColor,
                pressedColor = pressedColor,
                textColor = textColor,
                width = btnSize,
                height = btnSize,
                shape = CircleShape,
            )
        }
        Spacer(Modifier.width(gap))
        // A button (right, upper position)
        GamepadButton(
            label = "A",
            accessibilityLabel = "Button A",
            controller = controller,
            buttonId = RetroButton.A,
            buttonColor = buttonColor,
            pressedColor = pressedColor,
            textColor = textColor,
            width = btnSize,
            height = btnSize,
            shape = CircleShape,
        )
    }
}

/**
 * A single gamepad button that sends press/release events to the libretro controller.
 * Uses low-level pointer input for multi-touch support -- each button independently
 * tracks its own pointer(s) so multiple buttons can be pressed simultaneously.
 */
@Composable
private fun GamepadButton(
    label: String,
    accessibilityLabel: String,
    controller: AndroidLibretroController,
    buttonId: Int,
    buttonColor: Color,
    pressedColor: Color,
    textColor: Color,
    width: Dp,
    height: Dp,
    shape: Shape,
    fontSize: Int = 15,
) {
    val isPressed = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(if (isPressed.value) pressedColor else buttonColor)
            .semantics { contentDescription = accessibilityLabel }
            .pointerInput(buttonId) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                isPressed.value = true
                                controller.setButton(0, buttonId, true)
                            }
                            PointerEventType.Release -> {
                                val anyDown = event.changes.any { it.pressed }
                                if (!anyDown) {
                                    isPressed.value = false
                                    controller.setButton(0, buttonId, false)
                                }
                            }
                            PointerEventType.Exit -> {
                                isPressed.value = false
                                controller.setButton(0, buttonId, false)
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSize.sp,
            textAlign = TextAlign.Center,
        )
    }
}
