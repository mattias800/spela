package com.spela.player.domain.model

import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlin.test.Test
import kotlin.test.assertEquals

class GamepadPositionTest {

    /**
     * Guards the ordinal contract shared with the C bridge
     * (player/native/src/gamepad_sdl3.c). If this fails, the C
     * `sdl_button_to_position` table must be updated in lockstep.
     */
    @Test
    fun ordinalContractIsStable() {
        assertEquals(0, GamepadPosition.SOUTH.ordinal)
        assertEquals(1, GamepadPosition.EAST.ordinal)
        assertEquals(2, GamepadPosition.WEST.ordinal)
        assertEquals(3, GamepadPosition.NORTH.ordinal)
        assertEquals(4, GamepadPosition.DPAD_UP.ordinal)
        assertEquals(5, GamepadPosition.DPAD_DOWN.ordinal)
        assertEquals(6, GamepadPosition.DPAD_LEFT.ordinal)
        assertEquals(7, GamepadPosition.DPAD_RIGHT.ordinal)
        assertEquals(8, GamepadPosition.L1.ordinal)
        assertEquals(9, GamepadPosition.R1.ordinal)
        assertEquals(10, GamepadPosition.L2.ordinal)
        assertEquals(11, GamepadPosition.R2.ordinal)
        assertEquals(12, GamepadPosition.L3.ordinal)
        assertEquals(13, GamepadPosition.R3.ordinal)
        assertEquals(14, GamepadPosition.START.ordinal)
        assertEquals(15, GamepadPosition.SELECT.ordinal)
        assertEquals(16, GamepadPosition.entries.size)
    }

    /** The default map must reproduce the historical fixed SDL3 desktop mapping. */
    @Test
    fun defaultMapReproducesHistoricalSdlBehavior() {
        val m = DefaultGamepadMapping.POSITION_TO_RETRO
        assertEquals(LibretroButtons.B, m[GamepadPosition.SOUTH])
        assertEquals(LibretroButtons.A, m[GamepadPosition.EAST])
        assertEquals(LibretroButtons.Y, m[GamepadPosition.WEST])
        assertEquals(LibretroButtons.X, m[GamepadPosition.NORTH])
        assertEquals(LibretroButtons.UP, m[GamepadPosition.DPAD_UP])
        assertEquals(LibretroButtons.DOWN, m[GamepadPosition.DPAD_DOWN])
        assertEquals(LibretroButtons.LEFT, m[GamepadPosition.DPAD_LEFT])
        assertEquals(LibretroButtons.RIGHT, m[GamepadPosition.DPAD_RIGHT])
        assertEquals(LibretroButtons.L, m[GamepadPosition.L1])
        assertEquals(LibretroButtons.R, m[GamepadPosition.R1])
        assertEquals(LibretroButtons.L2, m[GamepadPosition.L2])
        assertEquals(LibretroButtons.R2, m[GamepadPosition.R2])
        assertEquals(LibretroButtons.L3, m[GamepadPosition.L3])
        assertEquals(LibretroButtons.R3, m[GamepadPosition.R3])
        assertEquals(LibretroButtons.START, m[GamepadPosition.START])
        assertEquals(LibretroButtons.SELECT, m[GamepadPosition.SELECT])
    }

    /** Every position has a default; the mapping covers the full enum. */
    @Test
    fun everyPositionHasADefault() {
        assertEquals(
            GamepadPosition.entries.toSet(),
            DefaultGamepadMapping.POSITION_TO_RETRO.keys,
        )
    }
}
