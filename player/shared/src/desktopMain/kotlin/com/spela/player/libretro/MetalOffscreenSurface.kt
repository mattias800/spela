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
import com.spela.player.presentation.viewmodel.LibretroAnalog
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
    gamepadPortManager: GamepadPortManager? = null,
) {
    val effectiveMapping = remember(keyMapping) {
        keyMapping ?: defaultMetalKeyMapping
    }
    val analogTracker = remember { AnalogAxisTracker() }

    // Register keyboard as a device on port manager (device ID = -1)
    val keyboardPort = remember(gamepadPortManager) {
        gamepadPortManager?.connectDevice(-1, "Keyboard") ?: 0
    }
    val focusRequester = remember { FocusRequester() }
    val frameBuffers = remember { MetalFrameBuffers() }

    // Tick counter read inside the Canvas draw lambda to trigger draw-phase invalidation
    // each vsync. Reading state during draw avoids a recomposition round-trip, so the
    // latest emulation frame is displayed in the SAME Compose frame (no extra frame delay).
    val frameTick = remember { mutableStateOf(0L) }

    LaunchedEffect(controller) {
        while (true) {
            withFrameNanos { }
            frameTick.value++
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
                    val port = gamepadPortManager?.getPort(-1) ?: 0
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
            // Read frameTick to create a draw-phase observation — the draw lambda
            // re-executes each tick without a full recomposition cycle.
            @Suppress("UNUSED_EXPRESSION")
            frameTick.value

            val frame = controller.latestRenderedFrame ?: return@Canvas

            frameBuffers.ensureCapacity(frame.width, frame.height)
            frameBuffers.bitmap.installPixels(frameBuffers.imageInfo, frame.data, frame.width * 4)
            val bitmap = frameBuffers.bitmap.asComposeImageBitmap()

            val canvasWidth = size.width
            val canvasHeight = size.height

            // Use the core-reported display aspect ratio (DAR) if available.
            // This handles cases like N64 Angrylion outputting 640x240 pixels
            // that should be displayed at 4:3 (pixels aren't square).
            val dar = frame.aspectRatio
            val displayWidth: Float
            val displayHeight: Float
            if (dar > 0f) {
                // DAR-based: compute virtual display dimensions that match
                // the aspect ratio while keeping one dimension from the bitmap
                displayWidth = bitmap.height.toFloat() * dar
                displayHeight = bitmap.height.toFloat()
            } else {
                displayWidth = bitmap.width.toFloat()
                displayHeight = bitmap.height.toFloat()
            }

            val scaleX = canvasWidth / displayWidth
            val scaleY = canvasHeight / displayHeight
            val scale = minOf(scaleX, scaleY)

            val dstWidth = (displayWidth * scale).toInt()
            val dstHeight = (displayHeight * scale).toInt()
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
