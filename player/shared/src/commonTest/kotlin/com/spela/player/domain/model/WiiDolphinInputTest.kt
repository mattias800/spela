package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WiiDolphinInputTest {

    // ---- wiiControllerPortDevice (#1534) ----

    @Test
    fun wiiWithDolphinAttachesNunchuk() {
        assertEquals(
            RETRO_DEVICE_WIIMOTE_NC,
            wiiControllerPortDevice("wii", "/cores/dolphin_libretro.so"),
        )
    }

    @Test
    fun nunchukDeviceMatchesDolphinSubclassEncoding() {
        // (3 << 8) | RETRO_DEVICE_JOYPAD — see DolphinLibretro/Input.cpp.
        assertEquals(0x301, RETRO_DEVICE_WIIMOTE_NC)
    }

    @Test
    fun gamecubeGetsNoWiimoteDevice() {
        // "gamecube" is the production console code (see server console registry).
        assertNull(wiiControllerPortDevice("gamecube", "/cores/dolphin_libretro.so"))
    }

    @Test
    fun nonDolphinCoreOnWiiGetsNoWiimoteDevice() {
        assertNull(wiiControllerPortDevice("wii", "/cores/other_libretro.so"))
    }

    // ---- wiiIrPointerCoreVariables (#1524) ----

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
        // "gamecube" is the production console code (see server console registry).
        assertTrue(wiiIrPointerCoreVariables("gamecube", "/cores/dolphin_libretro.so").isEmpty())
    }

    @Test
    fun nonDolphinCoreOnWiiProducesNoOverrides() {
        assertTrue(wiiIrPointerCoreVariables("wii", "/cores/other_libretro.so").isEmpty())
    }
}
