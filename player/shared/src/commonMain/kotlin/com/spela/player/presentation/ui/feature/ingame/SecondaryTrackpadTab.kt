package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private const val TRACKPAD_SENSITIVITY = 1.5f
private const val TAP_TIMEOUT_MS = 200L
private const val TAP_MOVEMENT_THRESHOLD = 10f

/**
 * Trackpad tab for the secondary screen controls page.
 *
 * Provides relative-mode mouse input: dragging moves the cursor
 * relative to the current position. Includes dedicated left/right
 * click buttons and tap gesture shortcuts.
 */
@Composable
fun SecondaryTrackpadTab(
    onMouseMove: (dx: Float, dy: Float) -> Unit,
    onMouseButton: (left: Boolean, right: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.Small),
    ) {
        // Trackpad area
        TrackpadSurface(
            onMouseMove = onMouseMove,
            onTap = {
                // Single-finger tap = left click
                onMouseButton(true, false)
                onMouseButton(false, false)
            },
            onTwoFingerTap = {
                // Two-finger tap = right click
                onMouseButton(false, true)
                onMouseButton(false, false)
            },
            isButtonHeld = leftPressed,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Spacer(Modifier.height(SpSpacing.Small))

        // Click buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            ClickButton(
                label = "Left Click",
                isPressed = leftPressed,
                onPressChange = { pressed ->
                    leftPressed = pressed
                    onMouseButton(pressed, rightPressed)
                },
                modifier = Modifier.weight(1f),
            )
            ClickButton(
                label = "Right Click",
                isPressed = rightPressed,
                onPressChange = { pressed ->
                    rightPressed = pressed
                    onMouseButton(leftPressed, pressed)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrackpadSurface(
    onMouseMove: (dx: Float, dy: Float) -> Unit,
    onTap: () -> Unit,
    onTwoFingerTap: () -> Unit,
    isButtonHeld: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SpColor.SurfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, SpColor.OnBackgroundTertiary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .pointerInput(isButtonHeld) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    firstDown.consume()

                    var totalDx = 0f
                    var totalDy = 0f
                    var pointerCount = 1
                    val startTime = firstDown.uptimeMillis
                    var prevPosition = firstDown.position

                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }

                        if (activePointers.isEmpty()) {
                            val elapsed = (event.changes.firstOrNull()?.uptimeMillis ?: startTime) - startTime
                            val wasTap = elapsed < TAP_TIMEOUT_MS &&
                                kotlin.math.abs(totalDx) < TAP_MOVEMENT_THRESHOLD &&
                                kotlin.math.abs(totalDy) < TAP_MOVEMENT_THRESHOLD

                            if (wasTap) {
                                if (pointerCount >= 2) onTwoFingerTap() else onTap()
                            }
                            break
                        }

                        pointerCount = maxOf(pointerCount, activePointers.size)

                        if (activePointers.size == 1) {
                            val current = activePointers.first()
                            val dx = (current.position.x - prevPosition.x) * TRACKPAD_SENSITIVITY
                            val dy = (current.position.y - prevPosition.y) * TRACKPAD_SENSITIVITY
                            totalDx += dx
                            totalDy += dy
                            prevPosition = current.position

                            if (kotlin.math.abs(dx) > 0.5f || kotlin.math.abs(dy) > 0.5f) {
                                onMouseMove(dx, dy)
                            }
                            current.consume()
                        } else {
                            prevPosition = activePointers.first().position
                        }
                    }
                }
            }
            .semantics { contentDescription = "Trackpad area, drag to move cursor" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "\uD83D\uDDB1",
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackgroundTertiary.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun ClickButton(
    label: String,
    isPressed: Boolean,
    onPressChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isPressed) SpColor.Primary else SpColor.SurfaceVariant

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onPressChange(true)
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) {
                            onPressChange(false)
                            break
                        }
                    }
                }
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackground,
        )
    }
}
