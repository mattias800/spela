package com.spela.player.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor

/**
 * Minimal slider with a thin track and round thumb.
 *
 * @param value Current value in 0..1 range.
 * @param onValueChange Called when the user drags or taps.
 * @param activeColor Color of the filled portion of the track.
 * @param inactiveColor Color of the unfilled portion.
 * @param thumbColor Color of the round thumb.
 * @param trackHeight Thickness of the track.
 * @param thumbRadius Radius of the thumb circle.
 */
@Composable
fun SpSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = SpColor.Primary,
    inactiveColor: Color = SpColor.OnBackgroundTertiary.copy(alpha = 0.3f),
    thumbColor: Color = SpColor.OnBackground,
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 7.dp,
) {
    var dragging by remember { mutableFloatStateOf(-1f) }

    Canvas(
        modifier = modifier
            .height(44.dp) // Large touch target for accessibility
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(dragging)
                    },
                    onDragEnd = { dragging = -1f },
                    onDragCancel = { dragging = -1f },
                    onHorizontalDrag = { change, _ ->
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragging = fraction
                        onValueChange(fraction)
                    },
                )
            },
    ) {
        val trackY = size.height / 2
        val trackH = trackHeight.toPx()
        val thumbR = thumbRadius.toPx()
        val thumbX = value * size.width

        // Inactive track (full width)
        drawLine(
            color = inactiveColor,
            start = Offset(0f, trackY),
            end = Offset(size.width, trackY),
            strokeWidth = trackH,
            cap = StrokeCap.Round,
        )
        // Active track (up to thumb)
        if (thumbX > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, trackY),
                end = Offset(thumbX, trackY),
                strokeWidth = trackH,
                cap = StrokeCap.Round,
            )
        }
        // Round thumb
        drawCircle(
            color = thumbColor,
            radius = thumbR,
            center = Offset(thumbX.coerceIn(thumbR, size.width - thumbR), trackY),
        )
    }
}
