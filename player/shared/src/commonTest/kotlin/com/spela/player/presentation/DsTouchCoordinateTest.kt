package com.spela.player.presentation

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the DS touch-to-pointer coordinate mapping logic.
 * This validates the pure math that maps canvas touch coordinates to
 * the libretro pointer range (-0x7FFF to 0x7FFF).
 */
class DsTouchCoordinateTest {

    /**
     * Replicates the mapping logic from PlatformDsTouchScreen.android.kt
     * for unit testing the coordinate math.
     */
    private fun mapTouchToPointer(
        touchX: Float,
        touchY: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        srcWidth: Int,
        srcHeight: Int,
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

        val pointerX = ((normalizedX * 2f - 1f) * 0x7FFF).toInt().coerceIn(-0x7FFF, 0x7FFF)
        val pointerY = ((normalizedY * 2f - 1f) * 0x7FFF).toInt().coerceIn(-0x7FFF, 0x7FFF)

        return pointerX to pointerY
    }

    @Test
    fun centerTouchMapsToOrigin() {
        // DS bottom screen is 256x192, canvas is 512x384 (exact 2x scale)
        val (x, y) = mapTouchToPointer(256f, 192f, 512f, 384f, 256, 192)
        assertEquals(0, x)
        assertEquals(0, y)
    }

    @Test
    fun topLeftTouchMapsToNegativeMax() {
        val (x, y) = mapTouchToPointer(0f, 0f, 512f, 384f, 256, 192)
        assertEquals(-0x7FFF, x)
        assertEquals(-0x7FFF, y)
    }

    @Test
    fun bottomRightTouchMapsToPositiveMax() {
        val (x, y) = mapTouchToPointer(512f, 384f, 512f, 384f, 256, 192)
        assertEquals(0x7FFF, x)
        assertEquals(0x7FFF, y)
    }

    @Test
    fun touchOutsideImageClampsToEdge() {
        // Touch far to the left of the image area
        val (x, _) = mapTouchToPointer(-100f, 192f, 512f, 384f, 256, 192)
        assertEquals(-0x7FFF, x)

        // Touch far to the right
        val (x2, _) = mapTouchToPointer(700f, 192f, 512f, 384f, 256, 192)
        assertEquals(0x7FFF, x2)
    }

    @Test
    fun letterboxedCanvasOffsetsCorrectly() {
        // Canvas is wider than needed (letterboxed horizontally)
        // 256x192 image in a 1024x384 canvas -> scale = 2.0, image at center
        // Image occupies x: 256..768, y: 0..384
        val (x, y) = mapTouchToPointer(512f, 192f, 1024f, 384f, 256, 192)
        assertEquals(0, x)
        assertEquals(0, y)

        // Touch at left edge of rendered area
        val (x2, _) = mapTouchToPointer(256f, 192f, 1024f, 384f, 256, 192)
        assertEquals(-0x7FFF, x2)
    }

    @Test
    fun zeroSizeReturnsOrigin() {
        val (x, y) = mapTouchToPointer(100f, 100f, 0f, 0f, 256, 192)
        assertEquals(0, x)
        assertEquals(0, y)
    }
}
