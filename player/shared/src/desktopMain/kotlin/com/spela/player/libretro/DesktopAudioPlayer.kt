package com.spela.player.libretro

import java.util.logging.Logger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Desktop audio output: Android-quality SINC resampling + dynamic rate
 * control, with the device write **decoupled onto its own thread**.
 *
 *   core samples (e.g. 44100 / 32040 Hz) → native SINC resampler
 *   → ring buffer (48000 Hz) ──[SpelaAudioOut thread]──▶ SourceDataLine
 *
 * Why decoupled (#1288): the Android path does a blocking `AudioTrack.write`
 * directly on the emulation thread, and that's fine there. On desktop the
 * equivalent blocking `SourceDataLine.write` periodically stalls the whole
 * emulation loop for ~the device-buffer duration (~200ms) whenever the device
 * buffer fills — verified as 18–34 fps with 200ms frameTime spikes on SNES.
 * So here the emulation thread only ever [write]s into a ring buffer (resample
 * + non-blocking copy); a dedicated thread drains the ring to the device with
 * the blocking write. Emulation is never blocked by audio I/O.
 *
 * Dynamic rate control (±0.5%, RetroArch/Android's formula) nudges the
 * resampling ratio based on **ring** fill to keep it ~half full — eliminating
 * underruns/overruns and smoothing per-frame jitter. precisionSleep in the
 * emulation loop provides the realtime pacing the blocking write used to.
 *
 * The device + ring + output thread open lazily on the first [write] once the
 * core's sample rate is known.
 */
class DesktopAudioPlayer(private val controller: DesktopLibretroController) {

    private val logger = Logger.getLogger("SpelaAudio")

    private var line: SourceDataLine? = null
    private var initAttempted = false

    /** Volume level 0.0 (mute) to 1.0 (full). Applied to samples in [write]. */
    @Volatile
    var volume: Float = 1.0f

    /** Base resampling ratio: OUTPUT_RATE / coreSampleRate. Set on open. */
    private var baseRatio = 1.0

    /** Reusable resampler output (interleaved int16) — written on the emu thread. */
    private var resampleBuf = ShortArray(16384)

    // Ring buffer of resampled interleaved-stereo int16 at OUTPUT_RATE. The DRC
    // target buffer (mirrors the role of Android's AudioTrack buffer). Guarded
    // by `lock`; drained by the output thread, filled by [write].
    private var ring = ShortArray(0)
    private var head = 0
    private var tail = 0
    private var count = 0 // shorts currently buffered
    private val lock = Any()

    @Volatile
    private var running = false
    private var outputThread: Thread? = null

    companion object {
        const val OUTPUT_RATE = 48000
        private const val FRAME_BYTES = 4 // stereo * 16-bit

        /** Ring capacity in milliseconds — the DRC keeps it ~half full, so it
         *  contributes ~half this to latency. Kept small for low latency; the
         *  per-frame audio burst (~17ms at 60fps) plus normal jitter fits
         *  comfortably in half of this. */
        private const val RING_MS = 60

        /** Device line buffer — the output thread keeps it fed; it's the last
         *  line of defence against an underrun (audible crackle), so don't
         *  shrink it as aggressively as the ring. */
        private const val DEVICE_MS = 30

        /** Rate control delta (matches RetroArch / Android). ±0.5% at buffer
         *  extremes — ~8.6 cents, well below audibility. */
        private const val RATE_CONTROL_DELTA = 0.005
    }

    /** Lazily open the device + ring + output thread. Returns false until the
     *  core's sample rate is available (try again next frame). */
    private fun ensureStarted(): Boolean {
        if (line != null) return true
        if (initAttempted) return false

        val coreSampleRate = controller.getSampleRate()
        if (coreSampleRate <= 0.0) return false // not ready yet

        initAttempted = true
        baseRatio = OUTPUT_RATE.toDouble() / coreSampleRate
        ring = ShortArray(OUTPUT_RATE * 2 * RING_MS / 1000)

        val format = AudioFormat(OUTPUT_RATE.toFloat(), 16, 2, true, false) // signed, little-endian
        val deviceBytes = OUTPUT_RATE * FRAME_BYTES * DEVICE_MS / 1000

        return try {
            line = AudioSystem.getSourceDataLine(format).also {
                it.open(format, deviceBytes)
                it.start()
            }
            running = true
            outputThread = Thread({ outputLoop() }, "SpelaAudioOut").apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY
                start()
            }
            logger.info(
                "Audio started: core=$coreSampleRate out=$OUTPUT_RATE " +
                    "baseRatio=${"%.4f".format(baseRatio)} ring=${RING_MS}ms device=${DEVICE_MS}ms",
            )
            true
        } catch (e: Exception) {
            logger.severe("Failed to open audio device: ${e.message}")
            false
        }
    }

    /**
     * Dynamic resampling ratio from the **ring** fill (RetroArch's
     * audio_driver_flush formula): emptier than half → ratio up (more output);
     * fuller than half → ratio down (drain). Keeps the ring ~half full.
     */
    fun calculateRatio(): Double {
        if (ring.isEmpty()) return baseRatio
        val avail = synchronized(lock) { ring.size - count } // free shorts
        val half = (ring.size / 2).coerceAtLeast(1)
        val direction = (avail - half).toDouble() / half
        return baseRatio * (1.0 + RATE_CONTROL_DELTA * direction)
    }

    /**
     * Resample the core's buffered audio at [ratio] and enqueue it into the
     * ring — **non-blocking**, called from the emulation thread. The output
     * thread does the blocking device write, so emulation never stalls on audio.
     */
    fun write(ratio: Double) {
        if (!ensureStarted()) return
        val n = controller.resampleAudio(resampleBuf, ratio) // interleaved shorts
        if (n <= 0) return

        val vol = volume * volume * volume // perceptual curve, matches old writeSync
        if (vol < 0.999f) {
            for (i in 0 until n) {
                resampleBuf[i] = (resampleBuf[i] * vol).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        synchronized(lock) {
            val cap = ring.size
            for (i in 0 until n) {
                if (count >= cap) { // ring full (output thread fell behind) — drop oldest
                    head = (head + 1) % cap
                    count--
                }
                ring[tail] = resampleBuf[i]
                tail = (tail + 1) % cap
                count++
            }
        }
    }

    private val scratch = ShortArray(4096)
    private val scratchBytes = ByteArray(8192)

    private fun outputLoop() {
        val l = line ?: return
        while (running) {
            var n: Int
            synchronized(lock) {
                n = minOf(scratch.size, count)
                for (i in 0 until n) {
                    scratch[i] = ring[head]
                    head = (head + 1) % ring.size
                }
                count -= n
            }
            if (n == 0) {
                Thread.sleep(2) // ring empty — device buffer covers the gap
                continue
            }
            var bi = 0
            for (i in 0 until n) {
                val s = scratch[i].toInt()
                scratchBytes[bi++] = (s and 0xFF).toByte()
                scratchBytes[bi++] = ((s shr 8) and 0xFF).toByte()
            }
            try {
                l.write(scratchBytes, 0, n * 2) // blocking — on THIS thread, not emulation
            } catch (_: Exception) {
                break
            }
        }
    }

    fun pause() {
        line?.stop()
    }

    fun resume() {
        line?.start()
    }

    fun stop() {
        running = false
        outputThread?.join(500)
        outputThread = null
        line?.let {
            it.flush()
            it.stop()
            it.close()
        }
        line = null
        initAttempted = false
        synchronized(lock) { head = 0; tail = 0; count = 0 }
    }
}
