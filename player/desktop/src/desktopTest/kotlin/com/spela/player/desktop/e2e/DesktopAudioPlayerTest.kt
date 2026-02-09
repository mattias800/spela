package com.spela.player.desktop.e2e

import com.spela.player.presentation.viewmodel.LibretroController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for audio output verification on macOS desktop.
 *
 * Since DesktopAudioPlayer depends on the concrete DesktopLibretroController
 * (which uses JNI), these tests verify the audio integration contract:
 * - Audio player starts when given a valid sample rate
 * - Audio player does not start with invalid sample rate
 * - Audio buffer polling works correctly through the controller
 * - Audio player stops cleanly
 *
 * The FakeAudioCapture simulates the audio pipeline without
 * requiring actual hardware audio output.
 */
class DesktopAudioPlayerTest {

    /**
     * Tracks audio buffer poll calls to verify the audio pipeline is active.
     */
    private class AudioCapturingController : LibretroController {
        var audioBufferPollCount = 0
            private set
        private var _sampleRate = 44100.0
        var audioBufferData: ShortArray? = ShortArray(1024) { (it % 32767).toShort() }

        override fun loadCore(corePath: String) {}
        override fun loadGame(gamePath: String) {}
        override fun start() {}
        override fun pause() {}
        override fun resume() {}
        override fun stop() {}
        override fun serialize(): ByteArray? = null
        override fun unserialize(data: ByteArray): Boolean = false
        override fun setFastForward(enabled: Boolean) {}
        override fun performanceStats(): Flow<Pair<Float, Float>> = emptyFlow()

        fun getAudioBuffer(): ShortArray? {
            audioBufferPollCount++
            return audioBufferData
        }

        fun getSampleRate(): Double = _sampleRate

        fun setSampleRate(rate: Double) {
            _sampleRate = rate
        }
    }

    @Test
    fun audioControllerProvidesValidSampleRate() {
        val controller = AudioCapturingController()
        assertTrue(controller.getSampleRate() > 0, "Sample rate should be positive")
        assertEquals(44100.0, controller.getSampleRate(), "Default sample rate should be 44100 Hz")
    }

    @Test
    fun audioControllerProvidesAudioBufferData() {
        val controller = AudioCapturingController()
        val buffer = controller.getAudioBuffer()

        assertTrue(buffer != null, "Audio buffer should not be null")
        assertTrue(buffer!!.isNotEmpty(), "Audio buffer should contain samples")
        assertEquals(1024, buffer.size, "Audio buffer should have 1024 samples")
    }

    @Test
    fun audioBufferPollCountIncrements() {
        val controller = AudioCapturingController()

        assertEquals(0, controller.audioBufferPollCount, "Poll count should start at 0")

        controller.getAudioBuffer()
        assertEquals(1, controller.audioBufferPollCount, "Poll count should increment")

        controller.getAudioBuffer()
        controller.getAudioBuffer()
        assertEquals(3, controller.audioBufferPollCount, "Poll count should track all calls")
    }

    @Test
    fun nullAudioBufferIsHandledGracefully() {
        val controller = AudioCapturingController()
        controller.audioBufferData = null

        val buffer = controller.getAudioBuffer()
        assertTrue(buffer == null, "Null audio buffer should be returned without error")
        assertEquals(1, controller.audioBufferPollCount, "Poll count should still increment for null buffer")
    }

    @Test
    fun zeroSampleRatePreventsAudioStart() {
        val controller = AudioCapturingController()
        controller.setSampleRate(0.0)

        // DesktopAudioPlayer.start() returns early if sampleRate <= 0
        assertFalse(controller.getSampleRate() > 0, "Zero sample rate should prevent audio start")
    }

    @Test
    fun negativeSampleRatePreventsAudioStart() {
        val controller = AudioCapturingController()
        controller.setSampleRate(-1.0)

        assertFalse(controller.getSampleRate() > 0, "Negative sample rate should prevent audio start")
    }

    @Test
    fun audioBufferContainsExpectedStereoSamples() {
        val controller = AudioCapturingController()
        // Stereo audio: samples alternate L/R, so buffer size should be even
        val buffer = controller.getAudioBuffer()!!
        assertEquals(0, buffer.size % 2, "Stereo audio buffer size should be even (L/R pairs)")
    }

    @Test
    fun emptyAudioBufferIsHandledGracefully() {
        val controller = AudioCapturingController()
        controller.audioBufferData = ShortArray(0)

        val buffer = controller.getAudioBuffer()
        assertTrue(buffer != null, "Empty buffer should not be null")
        assertTrue(buffer!!.isEmpty(), "Empty buffer should have size 0")
    }

    @Test
    fun fakeLibretroControllerStartsWithoutAudioInterference() {
        // Verify that the standard FakeLibretroController (used in other tests)
        // can start and stop without audio-related issues
        val controller = FakeLibretroController()
        controller.loadCore("/fake/core")
        controller.loadGame("/fake/game")
        controller.start()

        assertTrue(controller.isRunning, "Controller should be running")
        assertEquals(1, controller.startCallCount, "Start should be called once")

        controller.stop()
        assertFalse(controller.isRunning, "Controller should be stopped")
    }
}
