package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.LibretroController

/**
 * Wii IR pointer surface for the secondary screen (#1581), the AYN Thor's
 * clamshell-open experience: aim on the second touchscreen while the game
 * plays on the main screen.
 *
 * Absolute touch → IR position, reusing the same aspect-letterboxed mapping
 * as the primary-screen [WiiTouchPointerOverlay] ([calcWiiRenderInfo] /
 * [wiiPointerCoords]), and the same hold-last-position release semantics
 * (#1560). Unlike [SecondaryTrackpadTab] (relative dx/dy for a mouse), this
 * sends absolute pointer coordinates.
 */
@Composable
fun SecondaryWiiPointerTab(
    controller: LibretroController,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    var containerSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(SpSpacing.Medium)
            .background(SpColor.SurfaceVariant)
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
