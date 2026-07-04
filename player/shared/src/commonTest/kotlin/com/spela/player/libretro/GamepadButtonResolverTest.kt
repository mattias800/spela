package com.spela.player.libretro

import com.spela.player.domain.model.DefaultGamepadMapping
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GamepadButtonResolverTest {

    private fun pressed(vararg positions: GamepadPosition): BooleanArray {
        val a = BooleanArray(GamepadPosition.entries.size)
        positions.forEach { a[it.ordinal] = true }
        return a
    }

    @Test
    fun defaultMappingRoutesSouthToB() {
        val out = GamepadButtonResolver.resolve(pressed(GamepadPosition.SOUTH), DefaultGamepadMapping.POSITION_TO_RETRO)
        assertTrue(out[LibretroButtons.B])
        assertFalse(out[LibretroButtons.A])
    }

    @Test
    fun guidingExampleSouthToANesA() {
        // Remap SOUTH→A (NES A), WEST→B (NES B).
        val mapping = DefaultGamepadMapping.POSITION_TO_RETRO + mapOf(
            GamepadPosition.SOUTH to LibretroButtons.A,
            GamepadPosition.WEST to LibretroButtons.B,
        )
        val south = GamepadButtonResolver.resolve(pressed(GamepadPosition.SOUTH), mapping)
        assertTrue(south[LibretroButtons.A])
        val west = GamepadButtonResolver.resolve(pressed(GamepadPosition.WEST), mapping)
        assertTrue(west[LibretroButtons.B])
    }

    @Test
    fun fanInOrsPressesToSameRetroButton() {
        // Two positions mapped to the same RetroPad id: pressing either presses it.
        val mapping = mapOf(
            GamepadPosition.SOUTH to LibretroButtons.A,
            GamepadPosition.EAST to LibretroButtons.A,
        )
        assertTrue(GamepadButtonResolver.resolve(pressed(GamepadPosition.SOUTH), mapping)[LibretroButtons.A])
        assertTrue(GamepadButtonResolver.resolve(pressed(GamepadPosition.EAST), mapping)[LibretroButtons.A])
        // Pressing SOUTH while EAST is released still reports A pressed (no clobber).
        assertTrue(GamepadButtonResolver.resolve(pressed(GamepadPosition.SOUTH), mapping)[LibretroButtons.A])
    }

    @Test
    fun unmappedPositionProducesNoPress() {
        val mapping = emptyMap<GamepadPosition, Int>()
        val out = GamepadButtonResolver.resolve(pressed(GamepadPosition.SOUTH), mapping)
        assertFalse(out.any { it })
    }

    @Test
    fun nothingPressedProducesAllFalse() {
        val out = GamepadButtonResolver.resolve(BooleanArray(16), DefaultGamepadMapping.POSITION_TO_RETRO)
        assertFalse(out.any { it })
    }

    @Test
    fun analogTriggerPressureUsesMappedRetroButtonIds() {
        val mapping = DefaultGamepadMapping.POSITION_TO_RETRO + mapOf(
            GamepadPosition.L2 to LibretroButtons.R2,
            GamepadPosition.R2 to LibretroButtons.L2,
        )

        val out = GamepadButtonResolver.resolveAnalogTriggerPressures(
            l2 = 1000.toShort(),
            r2 = 2000.toShort(),
            mapping = mapping,
        )

        assertEquals(1000.toShort(), out[LibretroButtons.R2])
        assertEquals(2000.toShort(), out[LibretroButtons.L2])
    }

    @Test
    fun analogTriggerPressureUsesStrongestPressureForFanInMapping() {
        val mapping = mapOf(
            GamepadPosition.L2 to LibretroButtons.L2,
            GamepadPosition.R2 to LibretroButtons.L2,
        )

        val out = GamepadButtonResolver.resolveAnalogTriggerPressures(
            l2 = 500.toShort(),
            r2 = 2500.toShort(),
            mapping = mapping,
        )

        assertEquals(mapOf(LibretroButtons.L2 to 2500.toShort()), out)
    }

    @Test
    fun analogTriggerPressureSkipsMissingAxesAndInvalidMappings() {
        val mapping = mapOf(
            GamepadPosition.L2 to 99,
            GamepadPosition.R2 to LibretroButtons.R2,
        )

        val out = GamepadButtonResolver.resolveAnalogTriggerPressures(
            l2 = 1000.toShort(),
            r2 = null,
            mapping = mapping,
        )

        assertEquals(emptyMap(), out)
    }

    @Test
    fun analogTriggerRouteTrackerReturnsIdsRemovedByLiveRemap() {
        val tracker = AnalogTriggerRouteTracker(portCount = 2)

        assertEquals(emptySet(), tracker.update(port = 0, currentIds = setOf(LibretroButtons.L2)))
        assertEquals(setOf(LibretroButtons.L2), tracker.update(port = 0, currentIds = setOf(LibretroButtons.R2)))
        assertEquals(setOf(LibretroButtons.R2), tracker.update(port = 0, currentIds = emptySet()))
    }

    @Test
    fun analogTriggerRouteTrackerKeepsPortsIndependent() {
        val tracker = AnalogTriggerRouteTracker(portCount = 2)

        tracker.update(port = 0, currentIds = setOf(LibretroButtons.L2))
        tracker.update(port = 1, currentIds = setOf(LibretroButtons.R2))

        assertEquals(setOf(LibretroButtons.L2), tracker.clearPort(0))
        assertEquals(setOf(LibretroButtons.R2), tracker.clearPort(1))
    }

    @Test
    fun assignmentChangeInvalidatesRemovedDevicePort() {
        assertEquals(
            setOf(0),
            AnalogTriggerRouteTracker.portsInvalidatedByAssignmentChange(
                previousAssignments = mapOf(42 to 0),
                currentAssignments = emptyMap(),
            ),
        )
    }

    @Test
    fun assignmentChangeInvalidatesMovedDevicePorts() {
        assertEquals(
            setOf(0, 1),
            AnalogTriggerRouteTracker.portsInvalidatedByAssignmentChange(
                previousAssignments = mapOf(42 to 0),
                currentAssignments = mapOf(42 to 1),
            ),
        )
    }

    @Test
    fun assignmentChangeInvalidatesReplacedDevicePort() {
        assertEquals(
            setOf(0),
            AnalogTriggerRouteTracker.portsInvalidatedByAssignmentChange(
                previousAssignments = mapOf(42 to 0),
                currentAssignments = mapOf(99 to 0),
            ),
        )
    }

    @Test
    fun assignmentChangeInvalidatesNewlyOccupiedPort() {
        assertEquals(
            setOf(0),
            AnalogTriggerRouteTracker.portsInvalidatedByAssignmentChange(
                previousAssignments = emptyMap(),
                currentAssignments = mapOf(42 to 0),
            ),
        )
    }

    @Test
    fun assignmentChangeKeepsStableAssignments() {
        assertEquals(
            emptySet(),
            AnalogTriggerRouteTracker.portsInvalidatedByAssignmentChange(
                previousAssignments = mapOf(42 to 0, 99 to 1),
                currentAssignments = mapOf(42 to 0, 99 to 1),
            ),
        )
    }
}
