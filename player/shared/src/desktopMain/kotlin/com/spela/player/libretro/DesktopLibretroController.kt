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

    /* Emulation-thread dispatch: some cores (Dolphin) require retro_serialize /
     * retro_unserialize to run on the SAME thread as retro_run — they flush the
     * GPU render queue during (un)serialize, which needs the HW-render context
     * that lives on the emulation thread. Calling them from another thread (the
     * IO dispatcher, during auto-save-on-stop) deadlocks → "Uploading save…"
     * hangs forever. This queue lets any thread run work on the emulation
     * thread and wait for the result. Mirrors AndroidLibretroController. (#1206) */
    private class EmulationThreadRequest(
        val action: () -> Any?,
        val latch: java.util.concurrent.CountDownLatch = java.util.concurrent.CountDownLatch(1),
        @Volatile var result: Any? = null,
    )

    private val emulationThreadQueue = java.util.concurrent.LinkedBlockingQueue<EmulationThreadRequest>()

    /** Run [block] on the emulation thread and wait for the result. If the
     *  emulation thread isn't alive (or we're already on it), runs inline. */
    private fun <T> runOnEmulationThread(timeoutSeconds: Long = 15, block: () -> T): T? {
        val t = emulationThread
        if (t?.isAlive != true || Thread.currentThread() == t) {
            return block()
        }
        val req = EmulationThreadRequest(action = { block() })
        emulationThreadQueue.add(req)
        val completed = req.latch.await(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        @Suppress("UNCHECKED_CAST")
        return if (completed) req.result as? T else null
    }

    /** Service one queued emulation-thread request. Called from the emulation
     *  loop (on the emulation thread), so the action runs with the HW-render
     *  context current. */
    private fun pumpEmulationThreadQueue() {
        val req = emulationThreadQueue.poll() ?: return
        req.result = req.action()
        req.latch.countDown()
    }

    @Volatile
    private var currentFps = 0f

    @Volatile
    private var currentFrameTime = 0f

    /* Active play-time accrual (#1282). The emulation thread adds the
     * clamped wall-clock gap between presented frames; the reporter
     * drains via consumeActivePlayMillis(). lastFrameNanos is reset to 0
     * on pause so the gap across a pause isn't counted on resume. */
    private val activePlayMillis = java.util.concurrent.atomic.AtomicLong(0L)
    @Volatile
    private var lastFrameNanos = 0L

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

    fun getAspectRatio(): Float = jni.nativeGetAspectRatio()

    override fun loadCore(corePath: String, saveDir: String?) {
        jni.nativeSetSystemDir(fileStorage.getBiosDir())
        jni.nativeSetSaveDir(saveDir ?: fileStorage.getSavesDir())

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
            // Daemon so the emulation loop (and its audio) can never outlive
            // the JVM if the app exits without a clean stop() — otherwise a
            // window-close that skips teardown leaves the process running
            // headless with audio still playing. (#1286)
            isDaemon = true
            start()
        }
    }

    override fun pause() {
        paused = true
        // Reset the frame-gap baseline so the paused stretch isn't
        // credited as play time when the loop resumes (#1282).
        lastFrameNanos = 0L
    }

    override fun resume() {
        paused = false
    }

    override fun consumeActivePlayMillis(): Long = activePlayMillis.getAndSet(0L)

    /** Credit the wall-clock gap since the previous presented frame to
     *  the active-play accumulator, clamped so an idle/suspended stretch
     *  isn't counted (#1282). Called once per frame from the emulation
     *  loop(s) — the only writer of [lastFrameNanos]. */
    private fun accrueActivePlayTime(nowNanos: Long) {
        val delta = frameDeltaMillis(lastFrameNanos, nowNanos)
        if (delta > 0L) activePlayMillis.addAndGet(delta)
        lastFrameNanos = nowNanos
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
        // Drain any pending emulation-thread requests so their callers unblock
        // instead of waiting out the timeout once the loop has stopped. (#1206)
        while (true) {
            val req = emulationThreadQueue.poll() ?: break
            req.latch.countDown()
        }
        netplayTransport?.disconnect()
        emulationThread?.join()
        emulationThread = null
        deinitOnEmuThread = false
        latestRenderedFrame = null // Clear frame so next game doesn't flash the old one
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

    // Marshal (un)serialize onto the emulation thread — HW-render cores (e.g.
    // Dolphin) flush the GPU queue here and deadlock if called off-thread. (#1206)
    override fun serialize(): ByteArray? = runOnEmulationThread { jni.nativeSerialize() }

    override fun unserialize(data: ByteArray): Boolean =
        runOnEmulationThread { jni.nativeUnserialize(data) } ?: false

    override fun unserializeFromFile(path: String): Boolean =
        runOnEmulationThread { jni.nativeUnserializeFromFile(path) } ?: false

    override fun firstFrameRun(): Boolean = jni.nativeFirstFrameRun()

    override fun setFastForward(enabled: Boolean) {
        fastForward = enabled
    }

    override fun setVolume(volume: Float) {
        audioPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    override fun refreshPausedVideo() {
        if (!running || !paused || netplayTransport != null) return
        runOnEmulationThread(timeoutSeconds = 1) {
            jni.nativeRun()
            if (jni.nativeGpuIsActive()) {
                renderGpuFrameToBgra()
            }
            discardAudio()
        }
    }

    override fun performanceStats(): Flow<Pair<Float, Float>> = flow {
        while (running) {
            emit(currentFps to currentFrameTime)
            delay(500)
        }
    }

    fun getVideoFrame(): ByteArray? = jni.nativeGetVideoFrame()
    override fun getVideoWidth(): Int = jni.nativeGetVideoWidth()
    override fun getVideoHeight(): Int = jni.nativeGetVideoHeight()
    fun getPixelFormat(): Int = jni.nativeGetPixelFormat()
    fun getAudioBuffer(): ShortArray? = jni.nativeGetAudioBuffer()
    fun getSampleRate(): Double = jni.nativeGetSampleRate()

    /** Resample the core's buffered audio (SINC) by [ratio] into [out];
     *  returns the interleaved-stereo short count. Drains the core buffer.
     *  Used by [DesktopAudioPlayer] for dynamic-rate-control output (#1288). */
    fun resampleAudio(out: ShortArray, ratio: Double): Int = jni.nativeResampleAudio(out, ratio)

    private fun discardAudio() {
        jni.nativeGetAudioBuffer()
    }

    override fun setButton(port: Int, buttonId: Int, pressed: Boolean) {
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

    override fun setAnalogButton(port: Int, buttonId: Int, value: Short) {
        jni.nativeSetAnalogButton(port, buttonId, value)
    }

    override fun clearAnalogButton(port: Int, buttonId: Int) {
        jni.nativeClearAnalogButton(port, buttonId)
    }

    override fun setPointer(port: Int, x: Int, y: Int, pressed: Boolean) {
        jni.nativeSetInputPointer(port, x, y, pressed)
    }

    override fun setMouse(port: Int, dx: Short, dy: Short, left: Boolean, right: Boolean) {
        jni.nativeSetInputMouse(port, dx, dy, left, right)
    }

    override fun setKeyboardKey(key: Int, pressed: Boolean) {
        jni.nativeSetInputKeyboard(key, pressed)
    }

    override fun setControllerPortDevice(port: Int, device: Int) {
        jni.nativeSetControllerPortDevice(port, device)
    }

    override fun setCoreVariable(key: String, value: String) {
        jni.nativeSetCoreVariable(key, value)
    }

    override fun clearCoreVariables() {
        jni.nativeClearCoreVariables()
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
            // Run any queued work (serialize/unserialize) on this thread first —
            // HW-render cores need it on the retro_run thread. (#1206)
            pumpEmulationThreadQueue()
            if (paused) {
                Thread.sleep(16)
                continue
            }

            val frameStart = System.nanoTime()
            accrueActivePlayTime(frameStart)

            jni.nativeRun()

            // GPU offscreen path: upload happened in video_refresh_callback,
            // now render with shader and read back BGRA pixels on this thread.
            // This avoids blocking the Compose thread with Metal waitUntilCompleted.
            if (jni.nativeGpuIsActive()) {
                renderGpuFrameToBgra()
            }

            // Audio output via SINC resampler + dynamic rate control, then
            // pace the loop to the core's target FPS. This mirrors the Android
            // path (and RetroArch): the DRC'd blocking write keeps the device
            // buffer ~half-full and provides realtime back-pressure, while
            // precisionSleep caps the rate at targetFps. The old raw per-frame
            // writeSync caused choppy audio / wrong speed on bursty,
            // frontend-paced cores like Play! (PS2). (#1288)
            val ap = audioPlayer
            if (ap != null && !fastForward) {
                ap.write(ap.calculateRatio())
            }
            if (!fastForward) {
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
            pumpEmulationThreadQueue() // service serialize/unserialize on this thread (#1206)
            if (paused) {
                Thread.sleep(16)
                continue
            }

            val frameStart = System.nanoTime()
            accrueActivePlayTime(frameStart)
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
