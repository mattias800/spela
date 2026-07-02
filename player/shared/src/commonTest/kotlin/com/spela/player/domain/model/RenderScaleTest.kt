package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderScaleTest {

    @Test
    fun nativeScaleProducesNoCoreVariables() {
        assertTrue(
            renderScaleCoreVariables("gc", "/cores/dolphin_libretro.so", RenderScale.NATIVE).isEmpty(),
        )
    }

    @Test
    fun dolphinMapsScaleToEfbScale() {
        assertEquals(
            listOf(CoreVariableOverride("dolphin_efb_scale", "x3 (1920 x 1584)")),
            renderScaleCoreVariables("wii", "/cores/dolphin_libretro.so", RenderScale.THREE_X),
        )
    }

    @Test
    fun ppssppMapsScaleToInternalResolution() {
        assertEquals(
            listOf(CoreVariableOverride("ppsspp_internal_resolution", "1920x1088")),
            renderScaleCoreVariables("psp", "/cores/ppsspp_libretro.so", RenderScale.FOUR_X),
        )
    }

    @Test
    fun beetlePsxHwMapsSupportedScaleToInternalResolution() {
        assertEquals(
            listOf(CoreVariableOverride("beetle_psx_hw_internal_resolution", "2x")),
            renderScaleCoreVariables("psx", "/cores/mednafen_psx_hw_libretro.so", RenderScale.TWO_X),
        )
    }

    @Test
    fun beetlePsxHwDoesNotMapUnsupportedThreeXScale() {
        assertTrue(
            renderScaleCoreVariables("psx", "/cores/mednafen_psx_hw_libretro.so", RenderScale.THREE_X).isEmpty(),
        )
    }

    @Test
    fun n64MapsMupenAndParallelResolutionKeys() {
        assertEquals(
            listOf(
                CoreVariableOverride("mupen64plus-43screensize", "960x720"),
                CoreVariableOverride("mupen64plus-169screensize", "960x540"),
            ),
            renderScaleCoreVariables("n64", "/cores/mupen64plus_next_gles3_libretro.so", RenderScale.THREE_X),
        )
        assertEquals(
            listOf(CoreVariableOverride("parallel-n64-screensize", "1280x960")),
            renderScaleCoreVariables("n64", "/cores/parallel_n64_libretro.so", RenderScale.FOUR_X),
        )
    }

    @Test
    fun unsupportedConsoleProducesNoCoreVariables() {
        assertTrue(
            renderScaleCoreVariables("snes", "/cores/snes9x_libretro.so", RenderScale.FOUR_X).isEmpty(),
        )
    }

    @Test
    fun unsupportedConsoleDoesNotSupportRenderScale() {
        assertTrue(supportsRenderScale("gc"))
        assertTrue(!supportsRenderScale("snes"))
    }
}
