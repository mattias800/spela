package com.spela.player.libretro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmulationScalingTest {

    @Test
    fun `square pixels fit exactly in matching canvas`() {
        val result = computeScaledFrame(
            srcWidth = 320, srcHeight = 240,
            canvasWidth = 640f, canvasHeight = 480f,
        )
        assertEquals(640, result.width)
        assertEquals(480, result.height)
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun `square pixels letterboxed in wide canvas`() {
        val result = computeScaledFrame(
            srcWidth = 320, srcHeight = 240,
            canvasWidth = 1920f, canvasHeight = 480f,
        )
        // Height-constrained: scale = 480/240 = 2, so 640x480
        assertEquals(640, result.width)
        assertEquals(480, result.height)
        // Centered horizontally: (1920 - 640) / 2 = 640
        assertEquals(640, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun `square pixels pillarboxed in tall canvas`() {
        val result = computeScaledFrame(
            srcWidth = 320, srcHeight = 240,
            canvasWidth = 640f, canvasHeight = 960f,
        )
        // Width-constrained: scale = 640/320 = 2, so 640x480
        assertEquals(640, result.width)
        assertEquals(480, result.height)
        assertEquals(0, result.offsetX)
        // Centered vertically: (960 - 480) / 2 = 240
        assertEquals(240, result.offsetY)
    }

    @Test
    fun `DAR corrects non-square pixels - Amiga 320x200 at 4 to 3`() {
        // Amiga outputs 320x200 pixels but expects 4:3 display
        val result = computeScaledFrame(
            srcWidth = 320, srcHeight = 200,
            canvasWidth = 1920f, canvasHeight = 1080f,
            displayAspectRatio = 4f / 3f,
        )
        // Display dimensions: 200 * (4/3) = 266.67 wide, 200 tall
        // Scale: min(1920/266.67, 1080/200) = min(7.2, 5.4) = 5.4
        // Scaled: 266.67 * 5.4 = 1440, 200 * 5.4 = 1080
        assertEquals(1440, result.width)
        assertEquals(1080, result.height)
        // Centered: (1920 - 1440) / 2 = 240
        assertEquals(240, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun `DAR corrects non-square pixels - N64 Angrylion 640x240 at 4 to 3`() {
        val result = computeScaledFrame(
            srcWidth = 640, srcHeight = 240,
            canvasWidth = 1920f, canvasHeight = 1080f,
            displayAspectRatio = 4f / 3f,
        )
        // Display dimensions: 240 * (4/3) = 320 wide, 240 tall
        // Scale: min(1920/320, 1080/240) = min(6.0, 4.5) = 4.5
        // Scaled: 320 * 4.5 = 1440, 240 * 4.5 = 1080
        assertEquals(1440, result.width)
        assertEquals(1080, result.height)
        assertEquals(240, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun `DAR zero falls back to pixel dimensions`() {
        val withDar = computeScaledFrame(
            srcWidth = 320, srcHeight = 200,
            canvasWidth = 640f, canvasHeight = 480f,
            displayAspectRatio = 0f,
        )
        val withoutDar = computeScaledFrame(
            srcWidth = 320, srcHeight = 200,
            canvasWidth = 640f, canvasHeight = 480f,
        )
        assertEquals(withoutDar, withDar)
    }

    @Test
    fun `negative DAR falls back to pixel dimensions`() {
        val result = computeScaledFrame(
            srcWidth = 320, srcHeight = 240,
            canvasWidth = 640f, canvasHeight = 480f,
            displayAspectRatio = -1f,
        )
        assertEquals(640, result.width)
        assertEquals(480, result.height)
    }

    @Test
    fun `widescreen DAR on 16x9 canvas fills correctly`() {
        // A core outputs 640x480 pixels but reports 16:9 DAR
        val result = computeScaledFrame(
            srcWidth = 640, srcHeight = 480,
            canvasWidth = 1920f, canvasHeight = 1080f,
            displayAspectRatio = 16f / 9f,
        )
        // Display dimensions: 480 * (16/9) = 853.33, 480
        // Scale: min(1920/853.33, 1080/480) = min(2.25, 2.25) = 2.25
        // Scaled: 853.33 * 2.25 = 1920, 480 * 2.25 = 1080
        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun `output is always centered`() {
        val result = computeScaledFrame(
            srcWidth = 100, srcHeight = 100,
            canvasWidth = 500f, canvasHeight = 300f,
        )
        // Scale = min(5, 3) = 3, so 300x300 — but canvas is 500 wide
        assertEquals(300, result.width)
        assertEquals(300, result.height)
        assertEquals(100, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun `DAR output never exceeds canvas`() {
        val result = computeScaledFrame(
            srcWidth = 320, srcHeight = 200,
            canvasWidth = 800f, canvasHeight = 600f,
            displayAspectRatio = 4f / 3f,
        )
        assertTrue(result.width <= 800)
        assertTrue(result.height <= 600)
        assertTrue(result.offsetX >= 0)
        assertTrue(result.offsetY >= 0)
    }
}
