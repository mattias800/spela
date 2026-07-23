package com.spela.player.libretro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposeImageBitmap
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.WidescreenMode
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
    widescreenMode: WidescreenMode = WidescreenMode.NATIVE,
    modifier: Modifier = Modifier,
    onEscapePressed: (() -> Unit)? = null,
    keyMapping: Map<Int, Int>? = null,
    gamepadPortManager: GamepadPortManager? = null,
    overlayVisible: Boolean = false,
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

    // Request focus for keyboard input. Yield while the pause overlay is open
    // so its menu can hold focus and be navigated (#1211).
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) return@LaunchedEffect
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
                .pointerInput(controller) {
                    // Forward Compose pointer events to the libretro
                    // RETRO_DEVICE_MOUSE pipeline (#857). The native
                    // ingest + JNI binding + setMouse setter all
                    // existed; what was missing was the UI layer
                    // calling them. ScummVM and other mouse-driven
                    // cores receive nothing without this block.
                    //
                    // Deltas are scaled from canvas-pixel space to
                    // core-framebuffer-pixel space so cursor speed
                    // feels correct regardless of how the user
                    // resized the window.
                    awaitPointerEventScope {
                        var prev: Offset? = null
                        var leftHeld = false
                        var rightHeld = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val frame = controller.latestRenderedFrame
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            val scaleX = if (frame != null && canvasW > 0f) frame.width.toFloat() / canvasW else 1f
                            val scaleY = if (frame != null && canvasH > 0f) frame.height.toFloat() / canvasH else 1f
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
                                    // Reset on enter so the first delta after
                                    // re-entering the canvas isn't a giant jump
                                    // from wherever the cursor was off-canvas.
                                    prev = null
                                }
                            }
                        }
                    }
                }
                .onSizeChanged { size ->
                    // Report canvas size to GPU renderer so shaders render
                    // at display resolution instead of a static multiplier
                    controller.gpuResize(size.width, size.height)
                }
                .focusRequester(focusRequester)
                // Non-focusable + key-yielding while the overlay is open so its
                // menu owns focus and input (#1211).
                .focusable(enabled = !overlayVisible)
                .onPreviewKeyEvent { event ->
                    if (overlayVisible) return@onPreviewKeyEvent false
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

            // Use the core-reported display aspect ratio (DAR) if available.
            // This handles cases like N64 Angrylion outputting 640x240 pixels
            // that should be displayed at 4:3 (pixels aren't square).
            val dar = if (widescreenMode == WidescreenMode.NATIVE) {
                frame.aspectRatio
            } else {
                widescreenMode.displayAspectRatio
            }
            val scaled = computeScaledFrame(
                srcWidth = bitmap.width,
                srcHeight = bitmap.height,
                canvasWidth = size.width,
                canvasHeight = size.height,
                displayAspectRatio = dar,
                scaleMode = widescreenMode.frameScaleMode(),
            )

            // Nearest-neighbor when no shader (pixel-perfect), bilinear otherwise
            // (shader already applied filtering in the GPU pass)
            val quality = if (selectedShader == ShaderPreset.NONE) FilterQuality.None else FilterQuality.Medium
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(scaled.offsetX, scaled.offsetY),
                dstSize = IntSize(scaled.width, scaled.height),
                filterQuality = quality,
            )
        }

        AnimatedVisibility(
            visible = showEscHint,
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
        ) {
            PauseHintText()
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
