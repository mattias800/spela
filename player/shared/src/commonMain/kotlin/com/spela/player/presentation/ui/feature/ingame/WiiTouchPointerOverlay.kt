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
 * Transparent touch overlay that drives the Wii IR pointer (#1560) on the
 * primary display, for devices without an active secondary screen.
 *
 * Wii is a single full-screen game, so the whole letterboxed video viewport
 * is the pointer surface — no bottom-screen sub-region logic like
 * [DsPrimaryTouchOverlay]. Absolute touch position maps to the libretro
 * pointer range `[-0x7FFF, 0x7FFF]`, which the core reads via
 * `RETRO_DEVICE_POINTER` when `dolphin_ir_mode=2`.
 *
 * On release the pointer holds its last position (only the pressed bit is
 * cleared) — a touch surface has no "pointed away from the sensor bar"
 * concept, so snapping the reticle away would be jarring (user decision,
 * #1560).
 */
@Composable
fun WiiTouchPointerOverlay(
    controller: LibretroController,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    var containerSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it.toSize() }
            .pointerInput(aspectRatio) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val info = calcWiiRenderInfo(containerSize, aspectRatio)
                        ?: return@awaitEachGesture
                    var (lastX, lastY) = wiiPointerCoords(down.position, info)
                    controller.setPointer(0, lastX, lastY, true)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        when (event.type) {
                            PointerEventType.Move -> {
                                val (x, y) = wiiPointerCoords(change.position, info)
                                lastX = x
                                lastY = y
                                controller.setPointer(0, x, y, true)
                                change.consume()
                            }
                            PointerEventType.Release -> {
                                // Hold last position; clear only the pressed bit.
                                controller.setPointer(0, lastX, lastY, false)
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

internal data class WiiRenderInfo(
    val offsetX: Float,
    val offsetY: Float,
    val scaledWidth: Float,
    val scaledHeight: Float,
)

/**
 * The letterboxed rect of the Wii video inside [containerSize], for a source
 * [aspectRatio] (width / height). Returns null when either the container or
 * aspect is not yet measured/valid. Same min-scale, centered-offset fit as
 * [DsPrimaryTouchOverlay]'s private helper, generalized to an aspect ratio.
 */
internal fun calcWiiRenderInfo(containerSize: Size, aspectRatio: Float): WiiRenderInfo? {
    if (containerSize.width <= 0f || containerSize.height <= 0f || aspectRatio <= 0f) return null
    // Source dimensions with the given aspect, normalized to height 1.
    val srcWidth = aspectRatio
    val srcHeight = 1f
    val scale = min(containerSize.width / srcWidth, containerSize.height / srcHeight)
    val scaledWidth = srcWidth * scale
    val scaledHeight = srcHeight * scale
    return WiiRenderInfo(
        offsetX = (containerSize.width - scaledWidth) / 2f,
        offsetY = (containerSize.height - scaledHeight) / 2f,
        scaledWidth = scaledWidth,
        scaledHeight = scaledHeight,
    )
}

/**
 * Maps a touch [pos] to libretro pointer coordinates in `[-0x7FFF, 0x7FFF]`
 * on both axes, clamped to the letterboxed [info] rect.
 */
internal fun wiiPointerCoords(pos: Offset, info: WiiRenderInfo): Pair<Int, Int> {
    val normalizedX = ((pos.x - info.offsetX) / info.scaledWidth).coerceIn(0f, 1f)
    val normalizedY = ((pos.y - info.offsetY) / info.scaledHeight).coerceIn(0f, 1f)
    val pointerX = ((normalizedX * 2f - 1f) * 0x7FFF).toInt().coerceIn(-0x7FFF, 0x7FFF)
    val pointerY = ((normalizedY * 2f - 1f) * 0x7FFF).toInt().coerceIn(-0x7FFF, 0x7FFF)
    return pointerX to pointerY
}
