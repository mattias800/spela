package com.spela.player.libretro

import android.graphics.Bitmap
import android.util.Log
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.util.FileStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Android implementation of LibretroController using JNI.
 *
 * Manages the emulation thread that calls retro_run at the target FPS,
 * converts native video frames to Bitmap for Compose rendering,
 * and routes audio samples to an AudioTrack.
 */
class AndroidLibretroController(
    private val fileStorage: FileStorage,
) : LibretroController {

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
    private var currentFps = 0f

    @Volatile
    private var currentFrameTime = 0f

    /* Video: emulation thread writes to back buffer, then swaps to front for Compose */
    private val _frameBitmap = MutableStateFlow<Bitmap?>(null)
    val frameBitmap: StateFlow<Bitmap?> = _frameBitmap.asStateFlow()

    /* Audio output */
    private var audioOutput: AudioOutput? = null

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

    /* Libretro pixel format constants (match libretro.h) */
    companion object {
        private const val TAG = "LibretroController"
        private const val RETRO_PIXEL_FORMAT_0RGB1555 = 0
        private const val RETRO_PIXEL_FORMAT_XRGB8888 = 1
        private const val RETRO_PIXEL_FORMAT_RGB565 = 2
    }

    override fun loadCore(corePath: String) {
        jni.nativeSetSystemDir(fileStorage.getCoresDir())
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

        emulationThread = Thread({
            startAudio()
            runEmulationLoop()
        }, "SpelaEmulation").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun pause() {
        paused = true
        audioOutput?.pause()
    }

    override fun resume() {
        paused = false
        audioOutput?.resume()
    }

    override fun stop() {
        running = false
        emulationThread?.join(2000)
        emulationThread = null
        audioOutput?.stop()
        audioOutput = null
        jni.nativeUnloadGame()
        jni.nativeDeinit()
        _frameBitmap.value = null
        frontBitmap?.recycle()
        frontBitmap = null
        backBitmap?.recycle()
        backBitmap = null
        lastFrameWidth = 0
        lastFrameHeight = 0
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
     * Set button state from the platform input system.
     */
    fun setButton(port: Int, buttonId: Int, pressed: Boolean) {
        jni.nativeSetInputButton(port, buttonId, pressed)
    }

    fun setAnalog(port: Int, stickIndex: Int, axisId: Int, value: Short) {
        jni.nativeSetInputAnalog(port, stickIndex, axisId, value)
    }

    /**
     * Initialize audio output on the emulation thread.
     */
    private fun startAudio() {
        try {
            val sampleRate = jni.nativeGetSampleRate().toInt()
            Log.i(TAG, "Core sample rate: $sampleRate Hz")
            if (sampleRate > 0) {
                audioOutput = AudioOutput(sampleRate).also { it.start() }
                Log.i(TAG, "AudioOutput started at $sampleRate Hz")
            } else {
                Log.w(TAG, "Core reported invalid sample rate: $sampleRate")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio output", e)
        }
    }

    /**
     * The main emulation loop. Runs retro_run at the core's target FPS.
     * After each frame, updates the video bitmap and pushes audio samples.
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

            // Update video frame
            updateVideoFrame()

            // Push audio
            pushAudio()

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

        // Reallocate double buffers if dimensions changed
        if (width != lastFrameWidth || height != lastFrameHeight) {
            frontBitmap?.recycle()
            backBitmap?.recycle()
            frontBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            backBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            lastFrameWidth = width
            lastFrameHeight = height
        }

        // Write to back buffer, then swap: Compose only ever reads the front bitmap
        val back = backBitmap ?: return
        back.setPixels(pixelBuffer, 0, width, 0, 0, width, height)
        _frameBitmap.value = back
        backBitmap = frontBitmap
        frontBitmap = back
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
     * Reads buffered audio samples from the native layer and writes to AudioTrack.
     * Uses a pre-allocated buffer to avoid per-frame ShortArray allocation.
     */
    private fun pushAudio() {
        // Ensure audio buffer is large enough (stereo, ~2048 frames is typical max per tick)
        if (audioSampleBuffer.size < 4096) {
            audioSampleBuffer = ShortArray(4096)
        }

        val count = jni.nativeFillAudioBuffer(audioSampleBuffer)
        if (count > 0) {
            audioOutput?.writeSamples(audioSampleBuffer, count)
        }
    }
}
