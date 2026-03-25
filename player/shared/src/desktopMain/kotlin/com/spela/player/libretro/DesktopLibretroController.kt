package com.spela.player.libretro

import com.spela.player.netplay.InputState
import com.spela.player.netplay.NetplayInputBuffer
import com.spela.player.netplay.NetplayTransport
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.util.FileStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

/**
 * Desktop (JVM) implementation of LibretroController.
 *
 * Uses the same JNI native library as Android, but loaded from
 * the system library path (.dll on Windows, .dylib on macOS, .so on Linux).
 */
class DesktopLibretroController(
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

    /**
     * External button state for netplay. When in netplay mode, captureLocalInput
     * reads from this instead of the JNI table to avoid a feedback loop where
     * applyInputToJni's output is re-captured as new input.
     * Written by setButton (from pressButton / UI thread), read by emulation thread.
     */
    @Volatile
    private var netplayLocalButtons: Int = 0

    /** Callback invoked on the emulation thread when the remote peer times out. */
    var onNetplayPeerTimeout: (() -> Unit)? = null

    /**
     * Audio player for audio-sync frame pacing. When set, the emulation loop
     * writes audio directly to the device after each retro_run() and the
     * blocking write provides frame pacing (no precisionSleep needed).
     */
    @Volatile
    var audioPlayer: DesktopAudioPlayer? = null

    private var emulationThread: Thread? = null
    private var targetFps = 60.0

    @Volatile
    private var currentFps = 0f

    @Volatile
    private var currentFrameTime = 0f

    /**
     * Double-buffered rendered frame output for offscreen Metal rendering.
     * The emulation thread renders into one buffer while the Compose thread
     * reads from the other. No locks needed -- volatile reference swap is atomic.
     */
    data class RenderedFrame(val data: ByteArray, val width: Int, val height: Int, val aspectRatio: Float = 0f)

    private val renderBuffers = arrayOf(ByteArray(0), ByteArray(0))
    private var renderBufferIndex = 0

    @Volatile
    var latestRenderedFrame: RenderedFrame? = null
        private set

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
        netplayDisconnected = false

        val isNetplay = netplayTransport != null
        emulationThread = Thread({
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
    }

    override fun resume() {
        paused = false
    }

    /** Set by emulation thread; signals that unload+deinit should happen on the emu thread. */
    @Volatile
    private var deinitOnEmuThread = false

    override fun stop() {
        // Signal the emulation thread to run nativeUnloadGame + nativeDeinit
        // before exiting. This ensures cores with thread-affine resources
        // (e.g. Play! PS2's OpenGL context) are cleaned up on the correct thread.
        deinitOnEmuThread = true
        running = false
        netplayTransport?.disconnect()
        emulationThread?.join()
        emulationThread = null
        deinitOnEmuThread = false
        // Don't deinit GPU here — it persists across game sessions on desktop.
        // The composable's onDispose handles GPU cleanup when the user leaves
        // the emulation screen. This prevents stop() (called as precautionary
        // cleanup in startGame) from destroying the renderer mid-lifecycle.
        clearNetplayMode()
    }

    // Always report true — calling nativeSerializeSize() from a non-GL thread
    // crashes PPSSPP (and potentially other GL HW render cores) because they
    // flush their GL render queue during serialize. If a core doesn't support
    // save states, serialize() itself will return null gracefully.
    override fun supportsSaveStates(): Boolean = true

    override fun serialize(): ByteArray? = jni.nativeSerialize()

    override fun unserialize(data: ByteArray): Boolean = jni.nativeUnserialize(data)

    override fun setFastForward(enabled: Boolean) {
        fastForward = enabled
    }

    override fun setVolume(volume: Float) {
        audioPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    override fun performanceStats(): Flow<Pair<Float, Float>> = flow {
        while (running) {
            emit(currentFps to currentFrameTime)
            delay(500)
        }
    }

    fun getVideoFrame(): ByteArray? = jni.nativeGetVideoFrame()
    fun getVideoWidth(): Int = jni.nativeGetVideoWidth()
    fun getVideoHeight(): Int = jni.nativeGetVideoHeight()
    fun getPixelFormat(): Int = jni.nativeGetPixelFormat()
    fun getAudioBuffer(): ShortArray? = jni.nativeGetAudioBuffer()
    fun getSampleRate(): Double = jni.nativeGetSampleRate()

    fun setButton(port: Int, buttonId: Int, pressed: Boolean) {
        jni.nativeSetInputButton(port, buttonId, pressed)
        // Also update netplay-side button state (avoids JNI feedback loop)
        if (port == netplayLocalPort && netplayTransport != null) {
            val mask = 1 shl buttonId
            if (pressed) {
                netplayLocalButtons = netplayLocalButtons or mask
            } else {
                netplayLocalButtons = netplayLocalButtons and mask.inv()
            }
        }
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
    fun gpuInitOffscreen(width: Int, height: Int): Boolean = jni.nativeGpuInitOffscreen(width, height)
    fun gpuRender() = jni.nativeGpuRender()
    fun gpuRenderToBgra(outData: ByteArray): Long = jni.nativeGpuRenderToBgra(outData)
    fun gpuSetShader(shaderId: Int) = jni.nativeGpuSetShader(shaderId)
    fun gpuDeinit() = jni.nativeGpuDeinit()
    fun gpuIsActive(): Boolean = jni.nativeGpuIsActive()
    fun gpuSetSourceRect(x: Int, y: Int, w: Int, h: Int) = jni.nativeGpuSetSourceRect(x, y, w, h)

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

    /** Whether the GPU renderer is active (checked each frame). */
    fun isGpuActive(): Boolean = jni.nativeGpuIsActive()

    /** Last canvas size reported via gpuResize, used for buffer allocation. */
    @Volatile
    private var lastCanvasWidth = 1920

    @Volatile
    private var lastCanvasHeight = 1080

    fun gpuResize(width: Int, height: Int) {
        lastCanvasWidth = width
        lastCanvasHeight = height
        jni.nativeGpuResize(width, height)
    }

    /**
     * Render the current GPU frame through shaders and store the BGRA result.
     * Called on the emulation thread. Uses double buffering: writes to one buffer
     * while the Compose thread may be reading from the other.
     */
    private fun renderGpuFrameToBgra() {
        val w = jni.nativeGetVideoWidth()
        val h = jni.nativeGetVideoHeight()
        if (w <= 0 || h <= 0) return

        // Allocate enough for the shader-upscaled output (up to canvas size).
        // The GPU renderer outputs at the offscreen target resolution which
        // matches the canvas/window dimensions when a shader is active.
        val maxNeeded = lastCanvasWidth * lastCanvasHeight * 4
        val bufIdx = renderBufferIndex
        if (renderBuffers[bufIdx].size < maxNeeded) {
            renderBuffers[bufIdx] = ByteArray(maxNeeded)
        }

        val result = jni.nativeGpuRenderToBgra(renderBuffers[bufIdx])
        if (result != 0L) {
            // Unpack width (high 32 bits) and height (low 32 bits) from the packed long
            val actualW = (result shr 32).toInt()
            val actualH = (result and 0xFFFFFFFFL).toInt()
            val ar = jni.nativeGetAspectRatio()
            latestRenderedFrame = RenderedFrame(renderBuffers[bufIdx], actualW, actualH, ar)
            renderBufferIndex = 1 - bufIdx
        }
    }

    private fun runEmulationLoop() {
        val frameTimeNs = (1_000_000_000.0 / targetFps).toLong()
        var fpsCounter = 0
        var fpsTimer = System.nanoTime()

        while (running) {
            if (paused) {
                Thread.sleep(16)
                continue
            }

            val frameStart = System.nanoTime()

            jni.nativeRun()

            // GPU offscreen path: upload happened in video_refresh_callback,
            // now render with shader and read back BGRA pixels on this thread.
            // This avoids blocking the Compose thread with Metal waitUntilCompleted.
            if (jni.nativeGpuIsActive()) {
                renderGpuFrameToBgra()
            }

            // Audio-sync frame pacing: write audio to device (blocking write
            // paces emulation to the audio sample rate). Falls back to
            // precisionSleep if no audio player or no samples available.
            // Audio-sync frame pacing: write audio to device (blocking write
            // paces emulation to the audio sample rate). Falls back to
            // precisionSleep if no audio player, no samples, or device not ready.
            val ap = audioPlayer
            val audioSamples = jni.nativeGetAudioBuffer()
            val synced = if (ap != null && !fastForward && audioSamples != null && audioSamples.isNotEmpty()) {
                ap.writeSync(audioSamples)
            } else false
            if (!synced && !fastForward) {
                precisionSleep(frameStart + frameTimeNs)
            }

            fpsCounter++
            val now = System.nanoTime()
            val elapsed = now - fpsTimer
            if (elapsed >= 1_000_000_000L) {
                currentFps = fpsCounter.toFloat() * 1_000_000_000L / elapsed
                println("[Emulation] FPS: %.1f  frameTime: %.2fms".format(currentFps, currentFrameTime))
                fpsTimer = now
                fpsCounter = 0
            }

            val frameEnd = System.nanoTime()
            currentFrameTime = (frameEnd - frameStart) / 1_000_000f
        }

        // Deinit on the emulation thread so cores with thread-affine resources
        // (e.g. OpenGL contexts) are cleaned up on the correct thread.
        if (deinitOnEmuThread) {
            jni.nativeUnloadGame()
            jni.nativeDeinit()
        }
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
        val inputDelay = netplayInputDelay
        val frameTimeNs = (1_000_000_000.0 / targetFps).toLong()
        val playerCount = 2
        var frameCounter = 0L
        var fpsCounter = 0
        var fpsTimer = System.nanoTime()

        // Seed frames 0 through inputDelay-1 with empty inputs for both ports.
        // The loop sends input for frame F + inputDelay, so without seeding
        // the early frames would never have inputs and the loop would deadlock.
        val emptyInput = InputState()
        for (f in 0L until inputDelay.toLong()) {
            for (p in 0 until playerCount) {
                runBlocking {
                    inputBuffer.setLocalInput(f, p, emptyInput)
                }
                transport.sendInput(f, p, emptyInput)
            }
        }

        while (running) {
            if (paused) {
                Thread.sleep(16)
                continue
            }

            val frameStart = System.nanoTime()
            val currentFrame = frameCounter

            // 1. Capture local input from netplayLocalButtons (set by setButton),
            //    NOT from the JNI table (which has stale applyInputToJni state).
            val localInput = captureNetplayLocalInput()

            // 3. Buffer local input for frame F + inputDelay and send to remote
            val targetFrame = currentFrame + inputDelay
            runBlocking {
                inputBuffer.setLocalInput(targetFrame, localPort, localInput)
            }
            transport.sendInput(targetFrame, localPort, localInput)

            // 4. Block until we have both players' inputs for the current frame
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

            // 5. Apply all player inputs via JNI
            for ((port, input) in frameInputs) {
                applyInputToJni(port, input)
            }

            // 6. Run one emulation frame
            jni.nativeRun()

            if (jni.nativeGpuIsActive()) {
                renderGpuFrameToBgra()
            }

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

        if (deinitOnEmuThread) {
            jni.nativeUnloadGame()
            jni.nativeDeinit()
        }
    }

    /**
     * High-precision frame pacing. Thread.sleep has ~1-3ms overshoot on macOS,
     * so we sleep for the bulk of the wait and spin-wait for the last 2ms.
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
        while (System.nanoTime() < targetNanos) {
            Thread.onSpinWait()
        }
    }

    /**
     * Capture the current local input state from the JNI input table.
     * Returns an InputState with the current button and analog values.
     */
    /**
     * Capture local input from netplayLocalButtons (Kotlin-side state).
     * Reads the buttons set by setButton() on the external thread,
     * avoiding the JNI feedback loop with applyInputToJni.
     */
    private fun captureNetplayLocalInput(): InputState {
        return InputState(netplayLocalButtons.toUShort(), 0, 0)
    }

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

    override fun getSRAM(): ByteArray? = jni.nativeGetSRAM()

    override fun setSRAM(data: ByteArray): Boolean = jni.nativeSetSRAM(data)

    override fun cheatReset() = jni.nativeCheatReset()

    override fun cheatSet(index: Int, enabled: Boolean, code: String) =
        jni.nativeCheatSet(index, enabled, code)
}
