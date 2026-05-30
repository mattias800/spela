package com.spela.player.libretro

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.spela.player.domain.model.ShaderPreset
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode

/**
 * GPU post-processing shaders for the desktop emulation surface.
 *
 * The shared [com.spela.player.presentation.ui.feature.shader.drawShaderOverlay]
 * draws CRT/scanline/LCD effects as Compose geometry (hundreds of drawLine calls
 * + a radial gradient) every frame — its cost scales with output resolution and
 * collapses to single-digit FPS at fullscreen (#1209). On desktop we instead run
 * a single Skia [RuntimeEffect] (SkSL) fragment pass over the output, which is
 * GPU-bound and effectively free even at 4K.
 *
 * Ports of the native GLSL presets in `native/shaders/`. The `image` child shader
 * samples the source frame in source-pixel space; `coord` arrives in output-local
 * space (the rect is drawn translated to the destination origin).
 */
private const val SCANLINES_SKSL = """
uniform shader image;
uniform float2 uResolution;
uniform float2 uTexSize;
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    half4 color = image.eval(uv * uTexSize);
    float p = fract(uv.y * uTexSize.y);
    float mask = smoothstep(0.0, 0.35, p) * smoothstep(1.0, 0.65, p);
    mask = mix(0.7, 1.0, mask);
    return half4(color.rgb * half(mask), color.a);
}
"""

private const val CRT_SKSL = """
uniform shader image;
uniform float2 uResolution;
uniform float2 uTexSize;
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    half4 color = image.eval(uv * uTexSize);
    float p = fract(uv.y * uTexSize.y);
    float sl = smoothstep(0.0, 0.3, p) * smoothstep(1.0, 0.7, p);
    sl = mix(0.6, 1.0, sl);
    float2 c = uv - 0.5;
    float dist = length(c) * 1.414;
    float vignette = clamp(1.0 - dist * dist * 0.5, 0.0, 1.0);
    float m = sl * vignette;
    return half4(color.rgb * half(m), color.a);
}
"""

private const val LCD_SKSL = """
uniform shader image;
uniform float2 uResolution;
uniform float2 uTexSize;
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    half4 color = image.eval(uv * uTexSize);
    float2 pp = fract(uv * uTexSize);
    float ex = 0.1;
    float ey = 0.15;
    float px = smoothstep(0.0, ex, pp.x) * smoothstep(1.0, 1.0 - ex, pp.x);
    float py = smoothstep(0.0, ey, pp.y) * smoothstep(1.0, 1.0 - ey, pp.y);
    float grid = mix(0.5, 1.0, px * py);
    return half4(color.rgb * half(grid), color.a);
}
"""

/** SkSL source for presets rendered via a fragment shader, or null for the
 *  pass-through / filter-only presets (NONE / BILINEAR / SHARP_BILINEAR). */
internal fun ShaderPreset.desktopSksl(): String? = when (this) {
    ShaderPreset.SCANLINES -> SCANLINES_SKSL
    ShaderPreset.CRT_SIMPLE -> CRT_SKSL
    ShaderPreset.LCD_GRID -> LCD_SKSL
    ShaderPreset.NONE, ShaderPreset.BILINEAR, ShaderPreset.SHARP_BILINEAR -> null
}

/** Compiled-effect cache, keyed by SkSL source (compiling is the costly step). */
private val effectCache = HashMap<String, RuntimeEffect>()

/**
 * Draws [bitmap] into the destination rect with [shader]'s fragment effect.
 * Returns true if it handled drawing; false for pass-through presets or on
 * failure (the caller should then fall back to a plain [DrawScope.drawImage]).
 */
internal fun DrawScope.drawWithDesktopShader(
    bitmap: ImageBitmap,
    shader: ShaderPreset,
    dstOffset: IntOffset,
    dstSize: IntSize,
    srcSize: IntSize,
): Boolean {
    val sksl = shader.desktopSksl() ?: return false
    if (dstSize.width <= 0 || dstSize.height <= 0) return false
    return try {
        val effect = effectCache.getOrPut(sksl) { RuntimeEffect.makeForShader(sksl) }
        val image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        // Nearest sampling — these presets layer on top of a pixel-perfect base.
        val imageShader = image.makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            SamplingMode.DEFAULT,
            null,
        )
        val builder = RuntimeShaderBuilder(effect).apply {
            uniform("uResolution", dstSize.width.toFloat(), dstSize.height.toFloat())
            uniform("uTexSize", srcSize.width.toFloat(), srcSize.height.toFloat())
            child("image", imageShader)
        }
        val paint = Paint().apply { this.shader = builder.makeShader(null) }
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save()
            nc.translate(dstOffset.x.toFloat(), dstOffset.y.toFloat())
            nc.drawRect(Rect.makeWH(dstSize.width.toFloat(), dstSize.height.toFloat()), paint)
            nc.restore()
        }
        true
    } catch (e: Throwable) {
        println("[Shader] desktop RuntimeEffect failed (${e.message}); using plain draw")
        false
    }
}
