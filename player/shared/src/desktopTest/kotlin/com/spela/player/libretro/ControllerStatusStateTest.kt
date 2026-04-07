package com.spela.player.libretro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControllerStatusStateTest {

    @Test
    fun emptyStateHasNoConnectedPorts() {
        val state = ControllerStatusState.Empty
        assertEquals(4, state.ports.size)
        assertEquals(0, state.connectedCount)
        assertFalse(state.isMultiplayer)
        state.ports.forEach { assertFalse(it.connected); assertFalse(it.active) }
    }

    @Test
    fun singleControllerIsNotMultiplayer() {
        val occupied = BooleanArray(8) { it == 0 }
        val activity = LongArray(8)
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertEquals(1, state.connectedCount)
        assertFalse(state.isMultiplayer)
        assertTrue(state.ports[0].connected)
        assertFalse(state.ports[1].connected)
    }

    @Test
    fun twoControllersIsMultiplayer() {
        val occupied = BooleanArray(8) { it == 0 || it == 1 }
        val activity = LongArray(8)
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertEquals(2, state.connectedCount)
        assertTrue(state.isMultiplayer)
    }

    @Test
    fun recentActivityMarksPortActive() {
        val occupied = BooleanArray(8) { it == 0 }
        val activity = LongArray(8).also { it[0] = 900L }
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertTrue(state.ports[0].active, "Port 0 should be active (100ms ago < 300ms timeout)")
    }

    @Test
    fun expiredActivityMarksPortInactive() {
        val occupied = BooleanArray(8) { it == 0 }
        val activity = LongArray(8).also { it[0] = 500L }
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertFalse(state.ports[0].active, "Port 0 should be inactive (500ms ago >= 300ms timeout)")
    }

    @Test
    fun disconnectedPortIsNeverActive() {
        val occupied = BooleanArray(8) // all false
        val activity = LongArray(8).also { it[0] = 999L }
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertFalse(state.ports[0].active, "Disconnected port should not be active even with recent timestamp")
    }

    @Test
    fun onlyFirstFourPortsAreIncluded() {
        val occupied = BooleanArray(8) { true }
        val activity = LongArray(8)
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertEquals(4, state.ports.size)
        assertEquals(4, state.connectedCount)
    }

    @Test
    fun nonContiguousPortsHandledCorrectly() {
        val occupied = BooleanArray(8) { it == 0 || it == 2 }
        val activity = LongArray(8).also { it[2] = 950L }
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertTrue(state.ports[0].connected)
        assertFalse(state.ports[0].active)
        assertFalse(state.ports[1].connected)
        assertTrue(state.ports[2].connected)
        assertTrue(state.ports[2].active)
        assertTrue(state.isMultiplayer)
    }
}
