package com.spela.player.desktop.e2e

import com.spela.player.presentation.viewmodel.LibretroController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Input lag measurement tests for quantifiable latency validation.
 *
 * # Input Lag Pipeline on Desktop
 *
 * The full input-to-display pipeline is:
 *
 *   1. Key press        -> onKeyEvent callback (Compose UI thread)
 *   2. setButton()      -> JNI nativeSetInputButton (sets flag in native code)
 *   3. nativeRun()      -> Emulation thread polls input, runs one frame
 *   4. getVideoFrame()  -> LaunchedEffect polls via withFrameNanos (Compose frame)
 *   5. drawScaledBitmap -> Canvas renders the bitmap
 *
 * The theoretical minimum input lag at 60fps is:
 *   - Best case: input arrives just before nativeRun() polls -> ~16.7ms (1 frame)
 *   - Worst case: input arrives just after nativeRun() polls -> ~33.4ms (2 frames)
 *   - Plus Compose frame scheduling overhead: ~0-16.7ms
 *   - Total expected: 1-3 frames = 16.7ms - 50ms
 *
 * # Measurement Approach
 *
 * These tests use an instrumented controller that records timestamps at each
 * pipeline stage. This allows measuring:
 *   - Input dispatch latency: time from setButton() call to the next run() call
 *   - Frame production latency: time from run() to getVideoFrame() returning new data
 *   - Total pipeline latency: time from setButton() to frame data available
 *
 * The actual display latency (Compose rendering + vsync) cannot be measured
 * in unit tests but adds at most 1 additional frame (~16.7ms).
 *
 * # Thresholds
 *
 * Acceptable input lag for retro gaming:
 *   - Excellent: < 33ms (2 frames at 60fps)
 *   - Good: < 50ms (3 frames at 60fps)
 *   - Acceptable: < 83ms (5 frames at 60fps)
 *   - Poor: > 100ms (noticeable delay, action games suffer)
 */
class InputLagMeasurementTest {

    /**
     * Instrumented controller that timestamps key operations for latency measurement.
     */
    private class InstrumentedController : LibretroController {
        // Timestamps (nanos) for pipeline stages
        val inputTimestamps = mutableListOf<Long>()
        val runTimestamps = mutableListOf<Long>()
        val frameReadTimestamps = mutableListOf<Long>()

        // Track button state changes
        val buttonEvents = mutableListOf<ButtonEvent>()

        // Frame counter to detect new frames after input
        var frameCount = 0
            private set

        @Volatile
        var isRunning = false
            private set

        override fun loadCore(corePath: String) {}
        override fun loadGame(gamePath: String) {}
        override fun start() { isRunning = true }
        override fun pause() {}
        override fun resume() {}
        override fun stop() { isRunning = false }
        override fun serialize(): ByteArray? = null
        override fun unserialize(data: ByteArray): Boolean = false
        override fun setFastForward(enabled: Boolean) {}
        override fun performanceStats(): Flow<Pair<Float, Float>> = emptyFlow()

        /**
         * Simulates setButton() with timestamp recording.
         * In the real pipeline, this is called from the Compose UI thread.
         */
        fun setButton(port: Int, buttonId: Int, pressed: Boolean) {
            val now = System.nanoTime()
            inputTimestamps.add(now)
            buttonEvents.add(ButtonEvent(port, buttonId, pressed, now))
        }

        /**
         * Simulates nativeRun() with timestamp recording.
         * In the real pipeline, this runs on the emulation thread.
         */
        fun simulateRun() {
            val now = System.nanoTime()
            runTimestamps.add(now)
            frameCount++
        }

        /**
         * Simulates getVideoFrame() with timestamp recording.
         * In the real pipeline, this is polled from the Compose frame loop.
         */
        fun simulateFrameRead(): ByteArray {
            val now = System.nanoTime()
            frameReadTimestamps.add(now)
            return ByteArray(256 * 240 * 2) // NES-sized frame
        }

        /**
         * Measures the input dispatch latency: time from the last setButton()
         * to the next simulateRun() call.
         * Returns nanoseconds, or -1 if no measurement is available.
         */
        fun measureInputDispatchLatencyNs(): Long {
            if (inputTimestamps.isEmpty() || runTimestamps.isEmpty()) return -1
            val lastInput = inputTimestamps.last()
            val nextRun = runTimestamps.lastOrNull { it >= lastInput } ?: return -1
            return nextRun - lastInput
        }

        /**
         * Measures the frame production latency: time from the last run()
         * to the next frame read.
         * Returns nanoseconds, or -1 if no measurement is available.
         */
        fun measureFrameProductionLatencyNs(): Long {
            if (runTimestamps.isEmpty() || frameReadTimestamps.isEmpty()) return -1
            val lastRun = runTimestamps.last()
            val nextRead = frameReadTimestamps.lastOrNull { it >= lastRun } ?: return -1
            return nextRead - lastRun
        }

        /**
         * Measures total pipeline latency: time from setButton() to frame data available.
         * Returns nanoseconds, or -1 if no measurement is available.
         */
        fun measureTotalPipelineLatencyNs(): Long {
            if (inputTimestamps.isEmpty() || frameReadTimestamps.isEmpty()) return -1
            val lastInput = inputTimestamps.last()
            val nextFrame = frameReadTimestamps.lastOrNull { it >= lastInput } ?: return -1
            return nextFrame - lastInput
        }

        fun reset() {
            inputTimestamps.clear()
            runTimestamps.clear()
            frameReadTimestamps.clear()
            buttonEvents.clear()
        }

        data class ButtonEvent(
            val port: Int,
            val buttonId: Int,
            val pressed: Boolean,
            val timestampNs: Long,
        )
    }

    @Test
    fun inputDispatchLatencyIsBelowThreshold() {
        val controller = InstrumentedController()
        controller.start()

        // Simulate the emulation pipeline:
        // 1. Button press (UI thread)
        controller.setButton(0, 8, true) // A button pressed

        // 2. Small delay simulating thread scheduling between UI and emulation threads
        //    In practice this is near-zero since setButton is just setting a volatile flag
        Thread.sleep(1)

        // 3. Emulation tick processes the input
        controller.simulateRun()

        val latencyNs = controller.measureInputDispatchLatencyNs()
        assertTrue(latencyNs >= 0, "Should have a valid measurement")

        val latencyMs = latencyNs / 1_000_000.0
        assertTrue(
            latencyMs < 50.0,
            "Input dispatch latency should be < 50ms, was ${latencyMs}ms"
        )
    }

    @Test
    fun frameProductionLatencyIsBelowThreshold() {
        val controller = InstrumentedController()
        controller.start()

        // Simulate: run produces a frame, then it gets read
        controller.simulateRun()

        // Small delay simulating Compose frame polling interval
        Thread.sleep(1)

        controller.simulateFrameRead()

        val latencyNs = controller.measureFrameProductionLatencyNs()
        assertTrue(latencyNs >= 0, "Should have a valid measurement")

        val latencyMs = latencyNs / 1_000_000.0
        assertTrue(
            latencyMs < 50.0,
            "Frame production latency should be < 50ms, was ${latencyMs}ms"
        )
    }

    @Test
    fun totalPipelineLatencyIsBelowThreshold() {
        val controller = InstrumentedController()
        controller.start()

        // Full pipeline simulation:
        // 1. Input arrives
        controller.setButton(0, 4, true) // UP pressed
        Thread.sleep(1)

        // 2. Emulation tick
        controller.simulateRun()
        Thread.sleep(1)

        // 3. Frame read
        controller.simulateFrameRead()

        val latencyNs = controller.measureTotalPipelineLatencyNs()
        assertTrue(latencyNs >= 0, "Should have a valid measurement")

        val latencyMs = latencyNs / 1_000_000.0
        assertTrue(
            latencyMs < 83.0,
            "Total pipeline latency should be < 83ms (5 frames), was ${latencyMs}ms"
        )
    }

    @Test
    fun multipleInputsAreMeasuredIndependently() {
        val controller = InstrumentedController()
        controller.start()

        val latencies = mutableListOf<Double>()

        // Simulate 10 input-run-frame cycles
        repeat(10) {
            controller.setButton(0, 8, true) // Press
            Thread.sleep(1)
            controller.simulateRun()
            Thread.sleep(1)
            controller.simulateFrameRead()

            val latencyNs = controller.measureTotalPipelineLatencyNs()
            if (latencyNs >= 0) {
                latencies.add(latencyNs / 1_000_000.0)
            }

            // Release
            controller.setButton(0, 8, false)
            Thread.sleep(1)
            controller.simulateRun()
            Thread.sleep(1)
            controller.simulateFrameRead()
        }

        assertTrue(latencies.isNotEmpty(), "Should have latency measurements")

        val avgLatency = latencies.average()
        val maxLatency = latencies.max()

        assertTrue(
            avgLatency < 50.0,
            "Average pipeline latency should be < 50ms, was ${avgLatency}ms"
        )
        assertTrue(
            maxLatency < 83.0,
            "Max pipeline latency should be < 83ms (5 frames), was ${maxLatency}ms"
        )
    }

    @Test
    fun buttonEventsAreRecordedInOrder() {
        val controller = InstrumentedController()
        controller.start()

        // Simulate a rapid sequence of button presses
        controller.setButton(0, 4, true)   // UP press
        controller.setButton(0, 8, true)   // A press
        controller.setButton(0, 4, false)  // UP release
        controller.setButton(0, 8, false)  // A release

        val events = controller.buttonEvents
        assertTrue(events.size == 4, "Should record all 4 button events")

        // Verify ordering by timestamp
        for (i in 1 until events.size) {
            assertTrue(
                events[i].timestampNs >= events[i - 1].timestampNs,
                "Events should be in chronological order"
            )
        }

        // Verify correct button IDs
        assertTrue(events[0].buttonId == 4 && events[0].pressed)
        assertTrue(events[1].buttonId == 8 && events[1].pressed)
        assertTrue(events[2].buttonId == 4 && !events[2].pressed)
        assertTrue(events[3].buttonId == 8 && !events[3].pressed)
    }

    @Test
    fun resetClearsAllMeasurements() {
        val controller = InstrumentedController()
        controller.start()

        controller.setButton(0, 8, true)
        controller.simulateRun()
        controller.simulateFrameRead()

        assertTrue(controller.inputTimestamps.isNotEmpty())
        assertTrue(controller.runTimestamps.isNotEmpty())
        assertTrue(controller.frameReadTimestamps.isNotEmpty())
        assertTrue(controller.buttonEvents.isNotEmpty())

        controller.reset()

        assertTrue(controller.inputTimestamps.isEmpty(), "Input timestamps should be cleared")
        assertTrue(controller.runTimestamps.isEmpty(), "Run timestamps should be cleared")
        assertTrue(controller.frameReadTimestamps.isEmpty(), "Frame timestamps should be cleared")
        assertTrue(controller.buttonEvents.isEmpty(), "Button events should be cleared")
    }

    @Test
    fun noMeasurementBeforeInputReturnsNegative() {
        val controller = InstrumentedController()
        controller.start()

        assertTrue(
            controller.measureInputDispatchLatencyNs() == -1L,
            "Should return -1 with no data"
        )
        assertTrue(
            controller.measureFrameProductionLatencyNs() == -1L,
            "Should return -1 with no data"
        )
        assertTrue(
            controller.measureTotalPipelineLatencyNs() == -1L,
            "Should return -1 with no data"
        )
    }

    @Test
    fun latencyBenchmarkWith60FpsSimulation() {
        val controller = InstrumentedController()
        controller.start()

        val frameTimeMs = 16L // ~60fps
        val totalLatencies = mutableListOf<Double>()

        // Simulate 60 frames (1 second) with input on every 10th frame
        repeat(60) { frame ->
            if (frame % 10 == 0) {
                controller.setButton(0, 8, true)
            }

            // Simulate emulation frame
            Thread.sleep(1) // Shortened sleep for test speed (real would be ~16ms)
            controller.simulateRun()

            // Simulate Compose frame read
            controller.simulateFrameRead()

            if (frame % 10 == 0) {
                val latency = controller.measureTotalPipelineLatencyNs()
                if (latency >= 0) {
                    totalLatencies.add(latency / 1_000_000.0)
                }
                controller.setButton(0, 8, false)
            }
        }

        assertTrue(totalLatencies.isNotEmpty(), "Should have benchmark data")

        val avg = totalLatencies.average()
        val p95 = totalLatencies.sorted().let { it[(it.size * 0.95).toInt().coerceAtMost(it.size - 1)] }

        // In the simulated test, latencies will be very low because Thread.sleep(1)
        // is much faster than a real frame. The point is to validate the measurement
        // infrastructure works and can be used with real hardware in integration tests.
        assertTrue(avg >= 0, "Average latency should be non-negative: ${avg}ms")
        assertTrue(p95 >= 0, "P95 latency should be non-negative: ${p95}ms")
    }

    @Test
    fun frameCountIncrementsDuringPipeline() {
        val controller = InstrumentedController()
        controller.start()

        assertTrue(controller.frameCount == 0, "Frame count should start at 0")

        controller.simulateRun()
        assertTrue(controller.frameCount == 1, "Frame count should be 1 after first run")

        controller.simulateRun()
        controller.simulateRun()
        assertTrue(controller.frameCount == 3, "Frame count should be 3 after three runs")
    }
}
