package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SdlTypeClassifierTest {
    @Test
    fun mapsSdlGamepadTypes() {
        // SDL_GamepadType values (verified against SDL3 3.2.30 SDL_gamepad.h)
        assertEquals(ControllerStyle.Xbox, controllerStyleFromSdlType(2))          // XBOX360
        assertEquals(ControllerStyle.Xbox, controllerStyleFromSdlType(3))          // XBOXONE
        assertEquals(ControllerStyle.PlayStation, controllerStyleFromSdlType(4))   // PS3
        assertEquals(ControllerStyle.PlayStation, controllerStyleFromSdlType(5))   // PS4
        assertEquals(ControllerStyle.PlayStation, controllerStyleFromSdlType(6))   // PS5
        assertEquals(ControllerStyle.Nintendo, controllerStyleFromSdlType(7))      // SWITCH_PRO
        assertEquals(ControllerStyle.Nintendo, controllerStyleFromSdlType(10))     // JOYCON_PAIR
        assertEquals(ControllerStyle.Generic, controllerStyleFromSdlType(0))       // UNKNOWN
        assertEquals(ControllerStyle.Generic, controllerStyleFromSdlType(1))       // STANDARD
    }
}
