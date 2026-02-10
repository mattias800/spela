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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
@Composable
fun DesktopEmulationSurface(
    controller: DesktopLibretroController,
    modifier: Modifier = Modifier,
    onEscapePressed: (() -> Unit)? = null,
) {
    var currentBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Reusable frame buffers -- only reallocated when dimensions or format change
    val frameBuffers = remember { FrameBuffers() }

    // Frame polling loop -- runs each Compose frame
    LaunchedEffect(controller) {
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
    LaunchedEffect(Unit) {
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
                .focusable()
                .onKeyEvent { event ->
                    // Handle Escape key to toggle overlay
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                        onEscapePressed?.invoke()
                        return@onKeyEvent true
                    }
                    val buttonId = mapKeyToLibretro(event.key)
                    if (buttonId != null) {
                        val pressed = event.type == KeyEventType.KeyDown
                        controller.setButton(0, buttonId, pressed)
                        true
                    } else {
                        false
                    }
                },
        ) {
            val bitmap = currentBitmap ?: return@Canvas
            drawScaledBitmap(bitmap)
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

private fun DrawScope.drawScaledBitmap(bitmap: ImageBitmap) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    val imgWidth = bitmap.width.toFloat()
    val imgHeight = bitmap.height.toFloat()

    // Scale to fit while maintaining aspect ratio
    val scaleX = canvasWidth / imgWidth
    val scaleY = canvasHeight / imgHeight
    val scale = minOf(scaleX, scaleY)

    val dstWidth = (imgWidth * scale).toInt()
    val dstHeight = (imgHeight * scale).toInt()
    val offsetX = ((canvasWidth - dstWidth) / 2).toInt()
    val offsetY = ((canvasHeight - dstHeight) / 2).toInt()

    drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(offsetX, offsetY),
        dstSize = IntSize(dstWidth, dstHeight),
        filterQuality = FilterQuality.Low, // Nearest-neighbor for pixel art
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
 * Map Compose Desktop key codes to libretro joypad button IDs.
 * Returns null if the key is not mapped.
 */
private fun mapKeyToLibretro(key: Key): Int? = when (key) {
    Key.DirectionUp -> LibretroButtons.UP
    Key.DirectionDown -> LibretroButtons.DOWN
    Key.DirectionLeft -> LibretroButtons.LEFT
    Key.DirectionRight -> LibretroButtons.RIGHT
    Key.Z -> LibretroButtons.B
    Key.X -> LibretroButtons.A
    Key.A -> LibretroButtons.Y
    Key.S -> LibretroButtons.X
    Key.Enter -> LibretroButtons.START
    Key.ShiftRight -> LibretroButtons.SELECT
    Key.ShiftLeft -> LibretroButtons.SELECT
    Key.Q -> LibretroButtons.L
    Key.W -> LibretroButtons.R
    Key.One -> LibretroButtons.L2
    Key.Two -> LibretroButtons.R2
    else -> null
}
