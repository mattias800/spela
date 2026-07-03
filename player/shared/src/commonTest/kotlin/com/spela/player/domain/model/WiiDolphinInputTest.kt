package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WiiDolphinInputTest {

    // ---- wiiControllerPortDevice (#1534, #1559) ----

    @Test
    fun wiiWithDolphinUsesTheSchemeDevice() {
        assertEquals(
            RETRO_DEVICE_WIIMOTE_NC,
            wiiControllerPortDevice("wii", "/cores/dolphin_libretro.so", WiiControlScheme.NUNCHUK),
        )
        assertEquals(
            RETRO_DEVICE_CLASSIC,
            wiiControllerPortDevice(
                "wii",
                "/cores/dolphin_libretro.so",
                WiiControlScheme.CLASSIC_CONTROLLER,
            ),
        )
    }

    @Test
    fun schemeDevicesMatchDolphinSubclassEncodings() {
        // (subclass << 8) | RETRO_DEVICE_JOYPAD — see DolphinLibretro/Input.cpp.
        assertEquals(1, WiiControlScheme.WIIMOTE.portDevice)
        assertEquals(0x201, WiiControlScheme.WIIMOTE_SIDEWAYS.portDevice)
        assertEquals(0x301, WiiControlScheme.NUNCHUK.portDevice)
        assertEquals(0x401, WiiControlScheme.CLASSIC_CONTROLLER.portDevice)
        assertEquals(0x501, WiiControlScheme.CLASSIC_PRO.portDevice)
        assertEquals(0x601, WiiControlScheme.GC_PAD.portDevice)
    }

    @Test
    fun storageIdsRoundTrip() {
        for (scheme in WiiControlScheme.entries) {
            assertEquals(scheme, WiiControlScheme.fromStorageId(scheme.storageId))
        }
        assertNull(WiiControlScheme.fromStorageId(null))
        assertNull(WiiControlScheme.fromStorageId("bogus"))
    }

    @Test
    fun gamecubeGetsNoWiimoteDevice() {
        // "gamecube" is the production console code (see server console registry).
        assertNull(
            wiiControllerPortDevice("gamecube", "/cores/dolphin_libretro.so", WiiControlScheme.NUNCHUK),
        )
    }

    @Test
    fun nonDolphinCoreOnWiiGetsNoWiimoteDevice() {
        assertNull(
            wiiControllerPortDevice("wii", "/cores/other_libretro.so", WiiControlScheme.GC_PAD),
        )
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
