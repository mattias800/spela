package com.spela.player.libretro

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.logging.Logger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Desktop audio output using javax.sound.sampled with audio-sync frame pacing.
 *
 * Instead of running a separate polling thread, audio samples are written
 * directly from the emulation thread via [writeSync]. The blocking
 * SourceDataLine.write() call naturally paces emulation to the audio
 * sample rate, eliminating both audio jitter and the need for sleep-based
 * frame timing. This mirrors RetroArch's "audio sync" approach.
 *
 * The audio device is opened lazily on the first [writeSync] call,
 * ensuring the core's sample rate is available.
 */
class DesktopAudioPlayer(private val controller: DesktopLibretroController) {

    private val logger = Logger.getLogger("SpelaAudio")

    private var sourceDataLine: SourceDataLine? = null
    private var initAttempted = false

    /** Volume level 0.0 (mute) to 1.0 (full). Applied to samples in writeSync. */
    @Volatile
    var volume: Float = 1.0f

    // Pre-allocated conversion buffer (resized if needed)
    private var conversionBuffer = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)

    /**
     * Lazily open the audio device. Called automatically on the first
     * [writeSync] when audio samples are available (sample rate is set).
     */
    private fun ensureStarted(): Boolean {
        if (sourceDataLine != null) return true
        if (initAttempted) return false

        val sampleRate = controller.getSampleRate()
        if (sampleRate <= 0) return false // Not ready yet, try next frame

        initAttempted = true
        logger.info("Audio starting: sampleRate=$sampleRate")

        val format = AudioFormat(
            sampleRate.toFloat(),
            16,     // 16-bit samples
            2,      // stereo
            true,   // signed
            false,  // little-endian
        )

        // ~46ms buffer — small for low latency. Audio-sync pacing keeps the
        // buffer steadily filled, so underruns don't occur in normal operation.
        val bufferSize = (sampleRate * 2 * 2 * 0.046).toInt()

        try {
            sourceDataLine = AudioSystem.getSourceDataLine(format).also {
                it.open(format, bufferSize)
                it.start()
            }
            logger.info("Audio device opened successfully (buffer=${bufferSize} bytes, ~46ms)")
            return true
        } catch (e: Exception) {
            logger.severe("Failed to open audio device: ${e.message}")
            return false
        }
    }

    fun stop() {
        sourceDataLine?.let {
            it.drain()
            it.stop()
            it.close()
        }
        sourceDataLine = null
        initAttempted = false
    }

    /**
     * Write audio samples to the output device. Blocks until the device
     * accepts the data, which naturally paces the caller (emulation thread)
     * to the audio sample rate. This is the core of audio-sync frame pacing.
     *
     * On the first call, lazily opens the audio device (sample rate must be
     * available from the core by this point).
     *
     * Called from the emulation thread after each retro_run().
     *
     * @return true if audio was written (and blocking occurred), false if
     *         the device isn't ready yet (caller should fall back to sleep-based pacing).
     */
    fun writeSync(samples: ShortArray): Boolean {
        if (!ensureStarted()) return false
        val line = sourceDataLine ?: return false
        val needed = samples.size * 2
        if (conversionBuffer.capacity() < needed) {
            conversionBuffer = ByteBuffer.allocate(needed).order(ByteOrder.LITTLE_ENDIAN)
        }
        conversionBuffer.clear()
        // Apply logarithmic volume curve: human hearing is logarithmic, so
        // a linear slider position needs exponential scaling to feel natural.
        // vol^3 gives a good perceptual curve (50% slider ≈ 12.5% amplitude).
        val linear = volume
        val vol = linear * linear * linear
        val shortBuf = conversionBuffer.asShortBuffer()
        if (vol >= 0.999f) {
            shortBuf.put(samples)
        } else {
            for (s in samples) {
                shortBuf.put((s * vol).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
        line.write(conversionBuffer.array(), 0, needed)
        return true
    }
}
