package com.spela.player.libretro

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Result of computing how an emulation frame should be scaled and positioned
 * within a canvas, respecting the core's display aspect ratio.
 */
data class ScaledFrame(
    val width: Int,
    val height: Int,
    val offsetX: Int,
    val offsetY: Int,
)

/**
 * Computes the scaled size and centered offset for rendering an emulation frame
 * within a canvas, respecting the core-reported display aspect ratio (DAR).
 *
 * When [displayAspectRatio] > 0, virtual display dimensions are derived from
 * the DAR rather than raw pixel dimensions. This handles cores that output
 * non-square pixels (e.g. Amiga 320x200 displayed at 4:3).
 *
 * When [displayAspectRatio] <= 0, falls back to the source pixel dimensions.
 */
fun computeScaledFrame(
    srcWidth: Int,
    srcHeight: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    displayAspectRatio: Float = 0f,
): ScaledFrame {
    val displayWidth: Float
    val displayHeight: Float
    if (displayAspectRatio > 0f) {
        displayWidth = srcHeight.toFloat() * displayAspectRatio
        displayHeight = srcHeight.toFloat()
    } else {
        displayWidth = srcWidth.toFloat()
        displayHeight = srcHeight.toFloat()
    }

    val scale = min(canvasWidth / displayWidth, canvasHeight / displayHeight)
    val scaledWidth = (displayWidth * scale).roundToInt()
    val scaledHeight = (displayHeight * scale).roundToInt()
    val offsetX = ((canvasWidth - scaledWidth) / 2f).roundToInt()
    val offsetY = ((canvasHeight - scaledHeight) / 2f).roundToInt()

    return ScaledFrame(
        width = scaledWidth,
        height = scaledHeight,
        offsetX = offsetX,
        offsetY = offsetY,
    )
}
