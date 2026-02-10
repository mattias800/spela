package com.spela.player.libretro

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages an Android AudioTrack for libretro audio output.
 * Receives stereo interleaved int16 samples and plays them.
 *
 * Audio writing runs on a dedicated thread to avoid blocking the emulation loop.
 * The emulation thread writes samples into a lock-free ring buffer, and the audio
 * thread drains it to AudioTrack at its own pace.
 */
class AudioOutput(private val sampleRate: Int) {

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    // Ring buffer: sized for ~200ms of stereo audio at the given sample rate.
    // Each sample is one Short (one channel), stereo = 2 shorts per frame.
    private val ringCapacity = sampleRate * 2 * 200 / 1000 // ~200ms worth of stereo samples
    private val ring = ShortArray(ringCapacity)

    @Volatile
    private var writePos = 0

    @Volatile
    private var readPos = 0

    companion object {
        private const val TAG = "AudioOutput"
    }

    fun start() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        running.set(true)
        paused.set(false)
        writePos = 0
        readPos = 0

        audioThread = Thread({
            drainLoop()
        }, "SpelaAudio").apply {
            priority = Thread.MAX_PRIORITY - 1
            start()
        }
    }

    fun pause() {
        paused.set(true)
        audioTrack?.pause()
    }

    fun resume() {
        paused.set(false)
        audioTrack?.play()
    }

    fun stop() {
        running.set(false)
        audioThread?.join(1000)
        audioThread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    /**
     * Write stereo interleaved int16 samples to the AudioTrack.
     * Called from the emulation thread after each retro_run().
     */
    fun writeSamples(samples: ShortArray) {
        enqueueSamples(samples, samples.size)
    }

    /**
     * Write a specific number of stereo interleaved int16 samples.
     * Used with pre-allocated buffers where only part of the array contains valid data.
     * Non-blocking: writes to ring buffer instead of AudioTrack directly.
     */
    fun writeSamples(samples: ShortArray, count: Int) {
        enqueueSamples(samples, count)
    }

    /**
     * Enqueue samples into the ring buffer. Non-blocking — if the ring buffer is full,
     * excess samples are dropped to prevent the emulation thread from stalling.
     */
    private fun enqueueSamples(samples: ShortArray, count: Int) {
        val wp = writePos
        val rp = readPos
        val used = (wp - rp + ringCapacity) % ringCapacity
        val free = ringCapacity - 1 - used
        val toWrite = minOf(count, free)

        if (toWrite <= 0) return

        val startIdx = wp % ringCapacity
        val firstChunk = minOf(toWrite, ringCapacity - startIdx)
        System.arraycopy(samples, 0, ring, startIdx, firstChunk)
        if (toWrite > firstChunk) {
            System.arraycopy(samples, firstChunk, ring, 0, toWrite - firstChunk)
        }
        writePos = (wp + toWrite) % ringCapacity
    }

    /**
     * Audio thread: drains the ring buffer to AudioTrack in small batches.
     */
    private fun drainLoop() {
        val batch = ShortArray(1024)
        while (running.get()) {
            if (paused.get()) {
                Thread.sleep(10)
                continue
            }

            val wp = writePos
            val rp = readPos
            val available = (wp - rp + ringCapacity) % ringCapacity

            if (available == 0) {
                Thread.sleep(1)
                continue
            }

            val toRead = minOf(available, batch.size)
            val startIdx = rp % ringCapacity
            val firstChunk = minOf(toRead, ringCapacity - startIdx)
            System.arraycopy(ring, startIdx, batch, 0, firstChunk)
            if (toRead > firstChunk) {
                System.arraycopy(ring, 0, batch, firstChunk, toRead - firstChunk)
            }
            readPos = (rp + toRead) % ringCapacity

            try {
                audioTrack?.write(batch, 0, toRead)
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack write failed", e)
            }
        }
    }
}
