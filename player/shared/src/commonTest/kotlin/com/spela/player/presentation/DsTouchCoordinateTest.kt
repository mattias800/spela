package com.spela.player.presentation

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the DS touch-to-pointer coordinate mapping logic.
 * This validates the pure math that maps canvas touch coordinates to
 * the libretro pointer range (-0x7FFF to 0x7FFF).
 *
 * For dual-screen consoles (NDS/3DS), the core expects pointer coordinates
 * relative to the full viewport (both screens stacked). The bottom screen
 * occupies only the lower portion of the viewport, so Y is mapped to the
 * bottom half (0 to 0x7FFF), not the full range (-0x7FFF to 0x7FFF).
 * X still maps to the full range.
 */
class DsTouchCoordinateTest {

    /**
     * Replicates the mapping logic from PlatformDsTouchScreen.android.kt
     * for unit testing the coordinate math.
     *
     * @param fullHeight The full framebuffer height (both screens stacked).
     */
    private fun mapTouchToPointer(
        touchX: Float,
        touchY: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        srcWidth: Int,
        srcHeight: Int,
        fullHeight: Int = srcHeight,
    ): Pair<Int, Int> {
        if (canvasWidth <= 0 || canvasHeight <= 0 || srcWidth <= 0 || srcHeight <= 0) {
            return 0 to 0
        }

        val scale = min(canvasWidth / srcWidth.toFloat(), canvasHeight / srcHeight.toFloat())
        val scaledWidth = srcWidth * scale
        val scaledHeight = srcHeight * scale
        val offsetX = (canvasWidth - scaledWidth) / 2f
        val offsetY = (canvasHeight - scaledHeight) / 2f

        val normalizedX = ((touchX - offsetX) / scaledWidth).coerceIn(0f, 1f)
        val normalizedY = ((touchY - offsetY) / scaledHeight).coerceIn(0f, 1f)

        // X: full range
        val pointerX = ((normalizedX * 2f - 1f) * 0x7FFF).toInt().coerceIn(-0x7FFF, 0x7FFF)

        // Y: bottom portion of full viewport
        val splitRatio = if (fullHeight > 0) (fullHeight - srcHeight).toFloat() / fullHeight else 0f
        val viewportY = splitRatio + normalizedY * (1f - splitRatio)
        val pointerY = ((viewportY * 2f - 1f) * 0x7FFF).toInt().coerceIn(-0x7FFF, 0x7FFF)

        return pointerX to pointerY
    }

    // -- X axis tests (unchanged, full range) --

    @Test
    fun centerXMapsToZero() {
        // DS bottom screen: 256x192, canvas 512x384, full height 384
        val (x, _) = mapTouchToPointer(256f, 192f, 512f, 384f, 256, 192, fullHeight = 384)
        assertEquals(0, x)
    }

    @Test
    fun leftEdgeXMapsToNegativeMax() {
        val (x, _) = mapTouchToPointer(0f, 192f, 512f, 384f, 256, 192, fullHeight = 384)
        assertEquals(-0x7FFF, x)
    }

    @Test
    fun rightEdgeXMapsToPositiveMax() {
        val (x, _) = mapTouchToPointer(512f, 192f, 512f, 384f, 256, 192, fullHeight = 384)
        assertEquals(0x7FFF, x)
    }

    @Test
    fun touchOutsideImageClampsXToEdge() {
        val (x1, _) = mapTouchToPointer(-100f, 192f, 512f, 384f, 256, 192, fullHeight = 384)
        assertEquals(-0x7FFF, x1)

        val (x2, _) = mapTouchToPointer(700f, 192f, 512f, 384f, 256, 192, fullHeight = 384)
        assertEquals(0x7FFF, x2)
    }

    // -- Y axis tests (bottom-half mapping for dual-screen) --

    @Test
    fun topOfBottomScreenMapsToYZero() {
        // NDS: 256x384 full, bottom screen is 256x192 (splitY=192)
        // Touch at top of bottom screen -> y should be 0 (middle of full viewport)
        val (_, y) = mapTouchToPointer(256f, 0f, 512f, 384f, 256, 192, fullHeight = 384)
        assertEquals(0, y, "Top of bottom screen should map to y=0 (viewport midpoint)")
    }

    @Test
    fun bottomOfBottomScreenMapsToYPositiveMax() {
        // Touch at bottom of bottom screen -> y should be 0x7FFF
        val (_, y) = mapTouchToPointer(256f, 384f, 512f, 384f, 256, 192, fullHeight = 384)
        assertEquals(0x7FFF, y, "Bottom of bottom screen should map to y=0x7FFF")
    }

    @Test
    fun centerOfBottomScreenMapsToYMidway() {
        // Touch at center of bottom screen -> y should be approximately 0x3FFF
        val (_, y) = mapTouchToPointer(256f, 192f, 512f, 384f, 256, 192, fullHeight = 384)
        // splitRatio = 192/384 = 0.5, normalizedY = 0.5
        // viewportY = 0.5 + 0.5 * 0.5 = 0.75
        // pointerY = (0.75 * 2 - 1) * 0x7FFF = 0.5 * 0x7FFF = 0x3FFF
        assertEquals(0x3FFF, y, "Center of bottom screen should map to y≈0x3FFF")
    }

    @Test
    fun threeDsBottomScreenTopMapsToYZero() {
        // 3DS: 400x480 full (240 top + 240 bottom), bottom screen is 320x240
        // Touch at top of bottom screen -> y should be 0
        val (_, y) = mapTouchToPointer(320f, 0f, 640f, 480f, 320, 240, fullHeight = 480)
        assertEquals(0, y, "Top of 3DS bottom screen should map to y=0")
    }

    @Test
    fun threeDsBottomScreenBottomMapsToYPositiveMax() {
        val (_, y) = mapTouchToPointer(320f, 480f, 640f, 480f, 320, 240, fullHeight = 480)
        assertEquals(0x7FFF, y, "Bottom of 3DS bottom screen should map to y=0x7FFF")
    }

    @Test
    fun singleScreenFallsBackToFullRange() {
        // When fullHeight equals srcHeight (no split), Y should use full range
        val (_, y1) = mapTouchToPointer(256f, 0f, 512f, 384f, 256, 192, fullHeight = 192)
        assertEquals(-0x7FFF, y1, "Single screen top should map to y=-0x7FFF")

        val (_, y2) = mapTouchToPointer(256f, 384f, 512f, 384f, 256, 192, fullHeight = 192)
        assertEquals(0x7FFF, y2, "Single screen bottom should map to y=0x7FFF")
    }

    // -- Canvas layout tests --

    @Test
    fun letterboxedCanvasOffsetsCorrectly() {
        // 256x192 image in a 1024x384 canvas -> scale = 2.0, image at center
        // Image occupies x: 256..768, y: 0..384
        val (x, _) = mapTouchToPointer(512f, 192f, 1024f, 384f, 256, 192, fullHeight = 384)
        assertEquals(0, x)

        // Touch at left edge of rendered area
        val (x2, _) = mapTouchToPointer(256f, 192f, 1024f, 384f, 256, 192, fullHeight = 384)
        assertEquals(-0x7FFF, x2)
    }

    @Test
    fun zeroSizeReturnsOrigin() {
        val (x, y) = mapTouchToPointer(100f, 100f, 0f, 0f, 256, 192)
        assertEquals(0, x)
        assertEquals(0, y)
    }
}
