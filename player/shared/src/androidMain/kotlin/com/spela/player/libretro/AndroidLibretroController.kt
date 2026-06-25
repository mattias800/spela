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
import java.util.concurrent.CountDownLatch
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

    /** When true, skip GPU present and populate frameBitmap from CPU buffer instead. */
    @Volatile
    var dualScreenSplitActive = false
        set(value) {
            if (field == value) return
            field = value
            // A Vulkan HW core (e.g. Azahar 3DS) never fills the SW video
            // buffer, so dual-screen split needs the composited frame read
            // back to CPU. Toggle the native onscreen readback mode to match.
            jni.nativeGpuSetSplitReadback(value)
        }

    /** Callback invoked on the emulation thread when the remote peer times out. */
    var onNetplayPeerTimeout: (() -> Unit)? = null

    /** SurfaceView used for GPU rendering (GLES/Vulkan). Set by VulkanEmulationSurface. */
    @Volatile
    var vulkanSurfaceView: SurfaceView? = null

    private var emulationThread: Thread? = null
    /**
     * Latch the emulation thread counts down once
     * nativeUnloadGame + nativeDeinit have completed. stop() waits on
     * this instead of joining the thread, because the thread itself
     * never exits — see #907.
     */
    private var teardownDoneLatch: CountDownLatch? = null
    private var targetFps = 60.0

    /* Frame timing for performance stats */
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

    /* Video: emulation thread writes to back buffer, then swaps to front for Compose */
    private val _frameBitmap = MutableStateFlow<Bitmap?>(null)
    val frameBitmap: StateFlow<Bitmap?> = _frameBitmap.asStateFlow()

    /** Core-reported display aspect ratio (DAR). Returns 0 if unavailable. */
    fun getAspectRatio(): Float = jni.nativeGetAspectRatio()

    /* Audio output — native SINC resampler with dynamic rate control */
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

    /* Handler for posting state updates to the main thread (avoids Compose multithreading crash) */
    private val mainHandler = Handler(Looper.getMainLooper())

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
            // retro_load_game returned false. The core rejected the ROM —
            // see the `[core]` lines in logcat (tag SpelaLibretro) for the
            // core's own reason. Common causes: unsupported/corrupt ROM,
            // wrong core for the file, or missing BIOS.
            throw RuntimeException(
                "the emulator core could not load this game (it may be an " +
                    "unsupported or corrupt ROM): $gamePath",
            )
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
        val latch = CountDownLatch(1)
        teardownDoneLatch = latch
        emulationThread = Thread({
            startAudio()
            if (isNetplay) {
                runNetplayEmulationLoop()
            } else {
                runEmulationLoop()
            }
            // #907 + #926 — capture the park-required state BEFORE
            // nativeDeinit clears the bridge's core state. Only PSP-
            // on-GLES (PPSSPP's context_destroy poisons Adreno's
            // per-thread EGL TLS so pthread_exit's eglReleaseThread
            // crashes) needs the parked-thread workaround. Vulkan PSP
            // doesn't use EGL, and other GLES cores haven't been
            // observed to trigger the corruption. Querying after
            // nativeDeinit returns garbage — capture once now.
            val needsPark = try {
                jni.nativeGetCoreLibraryName().contains("PPSSPP") &&
                    !jni.nativeIsVulkanHwRender()
            } catch (_: Throwable) {
                false
            }

            // Libretro contract (RetroArch's runloop_event_deinit_core,
            // see #724 research): retro_unload_game and retro_deinit must
            // run on the same thread that called retro_run. Calling them
            // from stop()'s caller thread leaves cores like Play! PS2
            // and mupen64plus_next confused — the EGL "current context"
            // is wrong, and worker threads inside the core look for
            // synchronization with a thread that's gone. Run them here,
            // as the emulation thread's last act.
            try {
                jni.nativeUnloadGame()
                Log.i(TAG, "emulation thread: nativeUnloadGame complete")
            } catch (t: Throwable) {
                Log.e(TAG, "nativeUnloadGame on emulation thread failed", t)
            }
            try {
                jni.nativeDeinit()
                Log.i(TAG, "emulation thread: nativeDeinit complete")
            } catch (t: Throwable) {
                Log.e(TAG, "nativeDeinit on emulation thread failed", t)
            }
            // Signal stop() that teardown is done — it can proceed
            // with frontend-side cleanup (audio, GPU, bitmaps).
            latch.countDown()

            if (needsPark) {
                // #907 — PPSSPP's context_destroy poisons Adreno's
                // per-thread EGL binding; pthread_exit's automatic
                // eglReleaseThread (run by the EGL TLS destructor)
                // tries to clean up that binding and crashes inside
                // libGLESv2_adreno via a null vtable dispatch. Park
                // the thread forever — no exit means no
                // eglReleaseThread, no crash. The OS reclaims the
                // stack when the process exits. With the GLES PSP
                // path retired (#916, see Vulkan migration), this
                // branch is the safety net for if a future user ends
                // up here via some fallback. Other cores exit
                // normally — see #926.
                Log.i(TAG, "PSP+GLES: parking emulation thread (#907 fallback)")
                try {
                    while (true) {
                        Thread.sleep(Long.MAX_VALUE)
                    }
                } catch (_: InterruptedException) {
                    // Allow process-shutdown signals to break the park.
                }
            } else {
                Log.i(TAG, "emulation thread: exiting normally")
            }
        }, "SpelaEmulation").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun pause() {
        paused = true
        // Reset the frame-gap baseline so the paused stretch isn't
        // credited as play time when the loop resumes (#1282).
        lastFrameNanos = 0L
        audioPlayer?.pause()
    }

    override fun resume() {
        paused = false
        audioPlayer?.resume()
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

    override fun stop() {
        Log.i(TAG, "stop() called")
        running = false
        // Drain pending emulation-thread requests so callers don't block
        while (true) {
            val req = emulationThreadQueue.poll() ?: break
            req.latch.countDown()
        }
        netplayTransport?.disconnect()
        // The emulation thread runs nativeUnloadGame + nativeDeinit and
        // signals teardownDoneLatch. We wait on the LATCH (not on the
        // thread itself) — for PSP-on-GLES the thread parks forever
        // after teardown to avoid the Adreno crash described in #907;
        // every other path exits normally (see #926). The latch fires
        // before either branch so this wait works for both. No timeout
        // — heavy cores can spend tens of seconds in retro_unload_game
        // / retro_deinit on slow devices.
        teardownDoneLatch?.await()
        Log.i(TAG, "libretro teardown complete")
        // Drop our reference. If the thread parked, it stays alive
        // until process exit (~1MB stack leak); if it exited normally
        // the OS already reclaimed it.
        emulationThread = null
        teardownDoneLatch = null
        audioPlayer?.stop()
        audioPlayer = null
        clearNetplayMode()
        // Frontend-side Vulkan compositor teardown — safe on any thread,
        // doesn't touch core state.
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

    override fun firstFrameRun(): Boolean = jni.nativeFirstFrameRun()

    override fun serialize(): ByteArray? =
        runOnEmulationThread { jni.nativeSerialize() }

    override fun serializeToFile(path: String): Long? {
        val n = runOnEmulationThread { jni.nativeSerializeToFile(path) } ?: return null
        return if (n < 0) null else n
    }

    override fun unserialize(data: ByteArray): Boolean =
        runOnEmulationThread { jni.nativeUnserialize(data) } ?: false

    override fun unserializeFromFile(path: String): Boolean =
        runOnEmulationThread { jni.nativeUnserializeFromFile(path) } ?: false

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

    override fun setMouse(port: Int, dx: Short, dy: Short, left: Boolean, right: Boolean) {
        jni.nativeSetInputMouse(port, dx, dy, left, right)
    }

    override fun getVideoWidth(): Int = jni.nativeGetVideoWidth()
    override fun getVideoHeight(): Int = jni.nativeGetVideoHeight()

    override fun setKeyboardKey(key: Int, pressed: Boolean) {
        jni.nativeSetInputKeyboard(key, pressed)
    }

    override fun setControllerPortDevice(port: Int, device: Int) {
        jni.nativeSetControllerPortDevice(port, device)
    }

    override fun setCoreVariable(key: String, value: String) {
        jni.nativeSetCoreVariable(key, value)
    }

    /* GPU Renderer methods */

    // Re-apply the split-readback mode after (re)creating the renderer: the
    // native flag lives on the renderer struct, so a fresh renderer must be
    // told again whether dual-screen split is active.
    fun gpuInit(surface: Any): Boolean =
        jni.nativeGpuInit(surface).also { if (it) jni.nativeGpuSetSplitReadback(dualScreenSplitActive) }
    fun gpuRender() = jni.nativeGpuRender()
    fun gpuSetShader(shaderId: Int) = jni.nativeGpuSetShader(shaderId)
    fun gpuResize(width: Int, height: Int) = jni.nativeGpuResize(width, height)
    fun gpuDeinit() = jni.nativeGpuDeinit()
    fun gpuSuspend() = jni.nativeGpuSuspend()
    fun gpuResume(surface: Any): Boolean =
        jni.nativeGpuResume(surface).also { if (it) jni.nativeGpuSetSplitReadback(dualScreenSplitActive) }
    fun gpuIsActive(): Boolean = jni.nativeGpuIsActive()
    fun gpuSetSourceRect(x: Int, y: Int, w: Int, h: Int) = jni.nativeGpuSetSourceRect(x, y, w, h)
    override fun isHwRenderEnabled(): Boolean = jni.nativeIsHwRenderEnabled()

    override fun isVulkanHwRender(): Boolean = jni.nativeIsVulkanHwRender()

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
                audioPlayer = AndroidAudioPlayer(sampleRate, jni)
                Log.i(TAG, "AndroidAudioPlayer created at $sampleRate Hz (native SINC resampler)")
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

        // NOTE: the 2s PSP+Vulkan pre-run sleep (#916/#925) was removed here.
        // It worked around a PPSSPP libretro-Vulkan boot deadlock where the
        // first retro_run during BootState::Booting calls SwapBuffers →
        // vk_libretro_wait_for_presentation() and blocked forever because no
        // frame had presented yet. The upstream fix (hrydgard/ppsspp#21627,
        // PR #21631 — gate the wait on a new `ever_presented` flag) is merged
        // and shipped in the libretro buildbot nightly we pull, so the
        // workaround is redundant. Revert this commit if a buildbot
        // regression reintroduces the deadlock.

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
            accrueActivePlayTime(frameStart)

            jni.nativeRun()

            // GPU path: frame was already uploaded in video_refresh_callback,
            // just trigger the render pass. Skip when dual-screen split is active —
            // the primary display uses EmulationSurface (Canvas) instead, which crops
            // to the top screen from frameBitmap.
            if (jni.nativeGpuIsActive() && !dualScreenSplitActive) {
                jni.nativeGpuRender()
            }
            // Software path or dual-screen split: populate frameBitmap from CPU readback.
            // For GLES HW render cores, hw_gl_read_pixels() in video_refresh_callback
            // already populates the CPU buffer alongside the GPU upload.
            if (dualScreenSplitActive || !jni.nativeGpuIsActive()) {
                updateVideoFrame()
            }

            // Push audio (resampled to 48kHz via native SINC resampler with
            // dynamic rate control). Frame pacing via precisionSleep.
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
            accrueActivePlayTime(frameStart)
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

            if (jni.nativeGpuIsActive() && !dualScreenSplitActive) {
                jni.nativeGpuRender()
            }
            if (dualScreenSplitActive || !jni.nativeGpuIsActive()) {
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

        // Dual-screen split with a Vulkan HW core (e.g. Azahar 3DS): the core
        // renders to a VkImage and never fills the SW video buffer, so
        // nativeFillVideoFrame returns nothing and both screens go black. Read
        // the composited frame back from the GPU as BGRA instead.
        if (dualScreenSplitActive && jni.nativeIsVulkanHwRender()) {
            updateVideoFrameFromGpuReadback()
            return
        }

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

        // GLES HW render: the readback buffer from hw_gl_read_pixels() is bottom-up
        // (OpenGL origin is bottom-left). The GPU renderer's shader handles this via
        // flip_y, but when rendering via EmulationSurface (Canvas/Bitmap), we must
        // flip the pixel data manually.
        if (jni.nativeGpuIsActive()) {
            flipVertically(pixelBuffer, width, height)
        }

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
     * Dual-screen split + Vulkan HW render: read the composited frame back from
     * the GPU as BGRA and publish it as the front bitmap. The native renderer
     * is in split-readback mode (see [dualScreenSplitActive]) so the frame was
     * rendered offscreen at native resolution. No vertical flip — the Vulkan
     * readback is already top-down.
     */
    private fun updateVideoFrameFromGpuReadback() {
        val width = jni.nativeGetVideoWidth()
        val height = jni.nativeGetVideoHeight()
        if (width <= 0 || height <= 0) return

        val requiredBytes = width * height * 4
        if (videoFrameBuffer.size < requiredBytes) {
            videoFrameBuffer = ByteArray(requiredBytes)
        }

        val packed = jni.nativeGpuRenderToBgra(videoFrameBuffer)
        if (packed == 0L) return
        val w = (packed shr 32).toInt()
        val h = (packed and 0xFFFFFFFFL).toInt()
        if (w <= 0 || h <= 0) return

        val pixelCount = w * h
        if (pixelBuffer.size < pixelCount) {
            pixelBuffer = IntArray(pixelCount)
        }
        // BGRA bytes → packed ARGB: the XRGB8888 branch reads B,G,R in this exact
        // byte order and forces alpha opaque, which matches the BGRA readback.
        convertToPackedArgb(videoFrameBuffer, pixelCount, RETRO_PIXEL_FORMAT_XRGB8888, pixelBuffer)

        if (w != lastFrameWidth || h != lastFrameHeight) {
            frontBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            backBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            lastFrameWidth = w
            lastFrameHeight = h
        }
        val back = backBitmap ?: return
        back.setPixels(pixelBuffer, 0, w, 0, 0, w, h)
        backBitmap = frontBitmap
        frontBitmap = back
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

    /** Temp row buffer for vertical flip, lazily allocated to match frame width. */
    private var flipRowBuffer = IntArray(0)

    /**
     * Flips an IntArray of pixel data vertically (swaps top and bottom rows in-place).
     * Used to convert from OpenGL bottom-left origin to top-left origin for Bitmap.
     */
    private fun flipVertically(pixels: IntArray, width: Int, height: Int) {
        if (flipRowBuffer.size < width) {
            flipRowBuffer = IntArray(width)
        }
        val halfHeight = height / 2
        for (y in 0 until halfHeight) {
            val topStart = y * width
            val bottomStart = (height - 1 - y) * width
            System.arraycopy(pixels, topStart, flipRowBuffer, 0, width)
            System.arraycopy(pixels, bottomStart, pixels, topStart, width)
            System.arraycopy(flipRowBuffer, 0, pixels, bottomStart, width)
        }
    }

    /**
     * Resample audio via native SINC resampler and write to AudioTrack.
     * Rate control stays in Kotlin (reads AudioTrack.playbackHeadPosition).
     */
    private fun pushAudio() {
        val player = audioPlayer ?: return
        val ratio = player.calculateRatio()
        player.write(ratio)
    }

    /** Reusable buffer for discarding audio during fast-forward. */
    private var discardBuffer = ShortArray(0)

    /**
     * Discard audio samples during fast-forward.
     * Clears the native buffer without resampling or writing to AudioTrack.
     */
    private fun pushAudioDiscard() {
        if (discardBuffer.size < 4096) {
            discardBuffer = ShortArray(4096)
        }
        jni.nativeFillAudioBuffer(discardBuffer)
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
