package com.spela.player.libretro

import com.spela.player.presentation.viewmodel.LibretroController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Android implementation of LibretroController using JNI.
 *
 * Manages the emulation thread that calls retro_run at the target FPS,
 * and exposes video/audio buffers for the rendering layer.
 */
class AndroidLibretroController : LibretroController {

    private val jni = LibretroJni()

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    @Volatile
    private var fastForward = false

    private var emulationThread: Thread? = null
    private var targetFps = 60.0

    /* Frame timing for performance stats */
    @Volatile
    private var lastFrameTime = 0L

    @Volatile
    private var currentFps = 0f

    @Volatile
    private var currentFrameTime = 0f

    override fun loadCore(corePath: String) {
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
        emulationThread = Thread({
            runEmulationLoop()
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
        emulationThread?.join(2000)
        emulationThread = null
        jni.nativeUnloadGame()
        jni.nativeDeinit()
    }

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

    /**
     * Get the latest video frame as raw pixel data.
     * Called by the rendering layer (SurfaceView/GLSurfaceView).
     */
    fun getVideoFrame(): ByteArray? = jni.nativeGetVideoFrame()
    fun getVideoWidth(): Int = jni.nativeGetVideoWidth()
    fun getVideoHeight(): Int = jni.nativeGetVideoHeight()
    fun getPixelFormat(): Int = jni.nativeGetPixelFormat()

    /**
     * Get buffered audio samples. Stereo interleaved int16.
     * Called by the audio output thread.
     */
    fun getAudioBuffer(): ShortArray? = jni.nativeGetAudioBuffer()
    fun getSampleRate(): Double = jni.nativeGetSampleRate()

    /**
     * Set button state from the platform input system.
     */
    fun setButton(port: Int, buttonId: Int, pressed: Boolean) {
        jni.nativeSetInputButton(port, buttonId, pressed)
    }

    fun setAnalog(port: Int, stickIndex: Int, axisId: Int, value: Short) {
        jni.nativeSetInputAnalog(port, stickIndex, axisId, value)
    }

    /**
     * The main emulation loop. Runs retro_run at the core's target FPS.
     * Uses nanosecond-precision timing for accurate frame pacing.
     */
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

            /* Frame pacing: sleep until next frame (skip for fast-forward) */
            if (!fastForward) {
                val sleepNs = frameTimeNs - (frameEnd - frameStart)
                if (sleepNs > 0) {
                    Thread.sleep(sleepNs / 1_000_000, (sleepNs % 1_000_000).toInt())
                }
            }
        }
    }
}
