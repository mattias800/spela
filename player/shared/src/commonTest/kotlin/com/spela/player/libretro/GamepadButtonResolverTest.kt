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
}
