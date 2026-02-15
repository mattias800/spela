package com.spela.player.presentation.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.spela.player.domain.model.ShaderPreset

/**
 * Maps [ShaderPreset] to the GPU shader ID constants (matching gpu_renderer.h).
 * Used by Vulkan/Metal emulation surfaces to set the active shader.
 */
val ShaderPreset.gpuShaderId: Int
    get() = when (this) {
        ShaderPreset.NONE -> 0
        ShaderPreset.BILINEAR -> 1
        ShaderPreset.SHARP_BILINEAR -> 2
        ShaderPreset.CRT_SIMPLE -> 3
        ShaderPreset.SCANLINES -> 4
        ShaderPreset.LCD_GRID -> 5
    }

/**
 * Returns the [FilterQuality] appropriate for the given shader preset.
 * Nearest-neighbor for pixel-art presets; bilinear for smooth presets.
 */
fun ShaderPreset.filterQuality(): FilterQuality = when (this) {
    ShaderPreset.NONE -> FilterQuality.None
    ShaderPreset.BILINEAR -> FilterQuality.Medium
    ShaderPreset.SHARP_BILINEAR -> FilterQuality.Low
    // CRT/LCD/Scanlines use nearest-neighbor as the base, overlay on top
    ShaderPreset.CRT_SIMPLE, ShaderPreset.LCD_GRID, ShaderPreset.SCANLINES -> FilterQuality.None
}

/**
 * Draws post-processing shader overlay effects on top of the rendered game image.
 * Call after [drawImage] with the matching destination rect.
 *
 * @param shader  the active shader preset
 * @param dstOffset  top-left of the rendered game area on screen
 * @param dstSize    size of the rendered game area on screen
 * @param srcSize    original game resolution (e.g. 256x224)
 */
fun DrawScope.drawShaderOverlay(
    shader: ShaderPreset,
    dstOffset: IntOffset,
    dstSize: IntSize,
    srcSize: IntSize,
) {
    when (shader) {
        ShaderPreset.NONE,
        ShaderPreset.BILINEAR,
        ShaderPreset.SHARP_BILINEAR -> { /* no overlay needed */ }
        ShaderPreset.CRT_SIMPLE -> drawCrtOverlay(dstOffset, dstSize, srcSize)
        ShaderPreset.LCD_GRID -> drawLcdGridOverlay(dstOffset, dstSize, srcSize)
        ShaderPreset.SCANLINES -> drawScanlinesOverlay(dstOffset, dstSize, srcSize)
    }
}

/**
 * Horizontal scanline darkening -- the bottom portion of each pixel row
 * is drawn with a semi-transparent black line.
 */
private fun DrawScope.drawScanlinesOverlay(
    dstOffset: IntOffset,
    dstSize: IntSize,
    srcSize: IntSize,
) {
    val scaleY = dstSize.height.toFloat() / srcSize.height
    val scanlineColor = Color.Black.copy(alpha = 0.25f)
    val strokeWidth = scaleY * 0.35f

    val startX = dstOffset.x.toFloat()
    val endX = (dstOffset.x + dstSize.width).toFloat()

    for (row in 0 until srcSize.height) {
        val y = dstOffset.y + row * scaleY + scaleY * 0.75f
        drawLine(
            color = scanlineColor,
            start = Offset(startX, y),
            end = Offset(endX, y),
            strokeWidth = strokeWidth,
        )
    }
}

/**
 * CRT Classic: stronger scanlines + radial vignette darkening at edges.
 */
private fun DrawScope.drawCrtOverlay(
    dstOffset: IntOffset,
    dstSize: IntSize,
    srcSize: IntSize,
) {
    val scaleY = dstSize.height.toFloat() / srcSize.height
    val scanlineColor = Color.Black.copy(alpha = 0.35f)
    val strokeWidth = scaleY * 0.4f

    val startX = dstOffset.x.toFloat()
    val endX = (dstOffset.x + dstSize.width).toFloat()

    // Scanlines
    for (row in 0 until srcSize.height) {
        val y = dstOffset.y + row * scaleY + scaleY * 0.6f
        drawLine(
            color = scanlineColor,
            start = Offset(startX, y),
            end = Offset(endX, y),
            strokeWidth = strokeWidth,
        )
    }

    // Vignette (dark edges, bright center)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
            center = Offset(
                dstOffset.x + dstSize.width / 2f,
                dstOffset.y + dstSize.height / 2f,
            ),
            radius = maxOf(dstSize.width, dstSize.height) / 1.5f,
        ),
        topLeft = Offset(dstOffset.x.toFloat(), dstOffset.y.toFloat()),
        size = Size(dstSize.width.toFloat(), dstSize.height.toFloat()),
    )
}

/**
 * LCD Grid: thin horizontal + vertical grid lines simulating handheld LCD subpixels.
 */
private fun DrawScope.drawLcdGridOverlay(
    dstOffset: IntOffset,
    dstSize: IntSize,
    srcSize: IntSize,
) {
    val scaleX = dstSize.width.toFloat() / srcSize.width
    val scaleY = dstSize.height.toFloat() / srcSize.height
    val gridColor = Color.Black.copy(alpha = 0.2f)
    val lineWidth = maxOf(1f, minOf(scaleX, scaleY) * 0.15f)

    val left = dstOffset.x.toFloat()
    val top = dstOffset.y.toFloat()
    val right = left + dstSize.width
    val bottom = top + dstSize.height

    // Horizontal grid lines
    for (row in 0..srcSize.height) {
        val y = top + row * scaleY
        drawLine(
            color = gridColor,
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = lineWidth,
        )
    }

    // Vertical grid lines
    for (col in 0..srcSize.width) {
        val x = left + col * scaleX
        drawLine(
            color = gridColor,
            start = Offset(x, top),
            end = Offset(x, bottom),
            strokeWidth = lineWidth,
        )
    }
}
