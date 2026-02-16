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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Compose Desktop surface that displays Metal-rendered frames.
 *
 * The Metal GPU renderer runs in offscreen mode: it applies shader effects
 * to the game texture on the GPU, then reads back BGRA pixels. This surface
 * polls the rendered frames and displays them in a Compose Canvas.
 */
@Composable
fun MetalOffscreenSurface(
    controller: DesktopLibretroController,
    selectedShader: ShaderPreset = ShaderPreset.NONE,
    modifier: Modifier = Modifier,
    onEscapePressed: (() -> Unit)? = null,
    keyMapping: Map<Int, Int>? = null,
) {
    val effectiveMapping = remember(keyMapping) {
        keyMapping ?: defaultMetalKeyMapping
    }
    var currentBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val focusRequester = remember { FocusRequester() }

    val frameBuffers = remember { MetalFrameBuffers() }

    // Frame polling loop: each Compose frame, get Metal-rendered BGRA pixels
    LaunchedEffect(controller) {
        while (true) {
            withFrameNanos { }
            if (!controller.isGpuActive()) continue

            val width = controller.getVideoWidth()
            val height = controller.getVideoHeight()
            if (width <= 0 || height <= 0) continue

            frameBuffers.ensureCapacity(width, height)
            val bytesWritten = controller.gpuRenderToBgra(frameBuffers.pixelBuffer)
            if (bytesWritten <= 0) continue

            frameBuffers.bitmap.installPixels(frameBuffers.imageInfo, frameBuffers.pixelBuffer, width * 4)
            currentBitmap = frameBuffers.bitmap.asComposeImageBitmap()
        }
    }

    // Request focus for keyboard input
    LaunchedEffect(Unit) {
        while (true) {
            focusRequester.requestFocus()
            delay(500)
        }
    }

    // Fading hint
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
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                        onEscapePressed?.invoke()
                        return@onPreviewKeyEvent true
                    }
                    val keyCode = event.key.keyCode.toInt()
                    val buttonId = effectiveMapping[keyCode]
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

            val canvasWidth = size.width
            val canvasHeight = size.height
            val imgWidth = bitmap.width.toFloat()
            val imgHeight = bitmap.height.toFloat()

            val scaleX = canvasWidth / imgWidth
            val scaleY = canvasHeight / imgHeight
            val scale = minOf(scaleX, scaleY)

            val dstWidth = (imgWidth * scale).toInt()
            val dstHeight = (imgHeight * scale).toInt()
            val offsetX = ((canvasWidth - dstWidth) / 2).toInt()
            val offsetY = ((canvasHeight - dstHeight) / 2).toInt()

            // Shader effects already applied by Metal -- use bilinear upscale
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(offsetX, offsetY),
                dstSize = IntSize(dstWidth, dstHeight),
                filterQuality = FilterQuality.Medium,
            )
        }

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

private class MetalFrameBuffers {
    var bitmap = Bitmap()
        private set
    var imageInfo = ImageInfo(0, 0, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        private set
    var pixelBuffer = ByteArray(0)
        private set

    private var lastWidth = 0
    private var lastHeight = 0

    fun ensureCapacity(width: Int, height: Int) {
        if (width == lastWidth && height == lastHeight) return
        lastWidth = width
        lastHeight = height
        imageInfo = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        bitmap = Bitmap().apply { allocPixels(imageInfo) }
        pixelBuffer = ByteArray(width * height * 4)
    }
}

/**
 * Default key mapping (same as DesktopEmulationSurface).
 */
private val defaultMetalKeyMapping: Map<Int, Int> = mapOf(
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
)
