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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.screen.drawShaderOverlay
import com.spela.player.presentation.ui.screen.filterQuality
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Composable that renders emulation video frames from the AndroidLibretroController.
 * Scales the frame to fit the available space while maintaining aspect ratio.
 */
/**
 * @param isDualScreenSplit When true, only the top portion of the framebuffer is rendered
 *   (used for DS games where the secondary display shows the bottom screen).
 * @param splitY The Y pixel where the framebuffer splits (e.g. 192 for DS).
 */
@Composable
fun EmulationSurface(
    controller: AndroidLibretroController,
    selectedShader: ShaderPreset = ShaderPreset.NONE,
    isDualScreenSplit: Boolean = false,
    splitY: Int = 0,
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
                drawScaledBitmap(bmp, shader = selectedShader, isDualScreenSplit = isDualScreenSplit, splitY = splitY)
            }
        }
    }
}

/**
 * Draw a bitmap scaled to fit within the canvas, centered, maintaining aspect ratio.
 * Applies the selected shader's filter quality and overlay effects.
 *
 * When [isDualScreenSplit] is true, only renders the top portion up to [splitY].
 */
private fun DrawScope.drawScaledBitmap(
    bitmap: Bitmap,
    shader: ShaderPreset,
    isDualScreenSplit: Boolean = false,
    splitY: Int = 0,
) {
    val srcWidth = bitmap.width
    val srcHeight = if (isDualScreenSplit && splitY > 0) splitY else bitmap.height
    val srcOffset = IntOffset.Zero

    val dstWidth = size.width
    val dstHeight = size.height

    val scale = min(dstWidth / srcWidth.toFloat(), dstHeight / srcHeight.toFloat())
    val scaledWidth = (srcWidth * scale).roundToInt()
    val scaledHeight = (srcHeight * scale).roundToInt()
    val offsetX = ((dstWidth - scaledWidth) / 2f).roundToInt()
    val offsetY = ((dstHeight - scaledHeight) / 2f).roundToInt()

    val dstOffset = IntOffset(offsetX, offsetY)
    val dstSize = IntSize(scaledWidth, scaledHeight)
    val srcSize = IntSize(srcWidth, srcHeight)

    drawImage(
        image = bitmap.asImageBitmap(),
        srcOffset = srcOffset,
        srcSize = srcSize,
        dstOffset = dstOffset,
        dstSize = dstSize,
        filterQuality = shader.filterQuality(),
    )

    drawShaderOverlay(shader, dstOffset, dstSize, srcSize)
}
