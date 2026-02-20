package com.spela.player.libretro

import com.spela.player.presentation.viewmodel.LibretroAnalog
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnalogAxisTrackerTest {

    private val tracker = AnalogAxisTracker()

    @Test
    fun singleDirectionPressedReturnsFullDeflection() {
        val result = tracker.update(LibretroAnalog.LEFT_STICK_UP, true)
        assertNotNull(result)
        assertEquals(LibretroAnalog.STICK_LEFT, result.stickIndex)
        assertEquals(LibretroAnalog.AXIS_Y, result.axisId)
        assertEquals((-32767).toShort(), result.value)
    }

    @Test
    fun downDirectionReturnsPositiveDeflection() {
        val result = tracker.update(LibretroAnalog.LEFT_STICK_DOWN, true)
        assertNotNull(result)
        assertEquals(LibretroAnalog.STICK_LEFT, result.stickIndex)
        assertEquals(LibretroAnalog.AXIS_Y, result.axisId)
        assertEquals(32767.toShort(), result.value)
    }

    @Test
    fun leftDirectionReturnsNegativeXDeflection() {
        val result = tracker.update(LibretroAnalog.LEFT_STICK_LEFT, true)
        assertNotNull(result)
        assertEquals(LibretroAnalog.STICK_LEFT, result.stickIndex)
        assertEquals(LibretroAnalog.AXIS_X, result.axisId)
        assertEquals((-32767).toShort(), result.value)
    }

    @Test
    fun rightDirectionReturnsPositiveXDeflection() {
        val result = tracker.update(LibretroAnalog.LEFT_STICK_RIGHT, true)
        assertNotNull(result)
        assertEquals(LibretroAnalog.STICK_LEFT, result.stickIndex)
        assertEquals(LibretroAnalog.AXIS_X, result.axisId)
        assertEquals(32767.toShort(), result.value)
    }

    @Test
    fun opposingDirectionsCancelToZero() {
        tracker.update(LibretroAnalog.LEFT_STICK_UP, true)
        val result = tracker.update(LibretroAnalog.LEFT_STICK_DOWN, true)
        assertNotNull(result)
        assertEquals(0.toShort(), result.value)
    }

    @Test
    fun releaseOneWhileOtherHeldReturnsRemainingDirection() {
        tracker.update(LibretroAnalog.LEFT_STICK_UP, true)
        tracker.update(LibretroAnalog.LEFT_STICK_DOWN, true)
        // Both held -> 0. Now release UP, DOWN should win.
        val result = tracker.update(LibretroAnalog.LEFT_STICK_UP, false)
        assertNotNull(result)
        assertEquals(LibretroAnalog.AXIS_Y, result.axisId)
        assertEquals(32767.toShort(), result.value) // DOWN = +1
    }

    @Test
    fun releaseAllReturnsZero() {
        tracker.update(LibretroAnalog.LEFT_STICK_UP, true)
        val result = tracker.update(LibretroAnalog.LEFT_STICK_UP, false)
        assertNotNull(result)
        assertEquals(0.toShort(), result.value)
    }

    @Test
    fun nonAnalogIdReturnsNull() {
        val result = tracker.update(LibretroButtons.A, true)
        assertNull(result)
    }

    @Test
    fun regularButtonIdReturnsNull() {
        val result = tracker.update(0, true)
        assertNull(result)
    }

    @Test
    fun rightStickWorksIndependently() {
        tracker.update(LibretroAnalog.LEFT_STICK_UP, true)
        val result = tracker.update(LibretroAnalog.RIGHT_STICK_LEFT, true)
        assertNotNull(result)
        assertEquals(LibretroAnalog.STICK_RIGHT, result.stickIndex)
        assertEquals(LibretroAnalog.AXIS_X, result.axisId)
        assertEquals((-32767).toShort(), result.value)
    }

    @Test
    fun clearResetsState() {
        tracker.update(LibretroAnalog.LEFT_STICK_UP, true)
        tracker.clear()
        // After clear, pressing DOWN should only see DOWN (no opposing UP)
        val result = tracker.update(LibretroAnalog.LEFT_STICK_DOWN, true)
        assertNotNull(result)
        assertEquals(32767.toShort(), result.value)
    }

    @Test
    fun opposingXDirectionsCancelToZero() {
        tracker.update(LibretroAnalog.LEFT_STICK_LEFT, true)
        val result = tracker.update(LibretroAnalog.LEFT_STICK_RIGHT, true)
        assertNotNull(result)
        assertEquals(LibretroAnalog.AXIS_X, result.axisId)
        assertEquals(0.toShort(), result.value)
    }
}
