package com.spela.player.libretro

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Manages an Android AudioTrack for libretro audio output.
 * Receives stereo interleaved int16 samples and plays them.
 */
class AudioOutput(private val sampleRate: Int) {

    private var audioTrack: AudioTrack? = null

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
    }

    fun pause() {
        audioTrack?.pause()
    }

    fun resume() {
        audioTrack?.play()
    }

    fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    /**
     * Write stereo interleaved int16 samples to the AudioTrack.
     * Called from the emulation thread after each retro_run().
     */
    fun writeSamples(samples: ShortArray) {
        audioTrack?.write(samples, 0, samples.size)
    }

    /**
     * Write a specific number of stereo interleaved int16 samples to the AudioTrack.
     * Used with pre-allocated buffers where only part of the array contains valid data.
     */
    fun writeSamples(samples: ShortArray, count: Int) {
        audioTrack?.write(samples, 0, count)
    }
}
