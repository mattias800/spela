package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WiiIrPointerTest {

    @Test
    fun wiiWithDolphinCentersIrRestPosition() {
        assertEquals(
            listOf(
                CoreVariableOverride("dolphin_ir_offset", "10"),
                CoreVariableOverride("dolphin_ir_pitch", "20"),
            ),
            wiiIrPointerCoreVariables("wii", "/cores/dolphin_libretro.so"),
        )
    }

    @Test
    fun consoleIdIsNormalized() {
        assertEquals(
            listOf(
                CoreVariableOverride("dolphin_ir_offset", "10"),
                CoreVariableOverride("dolphin_ir_pitch", "20"),
            ),
            wiiIrPointerCoreVariables(" Wii ", "/cores/dolphin_libretro.dylib"),
        )
    }

    @Test
    fun gamecubeHasNoIrPointer() {
        assertTrue(wiiIrPointerCoreVariables("gc", "/cores/dolphin_libretro.so").isEmpty())
    }

    @Test
    fun nonDolphinCoreOnWiiProducesNoOverrides() {
        assertTrue(wiiIrPointerCoreVariables("wii", "/cores/other_libretro.so").isEmpty())
    }
}
