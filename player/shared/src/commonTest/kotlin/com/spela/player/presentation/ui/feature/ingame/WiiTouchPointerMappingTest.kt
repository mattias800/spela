package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WiiTouchPointerMappingTest {

    @Test
    fun invalidInputsReturnNull() {
        assertNull(calcWiiRenderInfo(Size(0f, 0f), 4f / 3f))
        assertNull(calcWiiRenderInfo(Size(100f, 100f), 0f))
    }

    @Test
    fun letterboxesWideContainerForFourThree() {
        // 4:3 source into a 400x300 container fits exactly (same aspect) — no bars.
        val info = calcWiiRenderInfo(Size(400f, 300f), 4f / 3f)!!
        assertEquals(400f, info.scaledWidth, 0.01f)
        assertEquals(300f, info.scaledHeight, 0.01f)
        assertEquals(0f, info.offsetX, 0.01f)
        assertEquals(0f, info.offsetY, 0.01f)
    }

    @Test
    fun pillarboxesWiderContainerForFourThree() {
        // 4:3 into a 600x300 container is height-limited → 400 wide, centered.
        val info = calcWiiRenderInfo(Size(600f, 300f), 4f / 3f)!!
        assertEquals(400f, info.scaledWidth, 0.01f)
        assertEquals(300f, info.scaledHeight, 0.01f)
        assertEquals(100f, info.offsetX, 0.01f) // (600-400)/2
        assertEquals(0f, info.offsetY, 0.01f)
    }

    @Test
    fun centerTouchMapsToOrigin() {
        val info = calcWiiRenderInfo(Size(400f, 300f), 4f / 3f)!!
        val (x, y) = wiiPointerCoords(Offset(200f, 150f), info)
        assertEquals(0, x)
        assertEquals(0, y)
    }

    @Test
    fun cornersMapToPointerExtremes() {
        val info = calcWiiRenderInfo(Size(400f, 300f), 4f / 3f)!!
        val (tlx, tly) = wiiPointerCoords(Offset(0f, 0f), info)
        assertEquals(-0x7FFF, tlx)
        assertEquals(-0x7FFF, tly)
        val (brx, bry) = wiiPointerCoords(Offset(400f, 300f), info)
        assertEquals(0x7FFF, brx)
        assertEquals(0x7FFF, bry)
    }

    @Test
    fun touchesOutsideLetterboxAreClamped() {
        // Pillarboxed: touch in the left bar (x < offsetX) clamps to left edge.
        val info = calcWiiRenderInfo(Size(600f, 300f), 4f / 3f)!!
        val (x, _) = wiiPointerCoords(Offset(10f, 150f), info)
        assertEquals(-0x7FFF, x)
        val (x2, _) = wiiPointerCoords(Offset(590f, 150f), info)
        assertEquals(0x7FFF, x2)
    }

    @Test
    fun pointerRangeStaysWithinBounds() {
        val info = calcWiiRenderInfo(Size(1920f, 1080f), 16f / 9f)!!
        val (x, y) = wiiPointerCoords(Offset(1920f, 1080f), info)
        assertTrue(x in -0x7FFF..0x7FFF)
        assertTrue(y in -0x7FFF..0x7FFF)
    }
}
