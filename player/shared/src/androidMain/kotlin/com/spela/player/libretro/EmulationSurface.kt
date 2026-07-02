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
import com.spela.player.domain.model.WidescreenMode
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.feature.shader.drawShaderOverlay
import com.spela.player.presentation.ui.feature.shader.filterQuality

/**
 * Composable that renders emulation video frames from the AndroidLibretroController.
 * Scales the frame to fit the available space while maintaining the core's display aspect ratio.
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
    widescreenMode: WidescreenMode = WidescreenMode.NATIVE,
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
                if (!bmp.isRecycled) {
                    val dar = controller.getAspectRatio()
                    drawScaledBitmap(
                        bitmap = bmp,
                        shader = selectedShader,
                        widescreenMode = widescreenMode,
                        isDualScreenSplit = isDualScreenSplit,
                        splitY = splitY,
                        displayAspectRatio = dar,
                    )
                }
            }
        }
    }
}

/**
 * Draw a bitmap scaled to fit within the canvas, centered, maintaining the core's
 * display aspect ratio (DAR). When DAR is unavailable, falls back to bitmap pixel
 * dimensions.
 *
 * When [isDualScreenSplit] is true, only renders the top portion up to [splitY].
 */
private fun DrawScope.drawScaledBitmap(
    bitmap: Bitmap,
    shader: ShaderPreset,
    widescreenMode: WidescreenMode,
    isDualScreenSplit: Boolean = false,
    splitY: Int = 0,
    displayAspectRatio: Float = 0f,
) {
    val srcWidth = bitmap.width
    val srcHeight = if (isDualScreenSplit && splitY > 0) splitY else bitmap.height
    val srcOffset = IntOffset.Zero

    // The core reports a display aspect ratio for the *full* frame
    // (both DS screens stacked = 256×384, DAR ≈ 0.667). When we crop
    // to the top half (splitY = 192), feeding that full-frame DAR
    // into computeScaledFrame produces a wrong-aspect destination —
    // it derives `displayWidth = srcHeight × DAR` from the cropped
    // height, giving a tall narrow shape (128×192) instead of the
    // correct 256×192. The visible result is the top screen
    // horizontally squashed / vertically stretched. Compensate by
    // scaling the DAR up by the inverse of the crop ratio so the
    // arithmetic lands on the cropped source's true aspect. See #887.
    val croppedDar = if (isDualScreenSplit && splitY > 0 && displayAspectRatio > 0f) {
        displayAspectRatio * (bitmap.height.toFloat() / splitY)
    } else {
        displayAspectRatio
    }
    val effectiveDar = if (widescreenMode == WidescreenMode.NATIVE) {
        croppedDar
    } else {
        widescreenMode.displayAspectRatio
    }

    val scaled = computeScaledFrame(
        srcWidth = srcWidth,
        srcHeight = srcHeight,
        canvasWidth = size.width,
        canvasHeight = size.height,
        displayAspectRatio = effectiveDar,
        scaleMode = widescreenMode.frameScaleMode(),
    )

    val dstOffset = IntOffset(scaled.offsetX, scaled.offsetY)
    val dstSize = IntSize(scaled.width, scaled.height)
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
