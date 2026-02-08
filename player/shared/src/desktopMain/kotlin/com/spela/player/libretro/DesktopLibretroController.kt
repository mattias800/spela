package com.spela.player.libretro

import com.spela.player.presentation.viewmodel.LibretroController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Desktop (JVM) implementation of LibretroController.
 *
 * Uses the same JNI native library as Android, but loaded from
 * the system library path (.dll on Windows, .dylib on macOS, .so on Linux).
 */
class DesktopLibretroController : LibretroController {

    companion object {
        init {
            System.loadLibrary("spela-libretro")
        }
    }

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    @Volatile
    private var fastForward = false

    private var emulationThread: Thread? = null
    private var targetFps = 60.0

    @Volatile
    private var currentFps = 0f

    @Volatile
    private var currentFrameTime = 0f

    /* JNI native declarations (same symbols as Android) */
    private external fun nativeLoadCore(corePath: String): Boolean
    private external fun nativeInit()
    private external fun nativeLoadGame(gamePath: String): Boolean
    private external fun nativeRun()
    private external fun nativeUnloadGame()
    private external fun nativeDeinit()
    private external fun nativeSerialize(): ByteArray?
    private external fun nativeUnserialize(data: ByteArray): Boolean
    private external fun nativeGetVideoFrame(): ByteArray?
    private external fun nativeGetVideoWidth(): Int
    private external fun nativeGetVideoHeight(): Int
    private external fun nativeGetPixelFormat(): Int
    private external fun nativeGetAudioBuffer(): ShortArray?
    private external fun nativeSetInputButton(port: Int, id: Int, pressed: Boolean)
    private external fun nativeSetInputAnalog(port: Int, index: Int, id: Int, value: Short)
    private external fun nativeGetTargetFps(): Double
    private external fun nativeGetSampleRate(): Double
    private external fun nativeSetSystemDir(dir: String)
    private external fun nativeSetSaveDir(dir: String)

    override fun loadCore(corePath: String) {
        if (!nativeLoadCore(corePath)) {
            throw RuntimeException("Failed to load core: $corePath")
        }
        nativeInit()
    }

    override fun loadGame(gamePath: String) {
        if (!nativeLoadGame(gamePath)) {
            throw RuntimeException("Failed to load game: $gamePath")
        }
        targetFps = nativeGetTargetFps()
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
        nativeUnloadGame()
        nativeDeinit()
    }

    override fun serialize(): ByteArray? = nativeSerialize()

    override fun unserialize(data: ByteArray): Boolean = nativeUnserialize(data)

    override fun setFastForward(enabled: Boolean) {
        fastForward = enabled
    }

    override fun performanceStats(): Flow<Pair<Float, Float>> = flow {
        while (running) {
            emit(currentFps to currentFrameTime)
            delay(500)
        }
    }

    fun getVideoFrame(): ByteArray? = nativeGetVideoFrame()
    fun getVideoWidth(): Int = nativeGetVideoWidth()
    fun getVideoHeight(): Int = nativeGetVideoHeight()
    fun getPixelFormat(): Int = nativeGetPixelFormat()
    fun getAudioBuffer(): ShortArray? = nativeGetAudioBuffer()
    fun getSampleRate(): Double = nativeGetSampleRate()

    fun setButton(port: Int, buttonId: Int, pressed: Boolean) {
        nativeSetInputButton(port, buttonId, pressed)
    }

    fun setAnalog(port: Int, stickIndex: Int, axisId: Int, value: Short) {
        nativeSetInputAnalog(port, stickIndex, axisId, value)
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

            nativeRun()

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

            if (!fastForward) {
                val sleepNs = frameTimeNs - (frameEnd - frameStart)
                if (sleepNs > 0) {
                    Thread.sleep(sleepNs / 1_000_000, (sleepNs % 1_000_000).toInt())
                }
            }
        }
    }
}
