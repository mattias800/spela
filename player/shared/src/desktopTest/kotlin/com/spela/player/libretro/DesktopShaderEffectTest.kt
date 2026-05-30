package com.spela.player.libretro

import com.spela.player.domain.model.ShaderPreset
import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopShaderEffectTest {

    @Test
    fun effectPresetsHaveCompilableSksl() {
        listOf(ShaderPreset.SCANLINES, ShaderPreset.CRT_SIMPLE, ShaderPreset.LCD_GRID).forEach { preset ->
            val sksl = preset.desktopSksl()
            assertNotNull(sksl, "$preset should provide SkSL")
            // Throws on invalid SkSL — guards against shader syntax regressions.
            RuntimeEffect.makeForShader(sksl)
        }
    }

    @Test
    fun passThroughPresetsHaveNoSksl() {
        listOf(ShaderPreset.NONE, ShaderPreset.BILINEAR, ShaderPreset.SHARP_BILINEAR).forEach { preset ->
            assertNull(preset.desktopSksl(), "$preset should not use a fragment shader")
        }
    }
}
