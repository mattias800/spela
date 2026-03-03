package com.spela.player.libretro

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Android audio output using AudioTrack with audio-sync frame pacing.
 *
 * Audio samples are written directly from the emulation thread via [writeSync].
 * The blocking AudioTrack.write() call naturally paces emulation to the audio
 * sample rate, eliminating both audio jitter and the need for sleep-based
 * frame timing. This mirrors RetroArch's "audio sync" approach and matches
 * the desktop's [DesktopAudioPlayer].
 *
 * The AudioTrack is opened lazily on the first [writeSync] call.
 */
class AndroidAudioPlayer(private val sampleRate: Int) {

    private var audioTrack: AudioTrack? = null
    private var initAttempted = false

    companion object {
        private const val TAG = "AndroidAudioPlayer"
    }

    /**
     * Lazily open the AudioTrack. Called automatically on the first [writeSync].
     */
    private fun ensureStarted(): Boolean {
        if (audioTrack != null) return true
        if (initAttempted) return false

        initAttempted = true
        Log.i(TAG, "Audio starting: sampleRate=$sampleRate")

        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        // ~46ms buffer matching desktop — small for low latency.
        // Audio-sync pacing keeps the buffer steadily filled.
        // Each sample frame = 4 bytes (2 channels * 16-bit).
        val targetBufSize = sampleRate * 4 * 46 / 1000
        val bufferSize = maxOf(minBufSize, targetBufSize)

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
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            Log.i(TAG, "AudioTrack opened (buffer=$bufferSize bytes, ~${bufferSize * 1000 / (sampleRate * 4)}ms)")
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
     * Write audio samples to the AudioTrack. Blocks until the device
     * accepts the data, which naturally paces the caller (emulation thread)
     * to the audio sample rate. This is the core of audio-sync frame pacing.
     *
     * @param samples Stereo interleaved int16 samples
     * @param count Number of valid samples in the array
     * @return true if audio was written (and blocking occurred), false if
     *         the device isn't ready (caller should fall back to sleep-based pacing).
     */
    fun writeSync(samples: ShortArray, count: Int): Boolean {
        if (count <= 0) return false
        if (!ensureStarted()) return false
        val track = audioTrack ?: return false

        val written = track.write(samples, 0, count, AudioTrack.WRITE_BLOCKING)
        return written > 0
    }
}
