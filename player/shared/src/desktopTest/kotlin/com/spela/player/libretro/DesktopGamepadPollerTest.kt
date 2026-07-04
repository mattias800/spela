package com.spela.player.libretro

import com.spela.player.domain.model.DefaultGamepadMapping
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopGamepadPollerTest {

    @Test
    fun normalizeTriggerPressureClampsToLibretroAnalogButtonRange() {
        assertEquals(0, DesktopGamepadPoller.normalizeTriggerPressure(-32768).toInt())
        assertEquals(0, DesktopGamepadPoller.normalizeTriggerPressure(-1).toInt())
        assertEquals(0, DesktopGamepadPoller.normalizeTriggerPressure(0).toInt())
        assertEquals(12345, DesktopGamepadPoller.normalizeTriggerPressure(12345).toInt())
        assertEquals(
            DesktopGamepadPoller.ANALOG_BUTTON_RANGE,
            DesktopGamepadPoller.normalizeTriggerPressure(32767).toInt(),
        )
        assertEquals(
            DesktopGamepadPoller.ANALOG_BUTTON_RANGE,
            DesktopGamepadPoller.normalizeTriggerPressure(50000).toInt(),
        )
    }

    @Test
    fun resolveTriggerAxesRoutesPressureThroughEffectiveMapping() {
        val mapping = DefaultGamepadMapping.POSITION_TO_RETRO + mapOf(
            GamepadPosition.L2 to LibretroButtons.R2,
            GamepadPosition.R2 to LibretroButtons.L2,
        )

        val out = DesktopGamepadPoller.resolveTriggerAxes(
            l2Raw = 12345,
            r2Raw = 23456,
            mapping = mapping,
        )

        assertEquals(12345.toShort(), out.analogPressures[LibretroButtons.R2])
        assertEquals(23456.toShort(), out.analogPressures[LibretroButtons.L2])
    }

    @Test
    fun resolveTriggerAxesUsesDigitalThreshold() {
        val out = DesktopGamepadPoller.resolveTriggerAxes(
            l2Raw = 8000,
            r2Raw = 8001,
            mapping = DefaultGamepadMapping.POSITION_TO_RETRO,
        )

        assertFalse(out.l2Pressed)
        assertTrue(out.r2Pressed)
    }

    @Test
    fun resolveTriggerAxesUsesStrongestPressureForFanInMapping() {
        val mapping = mapOf(
            GamepadPosition.L2 to LibretroButtons.L2,
            GamepadPosition.R2 to LibretroButtons.L2,
        )

        val out = DesktopGamepadPoller.resolveTriggerAxes(
            l2Raw = 1000,
            r2Raw = 2000,
            mapping = mapping,
        )

        assertEquals(mapOf(LibretroButtons.L2 to 2000.toShort()), out.analogPressures)
    }
}
