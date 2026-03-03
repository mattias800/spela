package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.feature.shader.drawShaderOverlay
import com.spela.player.presentation.ui.feature.shader.filterQuality
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.presentation.viewmodel.LibretroController
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders the bottom half of the DS framebuffer with touch-to-pointer input mapping.
 * Touch coordinates are mapped to the libretro pointer range (-0x7FFF to 0x7FFF).
 */
@Composable
actual fun PlatformDsTouchScreen(
    controller: LibretroController,
    splitY: Int,
    selectedShader: ShaderPreset,
    modifier: Modifier,
) {
    val androidController = controller as? AndroidLibretroController ?: return
    val bitmap by androidController.frameBitmap.collectAsState()

    // Track the rendered image area for touch coordinate mapping
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it.toSize() }
                .pointerInput(splitY) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val bmpHeight = bitmap?.height ?: 0
                        // Map and send initial touch
                        sendPointerFromTouch(
                            controller, down.position.x, down.position.y,
                            canvasSize, bitmap?.width ?: 0, bmpHeight - splitY,
                            fullHeight = bmpHeight,
                            pressed = true,
                        )
                        down.consume()

                        // Track moves and up
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            when (event.type) {
                                PointerEventType.Move -> {
                                    sendPointerFromTouch(
                                        controller, change.position.x, change.position.y,
                                        canvasSize, bitmap?.width ?: 0, bmpHeight - splitY,
                                        fullHeight = bmpHeight,
                                        pressed = true,
                                    )
                                    change.consume()
                                }
                                PointerEventType.Release -> {
                                    controller.setPointer(0, 0, 0, false)
                                    change.consume()
                                    break
                                }
                                else -> break
                            }
                        }
                    }
                },
        ) {
            val bmp = bitmap ?: return@Canvas
            if (bmp.isRecycled || bmp.height <= splitY) return@Canvas

            val srcWidth = bmp.width
            val srcHeight = bmp.height - splitY

            val cWidth = size.width
            val cHeight = size.height

            val scale = min(cWidth / srcWidth.toFloat(), cHeight / srcHeight.toFloat())
            val scaledWidth = (srcWidth * scale).roundToInt()
            val scaledHeight = (srcHeight * scale).roundToInt()
            val offsetX = ((cWidth - scaledWidth) / 2f).roundToInt()
            val offsetY = ((cHeight - scaledHeight) / 2f).roundToInt()

            val dstOffset = IntOffset(offsetX, offsetY)
            val dstSize = IntSize(scaledWidth, scaledHeight)
            val srcSize = IntSize(srcWidth, srcHeight)

            drawImage(
                image = bmp.asImageBitmap(),
                srcOffset = IntOffset(0, splitY),
                srcSize = srcSize,
                dstOffset = dstOffset,
                dstSize = dstSize,
                filterQuality = selectedShader.filterQuality(),
            )

            drawShaderOverlay(selectedShader, dstOffset, dstSize, srcSize)
        }
    }
}

/**
 * Maps a touch position on the canvas to libretro pointer coordinates and sends them.
 *
 * The libretro pointer device uses the range -0x7FFF to 0x7FFF for both axes,
 * where (-0x7FFF, -0x7FFF) is the top-left corner and (0x7FFF, 0x7FFF) is the bottom-right.
 *
 * For DS/3DS touch input, the core expects coordinates relative to the full viewport
 * (both screens stacked). The bottom screen occupies the lower portion of the viewport,
 * so Y is mapped to the bottom half only (0 to 0x7FFF), not the full range.
 * X uses the full range (-0x7FFF to 0x7FFF) as normal.
 *
 * @param fullHeight The full framebuffer height (both screens), used to compute the split ratio.
 */
private fun sendPointerFromTouch(
    controller: LibretroController,
    touchX: Float,
    touchY: Float,
    canvasSize: Size,
    srcWidth: Int,
    srcHeight: Int,
    fullHeight: Int,
    pressed: Boolean,
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0 || srcWidth <= 0 || srcHeight <= 0) return

    // Calculate the rendered image bounds (same as draw logic)
    val scale = min(canvasSize.width / srcWidth.toFloat(), canvasSize.height / srcHeight.toFloat())
    val scaledWidth = srcWidth * scale
    val scaledHeight = srcHeight * scale
    val offsetX = (canvasSize.width - scaledWidth) / 2f
    val offsetY = (canvasSize.height - scaledHeight) / 2f

    // Normalize touch to 0..1 range within the rendered image
    val normalizedX = ((touchX - offsetX) / scaledWidth).coerceIn(0f, 1f)
    val normalizedY = ((touchY - offsetY) / scaledHeight).coerceIn(0f, 1f)

    // X maps to full libretro pointer range: -0x7FFF to 0x7FFF
    val pointerX = ((normalizedX * 2f - 1f) * 0x7FFF).toInt().coerceIn(-0x7FFF, 0x7FFF)

    // Y maps to the bottom portion of the full viewport.
    // The split ratio determines where the bottom screen starts in the full viewport.
    val splitRatio = if (fullHeight > 0) (fullHeight - srcHeight).toFloat() / fullHeight else 0f
    val viewportY = splitRatio + normalizedY * (1f - splitRatio)
    val pointerY = ((viewportY * 2f - 1f) * 0x7FFF).toInt().coerceIn(-0x7FFF, 0x7FFF)

    controller.setPointer(0, pointerX, pointerY, pressed)
}
