package com.spela.player.libretro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spela.player.domain.model.WidescreenMode
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.feature.shader.filterQuality
import com.spela.player.presentation.viewmodel.LibretroAnalog
import com.spela.player.presentation.viewmodel.LibretroButtons
import com.spela.player.presentation.viewmodel.LibretroPixelFormat
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Compose Desktop surface that renders libretro video frames and handles keyboard input.
 *
 * Polls frames from [DesktopLibretroController] each display frame,
 * converts the raw pixel data to an [ImageBitmap], and draws it scaled
 * to fill the canvas while maintaining aspect ratio.
 */
/**
 * @param keyMapping Maps platform key codes to libretro button IDs.
 *   If null, the hardcoded default mapping is used.
 */
@Composable
fun DesktopEmulationSurface(
    controller: DesktopLibretroController,
    selectedShader: ShaderPreset = ShaderPreset.NONE,
    widescreenMode: WidescreenMode = WidescreenMode.NATIVE,
    modifier: Modifier = Modifier,
    onEscapePressed: (() -> Unit)? = null,
    keyMapping: Map<Int, Int>? = null,
    gamepadPortManager: GamepadPortManager? = null,
    overlayVisible: Boolean = false,
) {
    val effectiveMapping = remember(keyMapping) {
        keyMapping ?: defaultKeyMapping
    }
    val analogTracker = remember { AnalogAxisTracker() }

    // Register keyboard as a device on port manager (device ID = -1)
    val keyboardPort = remember(gamepadPortManager) {
        gamepadPortManager?.connectDevice(-1, "Keyboard") ?: 0
    }
    var currentBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Reusable frame buffers -- only reallocated when dimensions or format change
    val frameBuffers = remember { FrameBuffers() }

    // Frame polling loop -- runs each Compose frame
    // Clear stale frame from previous game before starting
    LaunchedEffect(controller) {
        currentBitmap = null
        while (true) {
            withFrameNanos { }
            val frameData = controller.getVideoFrame() ?: continue
            val width = controller.getVideoWidth()
            val height = controller.getVideoHeight()
            if (width <= 0 || height <= 0) continue

            val pixelFormat = controller.getPixelFormat()
            frameBuffers.ensureCapacity(width, height, pixelFormat)
            convertFrameInPlace(frameData, width, height, pixelFormat, frameBuffers.pixelBuffer)
            frameBuffers.bitmap.installPixels(frameBuffers.imageInfo, frameBuffers.pixelBuffer, width * 4)
            currentBitmap = frameBuffers.bitmap.asComposeImageBitmap()
        }
    }

    // Request focus so we receive key events, and periodically re-request
    // to recover from any focus-stealing UI elements (e.g. overlays).
    // While the in-game pause overlay is open we deliberately stop grabbing
    // focus so the overlay menu can hold it and be navigated (#1211).
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) return@LaunchedEffect
        while (true) {
            focusRequester.requestFocus()
            delay(500)
        }
    }

    // Fading hint shown briefly after the surface appears
    var showEscHint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3000)
        showEscHint = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                // Non-focusable while the overlay is open so focus stays in the
                // overlay menu rather than bouncing back to the surface (#1211).
                .focusable(enabled = !overlayVisible)
                .pointerInput(controller) {
                    // Forward Compose pointer events to the libretro
                    // RETRO_DEVICE_MOUSE pipeline (#857). Mirror of the
                    // MetalOffscreenSurface block — same scaling logic
                    // but reads frame size via controller.getVideoWidth/
                    // getVideoHeight since this surface doesn't keep a
                    // RenderedFrame snapshot around.
                    awaitPointerEventScope {
                        var prev: Offset? = null
                        var leftHeld = false
                        var rightHeld = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val frameW = controller.getVideoWidth()
                            val frameH = controller.getVideoHeight()
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            val scaleX = if (frameW > 0 && canvasW > 0f) frameW.toFloat() / canvasW else 1f
                            val scaleY = if (frameH > 0 && canvasH > 0f) frameH.toFloat() / canvasH else 1f
                            when (event.type) {
                                PointerEventType.Move -> {
                                    val p = prev
                                    if (p != null) {
                                        val dx = ((change.position.x - p.x) * scaleX).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                        val dy = ((change.position.y - p.y) * scaleY).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                        if (dx != 0.toShort() || dy != 0.toShort()) {
                                            controller.setMouse(0, dx, dy, leftHeld, rightHeld)
                                        }
                                    }
                                    prev = change.position
                                }
                                PointerEventType.Press -> {
                                    leftHeld = event.buttons.isPrimaryPressed
                                    rightHeld = event.buttons.isSecondaryPressed
                                    controller.setMouse(0, 0, 0, leftHeld, rightHeld)
                                    prev = change.position
                                }
                                PointerEventType.Release -> {
                                    leftHeld = event.buttons.isPrimaryPressed
                                    rightHeld = event.buttons.isSecondaryPressed
                                    controller.setMouse(0, 0, 0, leftHeld, rightHeld)
                                }
                                PointerEventType.Enter, PointerEventType.Exit -> {
                                    prev = null
                                }
                            }
                        }
                    }
                }
                .onPreviewKeyEvent { event ->
                    // While the pause overlay is open, yield all keys so the
                    // overlay menu's focus navigation (and its own Escape/back
                    // handling) gets them instead of the emulator (#1211).
                    if (overlayVisible) return@onPreviewKeyEvent false
                    // Handle Escape key to toggle overlay
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                        onEscapePressed?.invoke()
                        return@onPreviewKeyEvent true
                    }
                    val keyCode = event.key.keyCode.toInt()
                    val port = gamepadPortManager?.getPort(-1) ?: 0
                    // Use port-specific mapping from GamepadPortManager if available
                    val buttonId = gamepadPortManager?.mapKeyToLibretro(port, keyCode)
                        ?: effectiveMapping[keyCode]
                    if (buttonId != null) {
                        val pressed = event.type == KeyEventType.KeyDown
                        val axisUpdate = analogTracker.update(buttonId, pressed)
                        if (axisUpdate != null) {
                            controller.setAnalog(port, axisUpdate.stickIndex, axisUpdate.axisId, axisUpdate.value)
                        } else {
                            controller.setButton(port, buttonId, pressed)
                        }
                        gamepadPortManager?.reportActivity(port)
                        true
                    } else {
                        false
                    }
                },
        ) {
            val bitmap = currentBitmap ?: return@Canvas
            drawScaledBitmap(bitmap, selectedShader, widescreenMode)
        }

        // "Press Esc to pause" hint that fades after a few seconds
        AnimatedVisibility(
            visible = showEscHint,
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
        ) {
            Text(
                text = "Press Esc to pause",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private fun DrawScope.drawScaledBitmap(
    bitmap: ImageBitmap,
    shader: ShaderPreset,
    widescreenMode: WidescreenMode,
) {
    val scaled = computeScaledFrame(
        srcWidth = bitmap.width,
        srcHeight = bitmap.height,
        canvasWidth = size.width,
        canvasHeight = size.height,
        displayAspectRatio = widescreenMode.displayAspectRatio,
        scaleMode = if (widescreenMode.fillsCanvas) FrameScaleMode.FILL else FrameScaleMode.FIT,
    )

    val dstOffset = IntOffset(scaled.offsetX, scaled.offsetY)
    val dstSize = IntSize(scaled.width, scaled.height)
    val srcSize = IntSize(bitmap.width, bitmap.height)

    // CRT / scanlines / LCD render as a single GPU fragment pass (#1209) instead
    // of per-frame Compose geometry that scaled with output resolution. Returns
    // false for pass-through presets and on failure, where we draw plainly.
    if (drawWithDesktopShader(bitmap, shader, dstOffset, dstSize, srcSize)) return

    drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = srcSize,
        dstOffset = dstOffset,
        dstSize = dstSize,
        filterQuality = shader.filterQuality(),
    )
}

/**
 * Holds reusable buffers for frame conversion, avoiding per-frame allocation.
 * Only reallocates when frame dimensions or pixel format change.
 */
private class FrameBuffers {
    var bitmap = Bitmap()
        private set
    var imageInfo = ImageInfo(0, 0, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        private set
    var pixelBuffer = ByteArray(0)
        private set

    private var lastWidth = 0
    private var lastHeight = 0
    private var lastFormat = -1

    fun ensureCapacity(width: Int, height: Int, pixelFormat: Int) {
        if (width == lastWidth && height == lastHeight && pixelFormat == lastFormat) return
        lastWidth = width
        lastHeight = height
        lastFormat = pixelFormat
        imageInfo = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        bitmap = Bitmap().apply { allocPixels(imageInfo) }
        pixelBuffer = ByteArray(width * height * 4)
    }
}

/**
 * Convert raw libretro frame data into a pre-allocated BGRA byte buffer (in-place).
 */
private fun convertFrameInPlace(
    data: ByteArray,
    width: Int,
    height: Int,
    pixelFormat: Int,
    out: ByteArray,
) {
    when (pixelFormat) {
        LibretroPixelFormat.XRGB8888 -> convertXRGB8888toBGRA(data, out)
        LibretroPixelFormat.RGB565 -> convertRGB565toBGRA(data, width * height, out)
        else -> convertRGB1555toBGRA(data, width * height, out) // 0RGB1555
    }
}

/**
 * XRGB8888 -> BGRA8888 in-place for Skia.
 *
 * libretro XRGB8888 stores 32-bit values as 0x00RRGGBB. On little-endian (all
 * supported platforms), the JNI byte array layout is [BB, GG, RR, XX] per pixel.
 * Skia BGRA_8888 expects bytes [BB, GG, RR, AA]. So we just copy and set alpha.
 */
private fun convertXRGB8888toBGRA(data: ByteArray, out: ByteArray) {
    var i = 0
    while (i < data.size - 3) {
        out[i] = data[i]           // B
        out[i + 1] = data[i + 1]   // G
        out[i + 2] = data[i + 2]   // R
        out[i + 3] = 0xFF.toByte() // A (replace X)
        i += 4
    }
}

/** RGB565 -> BGRA8888 in-place */
private fun convertRGB565toBGRA(data: ByteArray, totalPixels: Int, out: ByteArray) {
    var srcIdx = 0
    var dstIdx = 0
    for (p in 0 until totalPixels) {
        if (srcIdx + 1 >= data.size) break
        val lo = data[srcIdx].toInt() and 0xFF
        val hi = data[srcIdx + 1].toInt() and 0xFF
        val pixel = lo or (hi shl 8)

        val r = ((pixel shr 11) and 0x1F) * 255 / 31
        val g = ((pixel shr 5) and 0x3F) * 255 / 63
        val b = (pixel and 0x1F) * 255 / 31

        out[dstIdx] = b.toByte()
        out[dstIdx + 1] = g.toByte()
        out[dstIdx + 2] = r.toByte()
        out[dstIdx + 3] = 0xFF.toByte()

        srcIdx += 2
        dstIdx += 4
    }
}

/** 0RGB1555 -> BGRA8888 in-place */
private fun convertRGB1555toBGRA(data: ByteArray, totalPixels: Int, out: ByteArray) {
    var srcIdx = 0
    var dstIdx = 0
    for (p in 0 until totalPixels) {
        if (srcIdx + 1 >= data.size) break
        val lo = data[srcIdx].toInt() and 0xFF
        val hi = data[srcIdx + 1].toInt() and 0xFF
        val pixel = lo or (hi shl 8)

        val r = ((pixel shr 10) and 0x1F) * 255 / 31
        val g = ((pixel shr 5) and 0x1F) * 255 / 31
        val b = (pixel and 0x1F) * 255 / 31

        out[dstIdx] = b.toByte()
        out[dstIdx + 1] = g.toByte()
        out[dstIdx + 2] = r.toByte()
        out[dstIdx + 3] = 0xFF.toByte()

        srcIdx += 2
        dstIdx += 4
    }
}

/**
 * The hardcoded default key mapping for Desktop.
 * Maps platform key codes (Key.keyCode as Int) to libretro button IDs.
 */
private val defaultKeyMapping: Map<Int, Int> = mapOf(
    Key.DirectionUp.keyCode.toInt() to LibretroButtons.UP,
    Key.DirectionDown.keyCode.toInt() to LibretroButtons.DOWN,
    Key.DirectionLeft.keyCode.toInt() to LibretroButtons.LEFT,
    Key.DirectionRight.keyCode.toInt() to LibretroButtons.RIGHT,
    Key.Z.keyCode.toInt() to LibretroButtons.B,
    Key.X.keyCode.toInt() to LibretroButtons.A,
    Key.A.keyCode.toInt() to LibretroButtons.Y,
    Key.S.keyCode.toInt() to LibretroButtons.X,
    Key.Enter.keyCode.toInt() to LibretroButtons.START,
    Key.ShiftRight.keyCode.toInt() to LibretroButtons.SELECT,
    Key.ShiftLeft.keyCode.toInt() to LibretroButtons.SELECT,
    Key.Q.keyCode.toInt() to LibretroButtons.L,
    Key.W.keyCode.toInt() to LibretroButtons.R,
    Key.One.keyCode.toInt() to LibretroButtons.L2,
    Key.Two.keyCode.toInt() to LibretroButtons.R2,
    // Analog sticks
    Key.T.keyCode.toInt() to LibretroAnalog.LEFT_STICK_UP,
    Key.G.keyCode.toInt() to LibretroAnalog.LEFT_STICK_DOWN,
    Key.F.keyCode.toInt() to LibretroAnalog.LEFT_STICK_LEFT,
    Key.H.keyCode.toInt() to LibretroAnalog.LEFT_STICK_RIGHT,
    Key.I.keyCode.toInt() to LibretroAnalog.RIGHT_STICK_UP,
    Key.K.keyCode.toInt() to LibretroAnalog.RIGHT_STICK_DOWN,
    Key.J.keyCode.toInt() to LibretroAnalog.RIGHT_STICK_LEFT,
    Key.L.keyCode.toInt() to LibretroAnalog.RIGHT_STICK_RIGHT,
)

/**
 * The hardcoded default key mapping as retroButtonId -> platformKeyCode.
 * Used by [KeyMappingRepository] as the fallback default for Desktop.
 */
val desktopDefaultRetroMapping: Map<Int, Int> = mapOf(
    LibretroButtons.UP to Key.DirectionUp.keyCode.toInt(),
    LibretroButtons.DOWN to Key.DirectionDown.keyCode.toInt(),
    LibretroButtons.LEFT to Key.DirectionLeft.keyCode.toInt(),
    LibretroButtons.RIGHT to Key.DirectionRight.keyCode.toInt(),
    LibretroButtons.B to Key.Z.keyCode.toInt(),
    LibretroButtons.A to Key.X.keyCode.toInt(),
    LibretroButtons.Y to Key.A.keyCode.toInt(),
    LibretroButtons.X to Key.S.keyCode.toInt(),
    LibretroButtons.START to Key.Enter.keyCode.toInt(),
    LibretroButtons.SELECT to Key.ShiftRight.keyCode.toInt(),
    LibretroButtons.L to Key.Q.keyCode.toInt(),
    LibretroButtons.R to Key.W.keyCode.toInt(),
    LibretroButtons.L2 to Key.One.keyCode.toInt(),
    LibretroButtons.R2 to Key.Two.keyCode.toInt(),
    // Analog sticks
    LibretroAnalog.LEFT_STICK_UP to Key.T.keyCode.toInt(),
    LibretroAnalog.LEFT_STICK_DOWN to Key.G.keyCode.toInt(),
    LibretroAnalog.LEFT_STICK_LEFT to Key.F.keyCode.toInt(),
    LibretroAnalog.LEFT_STICK_RIGHT to Key.H.keyCode.toInt(),
    LibretroAnalog.RIGHT_STICK_UP to Key.I.keyCode.toInt(),
    LibretroAnalog.RIGHT_STICK_DOWN to Key.K.keyCode.toInt(),
    LibretroAnalog.RIGHT_STICK_LEFT to Key.J.keyCode.toInt(),
    LibretroAnalog.RIGHT_STICK_RIGHT to Key.L.keyCode.toInt(),
)
