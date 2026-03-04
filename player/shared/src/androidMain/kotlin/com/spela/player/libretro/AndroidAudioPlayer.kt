package com.spela.player.libretro

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Android audio output with native SINC resampling:
 *
 *   core samples (32029 Hz) → native SINC resampler (NEON on ARM64)
 *   → AudioTrack (48000 Hz) with dynamic rate control
 *
 * The native SINC resampler (Kaiser-windowed, 8 taps, 256 subphases) provides
 * high-quality sample rate conversion matching RetroArch's audio quality.
 *
 * Dynamic rate control (±0.5% pitch adjustment based on AudioTrack buffer fill)
 * keeps the buffer at ~50% full, eliminating both underruns and overruns.
 *
 * The AudioTrack is opened lazily on the first [write] call.
 */
class AndroidAudioPlayer(
    private val coreSampleRate: Int,
    private val jni: LibretroJni,
) {

    private var audioTrack: AudioTrack? = null
    private var initAttempted = false

    /** Total stereo frames written to the AudioTrack (at OUTPUT_RATE). */
    private var totalWrittenFrames = 0L

    /** AudioTrack buffer capacity in stereo frames (at OUTPUT_RATE). */
    private var bufferCapacityFrames = 0

    /** Base resampling ratio: OUTPUT_RATE / coreSampleRate (e.g. 48000/32029 ≈ 1.499). */
    private val baseRatio = OUTPUT_RATE.toDouble() / coreSampleRate

    /** Reusable output buffer for resampled audio from native. */
    private var outputBuffer = ShortArray(16384)

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
    }

    /**
     * Calculate the dynamic resampling ratio based on AudioTrack buffer fill.
     *
     * The formula (RetroArch's audio_driver_flush) keeps the buffer at ~50% full:
     * - Buffer emptier than target → ratio increases → more output → fills buffer
     * - Buffer fuller than target → ratio decreases → fewer output → drains buffer
     */
    fun calculateRatio(): Double {
        val track = audioTrack ?: return baseRatio
        val played = track.playbackHeadPosition.toLong()
        val buffered = (totalWrittenFrames - played).coerceAtLeast(0)
        val halfSize = (bufferCapacityFrames / 2).coerceAtLeast(1)
        val avail = (bufferCapacityFrames - buffered).coerceAtLeast(0)
        val direction = (avail - halfSize).toDouble() / halfSize
        val adjust = 1.0 + RATE_CONTROL_DELTA * direction
        return baseRatio * adjust
    }

    /**
     * Resample buffered core audio via native SINC resampler and write to AudioTrack.
     *
     * @param ratio Dynamic resampling ratio from [calculateRatio]
     */
    fun write(ratio: Double) {
        if (!ensureStarted()) return
        val track = audioTrack ?: return

        val sampleCount = jni.nativeResampleAudio(outputBuffer, ratio)
        if (sampleCount <= 0) return

        val written = track.write(outputBuffer, 0, sampleCount)
        if (written > 0) {
            totalWrittenFrames += written / 2
        }
    }
}
