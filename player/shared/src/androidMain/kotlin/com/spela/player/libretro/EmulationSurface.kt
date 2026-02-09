package com.spela.player.libretro

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Composable that renders emulation video frames from the AndroidLibretroController.
 * Scales the frame to fit the available space while maintaining aspect ratio.
 */
@Composable
fun EmulationSurface(
    controller: AndroidLibretroController,
    modifier: Modifier = Modifier,
) {
    val bitmap by controller.frameBitmap.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            bitmap?.let { bmp ->
                drawScaledBitmap(bmp)
            }
        }
    }
}

/**
 * Draw a bitmap scaled to fit within the canvas, centered, maintaining aspect ratio.
 * Uses nearest-neighbor filtering for crisp pixel art rendering.
 */
private fun DrawScope.drawScaledBitmap(bitmap: Bitmap) {
    val srcWidth = bitmap.width.toFloat()
    val srcHeight = bitmap.height.toFloat()
    val dstWidth = size.width
    val dstHeight = size.height

    val scale = min(dstWidth / srcWidth, dstHeight / srcHeight)
    val scaledWidth = (srcWidth * scale).roundToInt()
    val scaledHeight = (srcHeight * scale).roundToInt()
    val offsetX = ((dstWidth - scaledWidth) / 2f).roundToInt()
    val offsetY = ((dstHeight - scaledHeight) / 2f).roundToInt()

    drawImage(
        image = bitmap.asImageBitmap(),
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(offsetX, offsetY),
        dstSize = IntSize(scaledWidth, scaledHeight),
        filterQuality = FilterQuality.None,
    )
}
