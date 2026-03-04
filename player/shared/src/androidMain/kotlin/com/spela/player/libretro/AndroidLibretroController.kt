package com.spela.player.libretro

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import com.spela.player.netplay.InputState
import com.spela.player.netplay.NetplayInputBuffer
import com.spela.player.netplay.NetplayTransport
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.util.FileStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

/**
 * Android implementation of LibretroController using JNI.
 *
 * Manages the emulation thread that calls retro_run at the target FPS,
 * converts native video frames to Bitmap for Compose rendering,
 * and routes audio samples to an AudioTrack.
 */
class AndroidLibretroController(
    private val jni: LibretroJni,
    private val fileStorage: FileStorage,
) : LibretroController {

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    @Volatile
    private var fastForward = false

    /* Netplay lockstep state */
    @Volatile
    private var netplayTransport: NetplayTransport? = null

    @Volatile
    private var netplayInputBuffer: NetplayInputBuffer? = null

    @Volatile
    private var netplayLocalPort: Int = 0

    @Volatile
    private var netplayInputDelay: Int = 0

    @Volatile
    private var netplayDisconnected = false

    /** Callback invoked on the emulation thread when the remote peer times out. */
    var onNetplayPeerTimeout: (() -> Unit)? = null

    /** SurfaceView used for GPU rendering (GLES/Vulkan). Set by VulkanEmulationSurface. */
    @Volatile
    var vulkanSurfaceView: SurfaceView? = null

    private var emulationThread: Thread? = null
    private var targetFps = 60.0

    /* Frame timing for performance stats */
    @Volatile
    private var currentFps = 0f

    @Volatile
    private var currentFrameTime = 0f

    /* Video: emulation thread writes to back buffer, then swaps to front for Compose */
    private val _frameBitmap = MutableStateFlow<Bitmap?>(null)
    val frameBitmap: StateFlow<Bitmap?> = _frameBitmap.asStateFlow()

    /* Audio output — blocking writes provide audio-sync frame pacing */
    private var audioPlayer: AndroidAudioPlayer? = null

    /* Emulation-thread dispatch: some cores (Dolphin) require retro_serialize,
     * retro_unserialize, and retro_serialize_size to run on the same thread as
     * retro_run. This queue lets any thread submit work to the emulation thread. */
    private class EmulationThreadRequest(
        val action: () -> Any?,
        val latch: java.util.concurrent.CountDownLatch = java.util.concurrent.CountDownLatch(1),
        var result: Any? = null,
    )

    private val emulationThreadQueue = java.util.concurrent.LinkedBlockingQueue<EmulationThreadRequest>()

    /* Reusable IntArray buffer for pixel conversion (avoids allocation per frame) */
    private var pixelBuffer = IntArray(0)

    /* Double-buffered bitmaps: emulation thread writes to backBitmap, then publishes it
     * as the new front bitmap. This avoids the data race where Compose reads a bitmap
     * while setPixels() is still writing to it. */
    private var frontBitmap: Bitmap? = null
    private var backBitmap: Bitmap? = null
    private var lastFrameWidth = 0
    private var lastFrameHeight = 0

    /* Reusable byte buffer for video frame data from JNI (avoids NewByteArray per frame) */
    private var videoFrameBuffer = ByteArray(0)

    /* Reusable short buffer for audio samples from JNI (avoids NewShortArray per frame) */
    private var audioSampleBuffer = ShortArray(0)


    /* Handler for posting state updates to the main thread (avoids Compose multithreading crash) */
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun loadCore(corePath: String) {
        jni.nativeSetSystemDir(fileStorage.getBiosDir())
        jni.nativeSetSaveDir(fileStorage.getSavesDir())

        if (!jni.nativeLoadCore(corePath)) {
            throw RuntimeException("Failed to load core: $corePath")
        }
        jni.nativeInit()
    }

    override fun loadGame(gamePath: String) {
        if (!jni.nativeLoadGame(gamePath)) {
            throw RuntimeException("Failed to load game: $gamePath")
        }
        targetFps = jni.nativeGetTargetFps()
        if (targetFps <= 0) targetFps = 60.0
    }

    override fun start() {
        running = true
        paused = false
        emulationThreadQueue.clear()
        netplayDisconnected = false
        mainHandler.post { _physicalControllerActive.value = false }
        lastPhysicalInputNanos = 0L

        val isNetplay = netplayTransport != null
        emulationThread = Thread({
            startAudio()
            if (isNetplay) {
                runNetplayEmulationLoop()
            } else {
                runEmulationLoop()
            }
        }, "SpelaEmulation").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun pause() {
        paused = true
        audioPlayer?.pause()
    }

    override fun resume() {
        paused = false
        audioPlayer?.resume()
    }

    override fun stop() {
        Log.i(TAG, "stop() called")
        running = false
        // Drain pending emulation-thread requests so callers don't block
        while (true) {
            val req = emulationThreadQueue.poll() ?: break
            req.latch.countDown()
        }
        netplayTransport?.disconnect()
        // No timeout: we MUST wait for retro_run() to return before calling
        // nativeUnloadGame(). Heavy cores (N64 Angrylion) may take 10-30s per
        // frame on slow devices. Calling unload while retro_run() is active
        // causes SIGSEGV in the core.
        emulationThread?.join()
        Log.i(TAG, "emulation thread joined")
        emulationThread = null
        audioPlayer?.stop()
        audioPlayer = null
        clearNetplayMode()
        jni.nativeUnloadGame()
        Log.i(TAG, "game unloaded")
        jni.nativeDeinit()
        Log.i(TAG, "core deinitialized")
        // Destroy GPU renderer after core is fully unloaded. nativeDeinit already
        // handled HW render cleanup (context_destroy, hw_vulkan_deinit), so this
        // just tears down the remaining Vulkan infrastructure (device, instance).
        jni.nativeGpuDeinit()
        Log.i(TAG, "GPU renderer destroyed")
        mainHandler.post { _frameBitmap.value = null }
        frontBitmap?.recycle()
        frontBitmap = null
        backBitmap?.recycle()
        backBitmap = null
        lastFrameWidth = 0
        lastFrameHeight = 0
    }

    /**
     * Dispatch a block to the emulation thread and wait for the result.
     * If the emulation thread isn't alive, runs the block on the calling thread.
     */
    private fun <T> runOnEmulationThread(timeoutSeconds: Long = 5, block: () -> T): T? {
        if (emulationThread?.isAlive != true) {
            @Suppress("UNCHECKED_CAST")
            return block()
        }
        val req = EmulationThreadRequest(action = { block() })
        emulationThreadQueue.add(req)
        val completed = req.latch.await(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        @Suppress("UNCHECKED_CAST")
        return if (completed) req.result as? T else null
    }

    override fun supportsSaveStates(): Boolean =
        runOnEmulationThread { jni.nativeSerializeSize() > 0 } ?: true

    override fun serialize(): ByteArray? =
        runOnEmulationThread { jni.nativeSerialize() }

    override fun unserialize(data: ByteArray): Boolean =
        runOnEmulationThread { jni.nativeUnserialize(data) } ?: false

    override fun setFastForward(enabled: Boolean) {
        fastForward = enabled
    }

    override fun performanceStats(): Flow<Pair<Float, Float>> = flow {
        while (running) {
            emit(currentFps to currentFrameTime)
            delay(500)
        }
    }

    /* Physical controller detection: set when hardware gamepad input is received,
     * reset after PHYSICAL_CONTROLLER_TIMEOUT_NS of inactivity. */
    private val _physicalControllerActive = MutableStateFlow(false)
    val physicalControllerActive: StateFlow<Boolean> = _physicalControllerActive.asStateFlow()

    @Volatile
    private var lastPhysicalInputNanos = 0L

    companion object {
        private const val TAG = "LibretroController"
        private const val RETRO_PIXEL_FORMAT_0RGB1555 = 0
        private const val RETRO_PIXEL_FORMAT_XRGB8888 = 1
        private const val RETRO_PIXEL_FORMAT_RGB565 = 2
        private const val PHYSICAL_CONTROLLER_TIMEOUT_NS = 10_000_000_000L // 10 seconds
    }

    /**
     * Set button state from the platform input system.
     */
    fun setButton(port: Int, buttonId: Int, pressed: Boolean) {
        jni.nativeSetInputButton(port, buttonId, pressed)
    }

    fun setAnalog(port: Int, stickIndex: Int, axisId: Int, value: Short) {
        jni.nativeSetInputAnalog(port, stickIndex, axisId, value)
    }

    override fun setPointer(port: Int, x: Int, y: Int, pressed: Boolean) {
        jni.nativeSetInputPointer(port, x, y, pressed)
    }

    override fun setCoreVariable(key: String, value: String) {
        jni.nativeSetCoreVariable(key, value)
    }

    /* GPU Renderer methods */

    fun gpuInit(surface: Any): Boolean = jni.nativeGpuInit(surface)
    fun gpuRender() = jni.nativeGpuRender()
    fun gpuSetShader(shaderId: Int) = jni.nativeGpuSetShader(shaderId)
    fun gpuResize(width: Int, height: Int) = jni.nativeGpuResize(width, height)
    fun gpuDeinit() = jni.nativeGpuDeinit()
    fun gpuSuspend() = jni.nativeGpuSuspend()
    fun gpuResume(surface: Any): Boolean = jni.nativeGpuResume(surface)
    fun gpuIsActive(): Boolean = jni.nativeGpuIsActive()
    fun gpuSetSourceRect(x: Int, y: Int, w: Int, h: Int) = jni.nativeGpuSetSourceRect(x, y, w, h)
    override fun isHwRenderEnabled(): Boolean = jni.nativeIsHwRenderEnabled()

    /**
     * Notify that physical controller input was received.
     * This triggers auto-hide of the touch overlay. If no physical input is
     * received for 10 seconds, the controller is assumed disconnected and
     * the touch overlay reappears.
     */
    fun notifyPhysicalControllerInput() {
        lastPhysicalInputNanos = System.nanoTime()
        if (!_physicalControllerActive.value) {
            _physicalControllerActive.value = true
        }
    }

    /**
     * Initialize audio output on the emulation thread.
     */
    private fun startAudio() {
        try {
            val sampleRate = jni.nativeGetSampleRate().toInt()
            Log.i(TAG, "Core sample rate: $sampleRate Hz")
            if (sampleRate > 0) {
                audioPlayer = AndroidAudioPlayer(sampleRate)
                Log.i(TAG, "AndroidAudioPlayer created at $sampleRate Hz")
            } else {
                Log.w(TAG, "Core reported invalid sample rate: $sampleRate")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create audio player", e)
        }
    }

    /**
     * The main emulation loop. Runs retro_run at the core's target FPS.
     * After each frame, updates the video bitmap (software path) or triggers
     * GPU render (hardware path). Audio is always pushed.
     */
    private fun runEmulationLoop() {
        val frameTimeNs = (1_000_000_000.0 / targetFps).toLong()
        var fpsCounter = 0
        var fpsTimer = System.nanoTime()

        while (running) {
            // Process pending emulation-thread requests (serialize, unserialize, etc.).
            // Must run here (same thread as retro_run) because some cores
            // (Dolphin) deadlock if these are called from another thread.
            val req = emulationThreadQueue.poll()
            if (req != null) {
                req.result = req.action()
                req.latch.countDown()
            }

            if (paused) {
                Thread.sleep(16)
                continue
            }

            val frameStart = System.nanoTime()

            jni.nativeRun()

            // GPU path: frame was already uploaded in video_refresh_callback,
            // just trigger the render pass. Software path: read pixels via JNI.
            if (jni.nativeGpuIsActive()) {
                jni.nativeGpuRender()
            } else {
                updateVideoFrame()
            }

            // Push audio (resampled to 48kHz with dynamic rate control,
            // blocking write as safety backpressure).
            if (!fastForward) {
                pushAudio()
                precisionSleep(frameStart + frameTimeNs)
            } else {
                pushAudioDiscard()
            }

            fpsCounter++
            val now = System.nanoTime()
            val elapsed = now - fpsTimer
            if (elapsed >= 1_000_000_000L) {
                currentFps = fpsCounter.toFloat() * 1_000_000_000L / elapsed
                fpsTimer = now
                fpsCounter = 0
            }

            val frameEnd = System.nanoTime()
            currentFrameTime = (frameEnd - frameStart) / 1_000_000f

            /* Reset physical controller flag if no input received for timeout period */
            val lastInput = lastPhysicalInputNanos
            if (_physicalControllerActive.value && lastInput > 0 &&
                (frameEnd - lastInput) > PHYSICAL_CONTROLLER_TIMEOUT_NS
            ) {
                mainHandler.post { _physicalControllerActive.value = false }
            }
        }
    }

    override fun setNetplayMode(
        transport: NetplayTransport,
        inputBuffer: NetplayInputBuffer,
        localPort: Int,
        inputDelay: Int,
    ) {
        netplayTransport = transport
        netplayInputBuffer = inputBuffer
        netplayLocalPort = localPort
        netplayInputDelay = inputDelay
    }

    override fun clearNetplayMode() {
        netplayTransport = null
        netplayInputBuffer = null
        netplayLocalPort = 0
        netplayInputDelay = 0
        netplayDisconnected = false
        onNetplayPeerTimeout = null
    }

    /**
     * Netplay lockstep emulation loop. Each frame:
     *   1. Capture local input and buffer it for frame F + inputDelay
     *   2. Send local input to remote peer via transport
     *   3. Block until remote input for frame F arrives
     *   4. Apply all player inputs via JNI
     *   5. Run one emulation frame
     *
     * If the remote peer times out, the loop signals disconnection
     * and pauses until either reconnection or exit.
     */
    private fun runNetplayEmulationLoop() {
        val transport = netplayTransport ?: return
        val inputBuffer = netplayInputBuffer ?: return
        val localPort = netplayLocalPort
        val remotePort = if (localPort == 0) 1 else 0
        val inputDelay = netplayInputDelay
        val frameTimeNs = (1_000_000_000.0 / targetFps).toLong()
        val playerCount = 2
        var frameCounter = 0L
        var fpsCounter = 0
        var fpsTimer = System.nanoTime()

        while (running) {
            if (paused) {
                Thread.sleep(16)
                continue
            }

            val frameStart = System.nanoTime()
            val currentFrame = frameCounter

            // 1. Capture local input state from the JNI input table
            val localInput = captureLocalInput(localPort)

            // 2. Buffer local input for frame F + inputDelay and send to remote
            val targetFrame = currentFrame + inputDelay
            runBlocking {
                inputBuffer.setLocalInput(targetFrame, localPort, localInput)
            }
            transport.sendInput(targetFrame, localPort, localInput)

            // 3. Block until we have both players' inputs for the current frame
            val frameInputs = runBlocking {
                inputBuffer.awaitInputsForFrame(currentFrame, playerCount, timeoutMs = 5000)
            }

            if (frameInputs == null) {
                // Remote peer timed out
                if (!netplayDisconnected) {
                    netplayDisconnected = true
                    onNetplayPeerTimeout?.invoke()
                }
                // Pause and wait for reconnection or exit
                Thread.sleep(100)
                continue
            }

            // If we were disconnected but got inputs, we've reconnected
            if (netplayDisconnected) {
                netplayDisconnected = false
            }

            // 4. Apply all player inputs via JNI
            for ((port, input) in frameInputs) {
                applyInputToJni(port, input)
            }

            // 5. Run one emulation frame
            jni.nativeRun()

            if (jni.nativeGpuIsActive()) {
                jni.nativeGpuRender()
            } else {
                updateVideoFrame()
            }
            pushAudio()

            frameCounter++

            // FPS tracking
            fpsCounter++
            val now = System.nanoTime()
            val elapsed = now - fpsTimer
            if (elapsed >= 1_000_000_000L) {
                currentFps = fpsCounter.toFloat() * 1_000_000_000L / elapsed
                fpsTimer = now
                fpsCounter = 0
            }

            val frameEnd = System.nanoTime()
            currentFrameTime = (frameEnd - frameStart) / 1_000_000f

            // Frame pacing (no fast forward in netplay)
            precisionSleep(frameStart + frameTimeNs)
        }
    }

    /**
     * Capture the current local input state from the JNI input table.
     * Returns an InputState with the current button and analog values.
     *
     * We read input state by querying the libretro button IDs (0..15)
     * and the left analog stick axes. The native layer stores these
     * values as set by setButton/setAnalog calls from the touch overlay
     * or physical controller.
     */
    private fun captureLocalInput(port: Int): InputState {
        // Read button state: libretro uses 16 button IDs (0..15)
        var buttons: UShort = 0u
        for (id in 0 until 16) {
            if (jni.nativeGetInputButton(port, id)) {
                buttons = (buttons.toInt() or (1 shl id)).toUShort()
            }
        }
        // Read left analog stick (index=0, id=0 for X, id=1 for Y)
        val analogX = jni.nativeGetInputAnalog(port, 0, 0)
        val analogY = jni.nativeGetInputAnalog(port, 0, 1)
        return InputState(buttons, analogX, analogY)
    }

    /**
     * Apply an InputState to the JNI input table for a given port.
     */
    private fun applyInputToJni(port: Int, input: InputState) {
        val buttons = input.buttonState.toInt()
        for (id in 0 until 16) {
            jni.nativeSetInputButton(port, id, (buttons and (1 shl id)) != 0)
        }
        jni.nativeSetInputAnalog(port, 0, 0, input.analogX)
        jni.nativeSetInputAnalog(port, 0, 1, input.analogY)
    }

    /**
     * Reads the native video frame buffer, converts to packed ARGB ints, and emits a Bitmap.
     * Uses Bitmap.setPixels(IntArray) which takes 0xAARRGGBB packed ints -- no byte-order
     * ambiguity regardless of CPU endianness.
     */
    private fun updateVideoFrame() {
        val width = jni.nativeGetVideoWidth()
        val height = jni.nativeGetVideoHeight()
        if (width <= 0 || height <= 0) return

        val format = jni.nativeGetPixelFormat()
        val pixelCount = width * height
        val bytesPerPixel = if (format == RETRO_PIXEL_FORMAT_XRGB8888) 4 else 2
        val requiredBytes = pixelCount * bytesPerPixel

        // Resize video frame buffer if needed
        if (videoFrameBuffer.size < requiredBytes) {
            videoFrameBuffer = ByteArray(requiredBytes)
        }

        // Fill pre-allocated buffer instead of allocating a new ByteArray per frame
        val copied = jni.nativeFillVideoFrame(videoFrameBuffer)
        if (copied <= 0) return

        // Resize pixel buffer if needed
        if (pixelBuffer.size < pixelCount) {
            pixelBuffer = IntArray(pixelCount)
        }

        convertToPackedArgb(videoFrameBuffer, pixelCount, format, pixelBuffer)

        // Reallocate double buffers if dimensions changed.
        // Don't recycle old bitmaps — Compose may still be drawing the previous
        // front bitmap via _frameBitmap. Let GC collect them instead.
        if (width != lastFrameWidth || height != lastFrameHeight) {
            frontBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            backBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            lastFrameWidth = width
            lastFrameHeight = height
        }

        // Write to back buffer, then swap: Compose only ever reads the front bitmap
        val back = backBitmap ?: return
        back.setPixels(pixelBuffer, 0, width, 0, 0, width, height)
        backBitmap = frontBitmap
        frontBitmap = back
        // Post the bitmap reference to main thread so Compose's SnapshotStateObserver
        // sees the update on the correct thread
        mainHandler.post { _frameBitmap.value = back }
    }

    /**
     * Converts raw pixel data from libretro format to packed ARGB integers (0xAARRGGBB).
     * This format is used by Bitmap.setPixels() and is endian-independent.
     *
     * libretro pixel formats (all stored little-endian in the byte array from JNI):
     *   XRGB8888: 32-bit, value = 0x00RRGGBB, bytes on LE = [BB, GG, RR, 00]
     *   RGB565:   16-bit, value = RRRRRGGGGGGBBBBB, bytes on LE = [lo, hi]
     *   0RGB1555: 16-bit, value = 0RRRRRGGGGGBBBBB, bytes on LE = [lo, hi]
     */
    private fun convertToPackedArgb(data: ByteArray, pixelCount: Int, format: Int, out: IntArray) {
        when (format) {
            RETRO_PIXEL_FORMAT_XRGB8888 -> {
                for (i in 0 until pixelCount) {
                    val si = i * 4
                    val b = data[si].toInt() and 0xFF
                    val g = data[si + 1].toInt() and 0xFF
                    val r = data[si + 2].toInt() and 0xFF
                    // data[si + 3] is X (unused), set A = 0xFF
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            RETRO_PIXEL_FORMAT_RGB565 -> {
                for (i in 0 until pixelCount) {
                    val si = i * 2
                    val lo = data[si].toInt() and 0xFF
                    val hi = data[si + 1].toInt() and 0xFF
                    val pixel = lo or (hi shl 8)
                    val r = ((pixel shr 11) and 0x1F) * 255 / 31
                    val g = ((pixel shr 5) and 0x3F) * 255 / 63
                    val b = (pixel and 0x1F) * 255 / 31
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            RETRO_PIXEL_FORMAT_0RGB1555 -> {
                for (i in 0 until pixelCount) {
                    val si = i * 2
                    val lo = data[si].toInt() and 0xFF
                    val hi = data[si + 1].toInt() and 0xFF
                    val pixel = lo or (hi shl 8)
                    val r = ((pixel shr 10) and 0x1F) * 255 / 31
                    val g = ((pixel shr 5) and 0x1F) * 255 / 31
                    val b = (pixel and 0x1F) * 255 / 31
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
    }

    /**
     * Drain audio samples from the native layer and write to AudioTrack (non-blocking).
     * Frame pacing is handled by precisionSleep on the system clock.
     * The AudioTrack's large buffer (~80ms) absorbs timing jitter.
     */
    private fun pushAudio() {
        if (audioSampleBuffer.size < 4096) {
            audioSampleBuffer = ShortArray(4096)
        }

        val count = jni.nativeFillAudioBuffer(audioSampleBuffer)
        if (count > 0) {
            audioPlayer?.write(audioSampleBuffer, count)
        }
    }

    /**
     * Drain audio samples from the native layer without writing to AudioTrack.
     * Used during fast-forward to prevent the native audio buffer from growing unbounded.
     */
    private fun pushAudioDiscard() {
        if (audioSampleBuffer.size < 4096) {
            audioSampleBuffer = ShortArray(4096)
        }
        jni.nativeFillAudioBuffer(audioSampleBuffer)
    }

    /**
     * High-precision frame pacing fallback. Thread.sleep has imprecise wake times
     * on Android, so we sleep for the bulk of the wait and spin-wait for the last ~2ms.
     */
    private fun precisionSleep(targetNanos: Long) {
        val now = System.nanoTime()
        val remainingNs = targetNanos - now
        if (remainingNs <= 0) return

        // Sleep for most of the duration (leave 2ms margin for spin-wait)
        val sleepMs = remainingNs / 1_000_000 - 2
        if (sleepMs > 0) {
            Thread.sleep(sleepMs)
        }

        // Spin-wait for sub-millisecond precision
        @Suppress("ControlFlowWithEmptyBody")
        while (System.nanoTime() < targetNanos) {
            // Empty body — Thread.onSpinWait() requires API 33
        }
    }

    override fun getSRAM(): ByteArray? = jni.nativeGetSRAM()

    override fun setSRAM(data: ByteArray): Boolean = jni.nativeSetSRAM(data)

    override fun cheatReset() = jni.nativeCheatReset()

    override fun cheatSet(index: Int, enabled: Boolean, code: String) =
        jni.nativeCheatSet(index, enabled, code)
}
