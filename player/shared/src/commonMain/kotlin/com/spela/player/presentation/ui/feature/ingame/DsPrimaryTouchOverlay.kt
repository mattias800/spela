package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import com.spela.player.presentation.viewmodel.LibretroController
import kotlin.math.min

/**
 * Transparent touch overlay for DS/3DS games on the primary display.
 *
 * When the full framebuffer (top + bottom screens stacked) is rendered on the primary
 * display, this overlay detects touches in the bottom-screen area and maps them to
 * libretro pointer coordinates. Touches in the top-screen area pass through unconsumed.
 */
@Composable
fun DsPrimaryTouchOverlay(
    controller: LibretroController,
    consoleId: String,
    splitY: Int,
    modifier: Modifier = Modifier,
) {
    // Known framebuffer dimensions for DS consoles
    val (srcWidth, fullHeight) = when (consoleId.lowercase()) {
        "nds" -> 256 to 384
        "3ds" -> 400 to 480
        else -> return
    }

    var containerSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it.toSize() }
            .pointerInput(splitY, srcWidth, fullHeight) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val renderInfo = calcRenderInfo(containerSize, srcWidth, fullHeight)
                    if (renderInfo == null || !isInBottomScreen(down.position, renderInfo, splitY, fullHeight)) {
                        // Touch is outside bottom screen — don't consume, let it pass through
                        return@awaitEachGesture
                    }

                    sendDsPointer(controller, down.position, renderInfo, splitY, fullHeight, pressed = true)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        when (event.type) {
                            PointerEventType.Move -> {
                                sendDsPointer(controller, change.position, renderInfo, splitY, fullHeight, pressed = true)
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
    )
}

private data class RenderInfo(
    val offsetX: Float,
    val offsetY: Float,
    val scaledWidth: Float,
    val scaledHeight: Float,
)

private fun calcRenderInfo(containerSize: Size, srcWidth: Int, fullHeight: Int): RenderInfo? {
    if (containerSize.width <= 0 || containerSize.height <= 0) return null
    val scale = min(containerSize.width / srcWidth.toFloat(), containerSize.height / fullHeight.toFloat())
    val scaledWidth = srcWidth * scale
    val scaledHeight = fullHeight * scale
    return RenderInfo(
        offsetX = (containerSize.width - scaledWidth) / 2f,
        offsetY = (containerSize.height - scaledHeight) / 2f,
        scaledWidth = scaledWidth,
        scaledHeight = scaledHeight,
    )
}

private fun isInBottomScreen(pos: Offset, info: RenderInfo, splitY: Int, fullHeight: Int): Boolean {
    // Calculate where the split line is in screen coordinates
    val splitFraction = splitY.toFloat() / fullHeight
    val splitScreenY = info.offsetY + info.scaledHeight * splitFraction
    return pos.x >= info.offsetX && pos.x <= info.offsetX + info.scaledWidth &&
            pos.y >= splitScreenY && pos.y <= info.offsetY + info.scaledHeight
}

private fun sendDsPointer(
    controller: LibretroController,
    pos: Offset,
    info: RenderInfo,
    splitY: Int,
    fullHeight: Int,
    pressed: Boolean,
) {
    // Normalize touch position within the full rendered game area
    val normalizedX = ((pos.x - info.offsetX) / info.scaledWidth).coerceIn(0f, 1f)
    val normalizedY = ((pos.y - info.offsetY) / info.scaledHeight).coerceIn(0f, 1f)

    // X maps to full libretro pointer range: -0x7FFF to 0x7FFF
    val pointerX = ((normalizedX * 2f - 1f) * 0x7FFF).toInt().coerceIn(-0x7FFF, 0x7FFF)

    // Y maps to the full viewport (both screens), which is what the core expects
    val pointerY = ((normalizedY * 2f - 1f) * 0x7FFF).toInt().coerceIn(-0x7FFF, 0x7FFF)

    controller.setPointer(0, pointerX, pointerY, pressed)
}
