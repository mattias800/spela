package com.spela.player.libretro

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.floor

/**
 * Android audio output modeled on RetroArch's audio pipeline:
 *
 *   core samples (32029 Hz) → linear interpolation resampler → AudioTrack (48000 Hz)
 *
 * The resampler converts from the core's native sample rate to a fixed 48000 Hz
 * output rate (matching most Android HAL native rates), with a dynamic ratio
 * adjustment of ±0.5% based on the AudioTrack buffer fill level.
 *
 * This dynamic rate control (identical to RetroArch's `audio_driver_flush`)
 * keeps the buffer hovering at ~50% full:
 * - Buffer emptier than target → ratio increases → more output samples → fills buffer
 * - Buffer fuller than target → ratio decreases → fewer output samples → drains buffer
 *
 * The ±0.5% pitch change is inaudible (<9 cents). Combined with blocking writes
 * as a safety backpressure mechanism, this eliminates both underruns (crackle)
 * and overruns (blocking stalls).
 *
 * The AudioTrack is opened lazily on the first [write] call.
 */
class AndroidAudioPlayer(private val coreSampleRate: Int) {

    private var audioTrack: AudioTrack? = null
    private var initAttempted = false

    /** Total stereo frames written to the AudioTrack (at OUTPUT_RATE). */
    private var totalWrittenFrames = 0L

    /** AudioTrack buffer capacity in stereo frames (at OUTPUT_RATE). */
    private var bufferCapacityFrames = 0

    // --- Resampler state ---

    /** Base resampling ratio: OUTPUT_RATE / coreSampleRate (e.g. 48000/32029 ≈ 1.499). */
    private val baseRatio = OUTPUT_RATE.toDouble() / coreSampleRate

    /** Fractional input position carried across write() calls for seamless resampling. */
    private var resamplePos = 0.0

    /** Last input sample from previous call, for cross-boundary interpolation. */
    private var prevSampleL: Short = 0
    private var prevSampleR: Short = 0

    /** Reusable output buffer for resampled audio. */
    private var outputBuffer = ShortArray(4096)

    companion object {
        private const val TAG = "AndroidAudioPlayer"

        /** Output sample rate fed to the AudioTrack. 48000 Hz matches most Android HALs. */
        private const val OUTPUT_RATE = 48000

        /**
         * Rate control delta (matches RetroArch's default). At buffer extremes
         * (completely full or empty), the resampling ratio deviates by ±0.5%
         * from nominal. This is ~8.6 cents — well below audibility.
         */
        private const val RATE_CONTROL_DELTA = 0.005
    }

    /**
     * Lazily open the AudioTrack at [OUTPUT_RATE]. Called on first [write].
     */
    private fun ensureStarted(): Boolean {
        if (audioTrack != null) return true
        if (initAttempted) return false

        initAttempted = true
        Log.i(TAG, "Audio starting: coreSampleRate=$coreSampleRate " +
            "outputRate=$OUTPUT_RATE baseRatio=${"%.4f".format(baseRatio)}")

        val minBufSize = AudioTrack.getMinBufferSize(
            OUTPUT_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        // ~150ms buffer at 48000 Hz for ample jitter headroom.
        // Align to frame size (4 bytes) as required by AudioTrack.
        val frameBytes = 4
        val targetBufSize = OUTPUT_RATE * frameBytes * 150 / 1000
        val bufferSize = ((maxOf(minBufSize, targetBufSize) + frameBytes - 1) / frameBytes) * frameBytes
        bufferCapacityFrames = bufferSize / frameBytes

        try {
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
                        .setSampleRate(OUTPUT_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            totalWrittenFrames = 0
            resamplePos = 0.0
            prevSampleL = 0
            prevSampleR = 0
            Log.i(TAG, "AudioTrack opened at $OUTPUT_RATE Hz (buffer=$bufferSize bytes, " +
                "~${bufferSize * 1000 / (OUTPUT_RATE * frameBytes)}ms, $bufferCapacityFrames frames)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open AudioTrack", e)
            return false
        }
    }

    fun pause() {
        audioTrack?.pause()
    }

    fun resume() {
        audioTrack?.play()
    }

    fun stop() {
        audioTrack?.let {
            it.stop()
            it.release()
        }
        audioTrack = null
        initAttempted = false
        resamplePos = 0.0
        prevSampleL = 0
        prevSampleR = 0
    }

    /**
     * Accept audio samples from the core, resample to [OUTPUT_RATE] with
     * dynamic rate control, and write to the AudioTrack.
     *
     * The dynamic ratio adjustment (RetroArch's formula) keeps the AudioTrack
     * buffer at ~50% fill, compensating for clock drift between the system
     * clock (which drives frame pacing) and the audio hardware clock.
     *
     * @param samples Stereo interleaved int16 samples at [coreSampleRate]
     * @param count Number of valid shorts in the array
     */
    fun write(samples: ShortArray, count: Int) {
        if (count <= 0) return
        if (!ensureStarted()) return
        val track = audioTrack ?: return

        val inputFrames = count / 2

        // --- Dynamic rate control (RetroArch audio_driver_flush formula) ---
        //
        // avail     = free space in buffer
        // half_size = target: half the buffer should be free
        // direction = (avail - half_size) / half_size  ∈ [-1, +1]
        // adjust    = 1.0 + 0.005 * direction
        //
        // When buffer is emptier than target: direction > 0 → adjust > 1 → more output → fills buffer
        // When buffer is fuller than target:  direction < 0 → adjust < 1 → less output → drains buffer
        val played = track.playbackHeadPosition.toLong()
        val buffered = (totalWrittenFrames - played).coerceAtLeast(0)
        val halfSize = (bufferCapacityFrames / 2).coerceAtLeast(1)
        val avail = (bufferCapacityFrames - buffered).coerceAtLeast(0)
        val direction = (avail - halfSize).toDouble() / halfSize
        val adjust = 1.0 + RATE_CONTROL_DELTA * direction

        // Resample: core rate → OUTPUT_RATE, incorporating dynamic adjustment
        val ratio = baseRatio * adjust
        val outFrames = resample(samples, inputFrames, ratio)

        // Blocking write — acts as safety backpressure. When rate control keeps
        // the buffer at ~50%, writes return immediately. Only blocks if the
        // buffer is completely full (rare transient).
        if (outFrames > 0) {
            val written = track.write(outputBuffer, 0, outFrames * 2)
            if (written > 0) {
                totalWrittenFrames += written / 2
            }
        }

    }

    /**
     * Linear interpolation resampler. Converts [inputFrames] stereo frames at
     * [coreSampleRate] to output at [OUTPUT_RATE] × adjust, using the given
     * combined [ratio] (= OUTPUT_RATE / coreSampleRate × adjust).
     *
     * Maintains [resamplePos] across calls for seamless sample-accurate
     * boundary handling. Saves the last input sample for cross-boundary
     * interpolation.
     *
     * @return Number of output stereo frames written to [outputBuffer]
     */
    private fun resample(input: ShortArray, inputFrames: Int, ratio: Double): Int {
        if (inputFrames <= 0 || ratio <= 0) return 0

        // Input samples consumed per output sample (< 1 for upsampling)
        val inputStep = 1.0 / ratio

        // Ensure output buffer is large enough
        val maxOutput = ((inputFrames / inputStep) + 4).toInt()
        if (outputBuffer.size < maxOutput * 2) {
            outputBuffer = ShortArray(maxOutput * 2 + 64)
        }

        var outCount = 0

        while (resamplePos < inputFrames.toDouble()) {
            val idx = floor(resamplePos).toInt()
            val frac = resamplePos - idx

            val s0L: Int
            val s0R: Int
            val s1L: Int
            val s1R: Int

            when {
                idx < 0 -> {
                    // Cross-boundary: interpolate between previous call's last
                    // sample and this call's first sample
                    s0L = prevSampleL.toInt()
                    s0R = prevSampleR.toInt()
                    s1L = input[0].toInt()
                    s1R = input[1].toInt()
                }
                idx >= inputFrames - 1 -> {
                    // At last sample — hold (next call will provide the neighbor)
                    s0L = input[(inputFrames - 1) * 2].toInt()
                    s0R = input[(inputFrames - 1) * 2 + 1].toInt()
                    s1L = s0L
                    s1R = s0R
                }
                else -> {
                    s0L = input[idx * 2].toInt()
                    s0R = input[idx * 2 + 1].toInt()
                    s1L = input[(idx + 1) * 2].toInt()
                    s1R = input[(idx + 1) * 2 + 1].toInt()
                }
            }

            outputBuffer[outCount * 2] = ((1.0 - frac) * s0L + frac * s1L).toInt().toShort()
            outputBuffer[outCount * 2 + 1] = ((1.0 - frac) * s0R + frac * s1R).toInt().toShort()
            outCount++
            resamplePos += inputStep
        }

        // Save last input sample for next call's cross-boundary interpolation
        if (inputFrames > 0) {
            prevSampleL = input[(inputFrames - 1) * 2]
            prevSampleR = input[(inputFrames - 1) * 2 + 1]
        }

        // Carry fractional position to next call
        resamplePos -= inputFrames

        return outCount
    }
}
