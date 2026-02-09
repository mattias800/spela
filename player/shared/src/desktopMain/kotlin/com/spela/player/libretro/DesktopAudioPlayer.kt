package com.spela.player.libretro

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Desktop audio output using javax.sound.sampled.
 *
 * Runs a background thread that polls audio samples from the
 * [DesktopLibretroController] and writes them to the system audio device.
 */
class DesktopAudioPlayer(private val controller: DesktopLibretroController) {

    private var audioThread: Thread? = null

    @Volatile
    private var running = false

    private var sourceDataLine: SourceDataLine? = null

    fun start(sampleRate: Double) {
        if (sampleRate <= 0) return

        running = true
        audioThread = Thread({
            runAudioLoop(sampleRate)
        }, "SpelaAudio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        audioThread?.join(1000)
        audioThread = null
        sourceDataLine?.let {
            it.drain()
            it.stop()
            it.close()
        }
        sourceDataLine = null
    }

    private fun runAudioLoop(sampleRate: Double) {
        val format = AudioFormat(
            sampleRate.toFloat(),
            16,     // 16-bit samples
            2,      // stereo
            true,   // signed
            false,  // little-endian
        )

        val line = try {
            AudioSystem.getSourceDataLine(format).also {
                // Buffer for ~50ms of audio
                val bufferSize = (sampleRate * 2 * 2 * 0.05).toInt()
                it.open(format, bufferSize)
                it.start()
            }
        } catch (e: Exception) {
            System.err.println("Failed to open audio device: ${e.message}")
            return
        }
        sourceDataLine = line

        while (running) {
            val samples = controller.getAudioBuffer()
            if (samples != null && samples.isNotEmpty()) {
                // Convert ShortArray to ByteArray (little-endian)
                val bytes = ByteArray(samples.size * 2)
                for (i in samples.indices) {
                    val s = samples[i].toInt()
                    bytes[i * 2] = (s and 0xFF).toByte()
                    bytes[i * 2 + 1] = (s shr 8 and 0xFF).toByte()
                }
                line.write(bytes, 0, bytes.size)
            } else {
                // No audio data yet, sleep briefly to avoid busy-spinning
                Thread.sleep(1)
            }
        }
    }
}
