package com.spela.player.netplay

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetplayInputBufferTest {

    @Test
    fun setAndGetLocalInput() = runTest {
        val buffer = NetplayInputBuffer()
        val input = InputState(buttonState = 0x00FFu, analogX = 10, analogY = -10)

        buffer.setLocalInput(frame = 1, port = 0, input = input)
        val result = buffer.getInputsForFrame(1)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(input, result[0])
    }

    @Test
    fun setAndGetRemoteInput() = runTest {
        val buffer = NetplayInputBuffer()
        val input = InputState(buttonState = 0x00ABu, analogX = -50, analogY = 50)

        buffer.setRemoteInput(frame = 1, port = 1, input = input)
        val result = buffer.getInputsForFrame(1)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(input, result[1])
    }

    @Test
    fun multiplePlayersOnSameFrame() = runTest {
        val buffer = NetplayInputBuffer()
        val input0 = InputState(buttonState = 1u)
        val input1 = InputState(buttonState = 2u)

        buffer.setLocalInput(frame = 5, port = 0, input = input0)
        buffer.setRemoteInput(frame = 5, port = 1, input = input1)

        val result = buffer.getInputsForFrame(5)
        assertNotNull(result)
        assertEquals(2, result.size)
        assertEquals(input0, result[0])
        assertEquals(input1, result[1])
    }

    @Test
    fun hasAllInputsForFrameReturnsFalseWhenMissing() = runTest {
        val buffer = NetplayInputBuffer()
        val input = InputState(buttonState = 1u)

        buffer.setLocalInput(frame = 1, port = 0, input = input)

        assertFalse(buffer.hasAllInputsForFrame(1, playerCount = 2))
    }

    @Test
    fun hasAllInputsForFrameReturnsTrueWhenComplete() = runTest {
        val buffer = NetplayInputBuffer()

        buffer.setLocalInput(frame = 1, port = 0, input = InputState(buttonState = 1u))
        buffer.setRemoteInput(frame = 1, port = 1, input = InputState(buttonState = 2u))

        assertTrue(buffer.hasAllInputsForFrame(1, playerCount = 2))
    }

    @Test
    fun hasAllInputsForFrameReturnsFalseForNonexistentFrame() = runTest {
        val buffer = NetplayInputBuffer()
        assertFalse(buffer.hasAllInputsForFrame(999, playerCount = 1))
    }

    @Test
    fun getInputsForFrameReturnsNullForNonexistentFrame() = runTest {
        val buffer = NetplayInputBuffer()
        assertNull(buffer.getInputsForFrame(999))
    }

    @Test
    fun clearRemovesAllInputs() = runTest {
        val buffer = NetplayInputBuffer()

        buffer.setLocalInput(frame = 1, port = 0, input = InputState())
        buffer.setLocalInput(frame = 2, port = 0, input = InputState())
        buffer.setRemoteInput(frame = 1, port = 1, input = InputState())

        buffer.clear()

        assertNull(buffer.getInputsForFrame(1))
        assertNull(buffer.getInputsForFrame(2))
    }

    @Test
    fun bufferEvictsOldEntriesWhenFull() = runTest {
        val bufferSize = 4
        val buffer = NetplayInputBuffer(bufferSize = bufferSize)

        // Fill beyond capacity
        for (frame in 1L..6L) {
            buffer.setLocalInput(frame = frame, port = 0, input = InputState(buttonState = frame.toUShort()))
        }

        // Oldest frames should be evicted
        assertNull(buffer.getInputsForFrame(1))
        assertNull(buffer.getInputsForFrame(2))

        // Newest frames should be present
        assertNotNull(buffer.getInputsForFrame(3))
        assertNotNull(buffer.getInputsForFrame(4))
        assertNotNull(buffer.getInputsForFrame(5))
        assertNotNull(buffer.getInputsForFrame(6))
    }

    @Test
    fun overwriteInputOnSameFrameAndPort() = runTest {
        val buffer = NetplayInputBuffer()
        val input1 = InputState(buttonState = 1u)
        val input2 = InputState(buttonState = 2u)

        buffer.setLocalInput(frame = 1, port = 0, input = input1)
        buffer.setLocalInput(frame = 1, port = 0, input = input2)

        val result = buffer.getInputsForFrame(1)
        assertNotNull(result)
        assertEquals(input2, result[0])
    }

    @Test
    fun multipleFramesIndependent() = runTest {
        val buffer = NetplayInputBuffer()

        buffer.setLocalInput(frame = 1, port = 0, input = InputState(buttonState = 1u))
        buffer.setLocalInput(frame = 2, port = 0, input = InputState(buttonState = 2u))
        buffer.setLocalInput(frame = 3, port = 0, input = InputState(buttonState = 3u))

        val frame1 = buffer.getInputsForFrame(1)
        val frame2 = buffer.getInputsForFrame(2)
        val frame3 = buffer.getInputsForFrame(3)

        assertNotNull(frame1)
        assertNotNull(frame2)
        assertNotNull(frame3)
        assertEquals(1u.toUShort(), frame1[0]?.buttonState)
        assertEquals(2u.toUShort(), frame2[0]?.buttonState)
        assertEquals(3u.toUShort(), frame3[0]?.buttonState)
    }

    @Test
    fun awaitInputsForFrameReturnsWhenComplete() = runTest {
        val buffer = NetplayInputBuffer()

        // Set both inputs before awaiting
        buffer.setLocalInput(frame = 1, port = 0, input = InputState(buttonState = 1u))
        buffer.setRemoteInput(frame = 1, port = 1, input = InputState(buttonState = 2u))

        val result = buffer.awaitInputsForFrame(frame = 1, playerCount = 2, timeoutMs = 1000)

        assertNotNull(result)
        assertEquals(2, result.size)
    }

    @Test
    fun awaitInputsForFrameTimesOutWhenIncomplete() = runTest {
        val buffer = NetplayInputBuffer()

        buffer.setLocalInput(frame = 1, port = 0, input = InputState(buttonState = 1u))
        // Do not set remote input

        val result = buffer.awaitInputsForFrame(frame = 1, playerCount = 2, timeoutMs = 50)

        assertNull(result)
    }

    @Test
    fun awaitInputsForFrameCompletesWhenRemoteArrives() = runTest {
        val buffer = NetplayInputBuffer()

        buffer.setLocalInput(frame = 1, port = 0, input = InputState(buttonState = 1u))

        val result = async {
            buffer.awaitInputsForFrame(frame = 1, playerCount = 2, timeoutMs = 5000)
        }

        // Simulate remote input arriving after a short delay
        launch {
            delay(20)
            buffer.setRemoteInput(frame = 1, port = 1, input = InputState(buttonState = 2u))
        }

        val inputs = result.await()
        assertNotNull(inputs)
        assertEquals(2, inputs.size)
    }

    @Test
    fun concurrentAccessDoesNotCorruptState() = runTest {
        val buffer = NetplayInputBuffer()
        val frameCount = 100

        // Simulate concurrent local and remote input writes
        val localJob = launch {
            for (frame in 1L..frameCount) {
                buffer.setLocalInput(frame = frame, port = 0, input = InputState(buttonState = frame.toUShort()))
            }
        }

        val remoteJob = launch {
            for (frame in 1L..frameCount) {
                buffer.setRemoteInput(frame = frame, port = 1, input = InputState(buttonState = (frame + 100).toUShort()))
            }
        }

        localJob.join()
        remoteJob.join()

        // Verify all frames have at least one input set correctly
        for (frame in 1L..frameCount) {
            val inputs = buffer.getInputsForFrame(frame)
            assertNotNull(inputs, "Frame $frame should have inputs")
            assertTrue(inputs.isNotEmpty(), "Frame $frame should have at least one input")
        }
    }

    @Test
    fun threePlayerInputs() = runTest {
        val buffer = NetplayInputBuffer()

        buffer.setLocalInput(frame = 1, port = 0, input = InputState(buttonState = 1u))
        buffer.setRemoteInput(frame = 1, port = 1, input = InputState(buttonState = 2u))
        buffer.setRemoteInput(frame = 1, port = 2, input = InputState(buttonState = 3u))

        assertTrue(buffer.hasAllInputsForFrame(1, playerCount = 3))
        assertFalse(buffer.hasAllInputsForFrame(1, playerCount = 4))
    }

    @Test
    fun getInputsReturnsDefensiveCopy() = runTest {
        val buffer = NetplayInputBuffer()
        buffer.setLocalInput(frame = 1, port = 0, input = InputState(buttonState = 1u))

        val result1 = buffer.getInputsForFrame(1)
        val result2 = buffer.getInputsForFrame(1)

        assertNotNull(result1)
        assertNotNull(result2)
        assertEquals(result1, result2)
    }
}
