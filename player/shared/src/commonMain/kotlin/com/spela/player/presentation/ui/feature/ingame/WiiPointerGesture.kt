package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.spela.player.presentation.viewmodel.LibretroButtons
import com.spela.player.presentation.viewmodel.LibretroController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val WII_POINTER_TAP_TIMEOUT_MS = 200L
internal const val WII_POINTER_TAP_MOVEMENT_THRESHOLD_PX = 10f
private const val WII_POINTER_A_PULSE_MS = 64L
private const val WII_POINTER_PORT = 0

internal fun Modifier.wiiPointerInput(
    controller: LibretroController,
    aspectRatio: Float,
    containerSize: Size,
): Modifier = pointerInput(controller, aspectRatio, containerSize) {
    val aPulseMutex = Mutex()
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val info = calcWiiRenderInfo(containerSize, aspectRatio)
                ?: return@awaitEachGesture
            handleWiiPointerGesture(this@coroutineScope, aPulseMutex, controller, info, down)
        }
    }
}

private suspend fun AwaitPointerEventScope.handleWiiPointerGesture(
    pulseScope: CoroutineScope,
    aPulseMutex: Mutex,
    controller: LibretroController,
    info: WiiRenderInfo,
    down: PointerInputChange,
) {
    down.consume()

    val startPosition = down.position
    val startTime = down.uptimeMillis
    val pointerId = down.id
    var lastEventTime = startTime
    var lastPosition = startPosition
    var multiTouchSeen = false
    var movementExceeded = false
    var pointerAiming = false
    var lastX = 0
    var lastY = 0

    fun startPointerAim(position: Offset) {
        val (x, y) = wiiPointerCoords(position, info)
        lastX = x
        lastY = y
        controller.setPointer(WII_POINTER_PORT, x, y, pressed = true)
        pointerAiming = true
    }

    try {
        while (true) {
            val event = awaitPointerEvent()
            lastEventTime = event.latestUptimeMillis(lastEventTime)
            val pointerChange = event.changes.firstOrNull { it.id == pointerId }
            val activePointer = pointerChange?.takeIf { it.pressed }
            multiTouchSeen = multiTouchSeen || event.changes.any { it.id != pointerId && it.pressed }

            if (activePointer == null) {
                val releasePosition = pointerChange?.position ?: lastPosition
                val elapsedMs = lastEventTime - startTime
                event.consumeAllChanges()

                if (pointerAiming) {
                    controller.setPointer(WII_POINTER_PORT, lastX, lastY, pressed = false)
                    pointerAiming = false
                } else if (isWiiPointerTap(
                        startPosition = startPosition,
                        endPosition = releasePosition,
                        elapsedMs = elapsedMs,
                        movementExceeded = movementExceeded,
                        multiTouchSeen = multiTouchSeen,
                    )
                ) {
                    pulseScope.launch {
                        aPulseMutex.withLock {
                            pulseWiiPointerA(controller)
                        }
                    }
                }
                return
            }

            if (pointerAiming) {
                val (x, y) = wiiPointerCoords(activePointer.position, info)
                lastX = x
                lastY = y
                controller.setPointer(WII_POINTER_PORT, x, y, pressed = true)
                event.consumeAllChanges()
                continue
            }

            if (!multiTouchSeen) {
                lastPosition = activePointer.position
                movementExceeded = movementExceeded ||
                    hasWiiPointerMovedBeyondTapThreshold(startPosition, lastPosition)

                if (movementExceeded) {
                    startPointerAim(lastPosition)
                    event.consumeAllChanges()
                    continue
                }
            }

            event.consumeAllChanges()
        }
    } finally {
        if (pointerAiming) {
            controller.setPointer(WII_POINTER_PORT, lastX, lastY, pressed = false)
        }
    }
}

private suspend fun pulseWiiPointerA(controller: LibretroController) {
    controller.setButton(WII_POINTER_PORT, LibretroButtons.A, pressed = true)
    try {
        delay(WII_POINTER_A_PULSE_MS)
    } finally {
        controller.setButton(WII_POINTER_PORT, LibretroButtons.A, pressed = false)
    }
}

internal fun isWiiPointerTap(
    startPosition: Offset,
    endPosition: Offset,
    elapsedMs: Long,
    movementExceeded: Boolean,
    multiTouchSeen: Boolean,
): Boolean = elapsedMs < WII_POINTER_TAP_TIMEOUT_MS &&
    !movementExceeded &&
    !multiTouchSeen &&
    !hasWiiPointerMovedBeyondTapThreshold(startPosition, endPosition)

internal fun hasWiiPointerMovedBeyondTapThreshold(
    startPosition: Offset,
    currentPosition: Offset,
    thresholdPx: Float = WII_POINTER_TAP_MOVEMENT_THRESHOLD_PX,
): Boolean {
    val dx = currentPosition.x - startPosition.x
    val dy = currentPosition.y - startPosition.y
    return dx * dx + dy * dy > thresholdPx * thresholdPx
}

private fun PointerEvent.latestUptimeMillis(fallback: Long): Long =
    changes.maxOfOrNull { it.uptimeMillis } ?: fallback

private fun PointerEvent.consumeAllChanges() {
    changes.forEach { it.consume() }
}
