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

    /** Callback invoked on the emulation thread when the remote peer times out. */
    var onNetplayPeerTimeout: (() -> Unit)? = null

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

    override fun stop() {
        running = false
        netplayTransport?.disconnect()
        // No timeout: we MUST wait for retro_run() to return before calling
        // nativeUnloadGame(). Calling unload while retro_run() is active causes
        // a crash in the core.
        emulationThread?.join()
        emulationThread = null
        // GPU deinit AFTER emulation thread is dead — no Metal race
        if (jni.nativeGpuIsActive()) {
            jni.nativeGpuDeinit()
        }
        clearNetplayMode()
        jni.nativeUnloadGame()
        jni.nativeDeinit()
    }

    override fun supportsSaveStates(): Boolean = jni.nativeSerializeSize() > 0

    override fun serialize(): ByteArray? = jni.nativeSerialize()

    override fun unserialize(data: ByteArray): Boolean = jni.nativeUnserialize(data)

    override fun setFastForward(enabled: Boolean) {
        fastForward = enabled
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
    fun gpuRenderToBgra(outData: ByteArray): Int = jni.nativeGpuRenderToBgra(outData)
    fun gpuSetShader(shaderId: Int) = jni.nativeGpuSetShader(shaderId)
    fun gpuResize(width: Int, height: Int) = jni.nativeGpuResize(width, height)
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

    /**
     * Render the current GPU frame through shaders and store the BGRA result.
     * Called on the emulation thread. Uses double buffering: writes to one buffer
     * while the Compose thread may be reading from the other.
     */
    private fun renderGpuFrameToBgra() {
        val w = jni.nativeGetVideoWidth()
        val h = jni.nativeGetVideoHeight()
        if (w <= 0 || h <= 0) return

        val needed = w * h * 4
        val bufIdx = renderBufferIndex
        if (renderBuffers[bufIdx].size < needed) {
            renderBuffers[bufIdx] = ByteArray(needed)
        }

        val written = jni.nativeGpuRenderToBgra(renderBuffers[bufIdx])
        if (written > 0) {
            val ar = jni.nativeGetAspectRatio()
            latestRenderedFrame = RenderedFrame(renderBuffers[bufIdx], w, h, ar)
            renderBufferIndex = 1 - bufIdx // Swap for next frame
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

            if (!fastForward) {
                precisionSleep(frameStart + frameTimeNs)
            }
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
}
