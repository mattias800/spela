package com.spela.player.desktop.e2e

import com.spela.player.libretro.DesktopScreenshotCapture
import com.spela.player.presentation.viewmodel.LibretroPixelFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for desktop screenshot capture.
 *
 * Tests the pixel conversion and PNG encoding logic directly via companion methods,
 * since the full DesktopScreenshotCapture depends on DesktopLibretroController (JNI).
 */
class DesktopScreenshotCaptureTest {

    @Test
    fun encodeBgraToPngProducesValidPng() {
        // Create a 2x2 BGRA image (red, green, blue, white)
        val width = 2
        val height = 2
        val bgra = ByteArray(width * height * 4)

        // Pixel 0: Red (BGRA = 0,0,255,255)
        bgra[0] = 0; bgra[1] = 0; bgra[2] = 0xFF.toByte(); bgra[3] = 0xFF.toByte()
        // Pixel 1: Green (BGRA = 0,255,0,255)
        bgra[4] = 0; bgra[5] = 0xFF.toByte(); bgra[6] = 0; bgra[7] = 0xFF.toByte()
        // Pixel 2: Blue (BGRA = 255,0,0,255)
        bgra[8] = 0xFF.toByte(); bgra[9] = 0; bgra[10] = 0; bgra[11] = 0xFF.toByte()
        // Pixel 3: White (BGRA = 255,255,255,255)
        bgra[12] = 0xFF.toByte(); bgra[13] = 0xFF.toByte(); bgra[14] = 0xFF.toByte(); bgra[15] = 0xFF.toByte()

        val png = DesktopScreenshotCapture.encodeBgraToPng(bgra, width, height)

        assertNotNull(png, "PNG output should not be null")
        assertTrue(png.size > 8, "PNG should have content beyond the header")
        // Verify PNG magic bytes
        assertEquals(0x89.toByte(), png[0], "PNG magic byte 0")
        assertEquals(0x50.toByte(), png[1], "PNG magic byte 1 (P)")
        assertEquals(0x4E.toByte(), png[2], "PNG magic byte 2 (N)")
        assertEquals(0x47.toByte(), png[3], "PNG magic byte 3 (G)")
    }

    @Test
    fun encodeBgraToPngHandlesLargerImage() {
        val width = 256
        val height = 224
        // Typical NES resolution - fill with a gradient pattern
        val bgra = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = (y * width + x) * 4
                bgra[i] = (x % 256).toByte()     // B
                bgra[i + 1] = (y % 256).toByte() // G
                bgra[i + 2] = ((x + y) % 256).toByte() // R
                bgra[i + 3] = 0xFF.toByte()      // A
            }
        }

        val png = DesktopScreenshotCapture.encodeBgraToPng(bgra, width, height)

        assertNotNull(png, "PNG output should not be null for NES-sized image")
        assertTrue(png.size > 100, "PNG of NES-resolution image should be non-trivial in size")
        // Verify PNG magic bytes
        assertEquals(0x89.toByte(), png[0])
    }

    @Test
    fun convertXRGB8888ToBgraProducesCorrectOutput() {
        val width = 2
        val height = 1
        // XRGB8888: on little-endian, bytes are [B, G, R, X]
        val xrgb = byteArrayOf(
            0x10, 0x20, 0x30, 0x00, // Pixel 0: B=0x10, G=0x20, R=0x30
            0x40, 0x50, 0x60, 0x00, // Pixel 1: B=0x40, G=0x50, R=0x60
        )

        val bgra = DesktopScreenshotCapture.convertToBgra(xrgb, width, height, LibretroPixelFormat.XRGB8888)

        assertEquals(8, bgra.size, "Output should be 2 pixels * 4 bytes")
        // Pixel 0: B=0x10, G=0x20, R=0x30, A=0xFF
        assertEquals(0x10.toByte(), bgra[0], "Pixel 0 B")
        assertEquals(0x20.toByte(), bgra[1], "Pixel 0 G")
        assertEquals(0x30.toByte(), bgra[2], "Pixel 0 R")
        assertEquals(0xFF.toByte(), bgra[3], "Pixel 0 A should be 0xFF")
        // Pixel 1: B=0x40, G=0x50, R=0x60, A=0xFF
        assertEquals(0x40.toByte(), bgra[4], "Pixel 1 B")
        assertEquals(0x50.toByte(), bgra[5], "Pixel 1 G")
        assertEquals(0x60.toByte(), bgra[6], "Pixel 1 R")
        assertEquals(0xFF.toByte(), bgra[7], "Pixel 1 A should be 0xFF")
    }

    @Test
    fun convertRGB565ToBgraProducesCorrectOutput() {
        val width = 1
        val height = 1
        // RGB565: Pure red = R=31, G=0, B=0 → 0xF800
        // Little-endian bytes: low=0x00, high=0xF8
        val rgb565 = byteArrayOf(0x00, 0xF8.toByte())

        val bgra = DesktopScreenshotCapture.convertToBgra(rgb565, width, height, LibretroPixelFormat.RGB565)

        assertEquals(4, bgra.size)
        assertEquals(0x00.toByte(), bgra[0], "B should be 0 for pure red")
        assertEquals(0x00.toByte(), bgra[1], "G should be 0 for pure red")
        assertEquals(0xFF.toByte(), bgra[2], "R should be 255 for pure red")
        assertEquals(0xFF.toByte(), bgra[3], "A should be 0xFF")
    }

    @Test
    fun convertRGB565PureGreenProducesCorrectOutput() {
        val width = 1
        val height = 1
        // RGB565: Pure green = R=0, G=63, B=0 → 0x07E0
        // Little-endian bytes: low=0xE0, high=0x07
        val rgb565 = byteArrayOf(0xE0.toByte(), 0x07)

        val bgra = DesktopScreenshotCapture.convertToBgra(rgb565, width, height, LibretroPixelFormat.RGB565)

        assertEquals(4, bgra.size)
        assertEquals(0x00.toByte(), bgra[0], "B should be 0 for pure green")
        assertEquals(0xFF.toByte(), bgra[1], "G should be 255 for pure green")
        assertEquals(0x00.toByte(), bgra[2], "R should be 0 for pure green")
        assertEquals(0xFF.toByte(), bgra[3], "A should be 0xFF")
    }

    @Test
    fun convertRGB1555ToBgraProducesCorrectOutput() {
        val width = 1
        val height = 1
        // 0RGB1555: Pure blue = R=0, G=0, B=31 → 0x001F
        // Little-endian bytes: low=0x1F, high=0x00
        val rgb1555 = byteArrayOf(0x1F, 0x00)

        val bgra = DesktopScreenshotCapture.convertToBgra(rgb1555, width, height, LibretroPixelFormat.RGB1555)

        assertEquals(4, bgra.size)
        assertEquals(0xFF.toByte(), bgra[0], "B should be 255 for pure blue")
        assertEquals(0x00.toByte(), bgra[1], "G should be 0 for pure blue")
        assertEquals(0x00.toByte(), bgra[2], "R should be 0 for pure blue")
        assertEquals(0xFF.toByte(), bgra[3], "A should be 0xFF")
    }

    @Test
    fun convertAndEncodePipelineProducesValidPng() {
        // Test the full pipeline: convert raw pixels → BGRA → PNG
        val width = 4
        val height = 4
        // Create XRGB8888 frame data (simple checkerboard)
        val xrgb = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = (y * width + x) * 4
                val isWhite = (x + y) % 2 == 0
                val c = if (isWhite) 0xFF.toByte() else 0x00.toByte()
                xrgb[i] = c       // B
                xrgb[i + 1] = c   // G
                xrgb[i + 2] = c   // R
                xrgb[i + 3] = 0   // X (ignored)
            }
        }

        val bgra = DesktopScreenshotCapture.convertToBgra(xrgb, width, height, LibretroPixelFormat.XRGB8888)
        val png = DesktopScreenshotCapture.encodeBgraToPng(bgra, width, height)

        assertNotNull(png, "Full pipeline should produce valid PNG")
        assertEquals(0x89.toByte(), png[0], "PNG magic byte")
    }

    @Test
    fun encodeBgraToPngWithZeroDimensionsReturnsNull() {
        val png = DesktopScreenshotCapture.encodeBgraToPng(ByteArray(0), 0, 0)
        assertNull(png, "Zero-dimension image should return null")
    }
}
