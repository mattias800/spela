package com.spela.player.desktop.e2e

import com.spela.player.presentation.viewmodel.LibretroPixelFormat
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression tests for frame conversion performance.
 *
 * These tests verify that the rendering pipeline can convert raw libretro frame
 * data to Skia bitmaps without excessive allocation overhead. The per-frame
 * allocation bug caused garbage collection pauses that dropped below 60fps.
 *
 * The fix introduced reusable [FrameBuffers] that pre-allocate pixel buffers
 * and Skia bitmaps, only reallocating when frame dimensions change. These tests
 * exercise the same conversion logic to ensure p95 conversion time stays under
 * 5ms per frame for NES-sized (256x240) output.
 */
class FramerateRegressionTest {

    /**
     * Mirrors the production FrameBuffers class to test the buffer-reuse pattern.
     * Only reallocates when dimensions or format change.
     */
    private class TestFrameBuffers {
        var bitmap = Bitmap()
            private set
        var imageInfo = ImageInfo(0, 0, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            private set
        var pixelBuffer = ByteArray(0)
            private set

        private var lastWidth = 0
        private var lastHeight = 0
        private var lastFormat = -1

        fun ensureCapacity(width: Int, height: Int, pixelFormat: Int) {
            if (width == lastWidth && height == lastHeight && pixelFormat == lastFormat) return
            lastWidth = width
            lastHeight = height
            lastFormat = pixelFormat
            imageInfo = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            bitmap = Bitmap().apply { allocPixels(imageInfo) }
            pixelBuffer = ByteArray(width * height * 4)
        }
    }

    /** XRGB8888 -> BGRA8888 in-place (same logic as production) */
    private fun convertXRGB8888InPlace(data: ByteArray, out: ByteArray) {
        var i = 0
        while (i < data.size - 3) {
            out[i] = data[i]
            out[i + 1] = data[i + 1]
            out[i + 2] = data[i + 2]
            out[i + 3] = 0xFF.toByte()
            i += 4
        }
    }

    /** RGB565 -> BGRA8888 in-place (same logic as production) */
    private fun convertRGB565InPlace(data: ByteArray, totalPixels: Int, out: ByteArray) {
        var srcIdx = 0
        var dstIdx = 0
        for (p in 0 until totalPixels) {
            if (srcIdx + 1 >= data.size) break
            val lo = data[srcIdx].toInt() and 0xFF
            val hi = data[srcIdx + 1].toInt() and 0xFF
            val pixel = lo or (hi shl 8)
            val r = ((pixel shr 11) and 0x1F) * 255 / 31
            val g = ((pixel shr 5) and 0x3F) * 255 / 63
            val b = (pixel and 0x1F) * 255 / 31
            out[dstIdx] = b.toByte()
            out[dstIdx + 1] = g.toByte()
            out[dstIdx + 2] = r.toByte()
            out[dstIdx + 3] = 0xFF.toByte()
            srcIdx += 2
            dstIdx += 4
        }
    }

    /** 0RGB1555 -> BGRA8888 in-place (same logic as production) */
    private fun convertRGB1555InPlace(data: ByteArray, totalPixels: Int, out: ByteArray) {
        var srcIdx = 0
        var dstIdx = 0
        for (p in 0 until totalPixels) {
            if (srcIdx + 1 >= data.size) break
            val lo = data[srcIdx].toInt() and 0xFF
            val hi = data[srcIdx + 1].toInt() and 0xFF
            val pixel = lo or (hi shl 8)
            val r = ((pixel shr 10) and 0x1F) * 255 / 31
            val g = ((pixel shr 5) and 0x1F) * 255 / 31
            val b = (pixel and 0x1F) * 255 / 31
            out[dstIdx] = b.toByte()
            out[dstIdx + 1] = g.toByte()
            out[dstIdx + 2] = r.toByte()
            out[dstIdx + 3] = 0xFF.toByte()
            srcIdx += 2
            dstIdx += 4
        }
    }

    private fun convertFrameInPlace(
        data: ByteArray,
        width: Int,
        height: Int,
        pixelFormat: Int,
        out: ByteArray,
    ) {
        when (pixelFormat) {
            LibretroPixelFormat.XRGB8888 -> convertXRGB8888InPlace(data, out)
            LibretroPixelFormat.RGB565 -> convertRGB565InPlace(data, width * height, out)
            else -> convertRGB1555InPlace(data, width * height, out)
        }
    }

    // NES resolution
    private val nesWidth = 256
    private val nesHeight = 240

    // SNES resolution
    private val snesWidth = 512
    private val snesHeight = 448

    /**
     * Generate consistent fake frame data for a given format and resolution.
     */
    private fun generateFrameData(width: Int, height: Int, pixelFormat: Int): ByteArray {
        val bytesPerPixel = if (pixelFormat == LibretroPixelFormat.XRGB8888) 4 else 2
        val size = width * height * bytesPerPixel
        return ByteArray(size) { (it % 256).toByte() }
    }

    @Test
    fun nesFrameConversionP95UnderThreshold_XRGB8888() {
        val frameData = generateFrameData(nesWidth, nesHeight, LibretroPixelFormat.XRGB8888)
        val buffers = TestFrameBuffers()
        buffers.ensureCapacity(nesWidth, nesHeight, LibretroPixelFormat.XRGB8888)

        val timings = measureConversionTimings(frameData, nesWidth, nesHeight, LibretroPixelFormat.XRGB8888, buffers)
        val p95 = percentile(timings, 0.95)

        assertTrue(
            p95 < 5.0,
            "XRGB8888 NES frame conversion p95 should be < 5ms, was ${p95}ms"
        )
    }

    @Test
    fun nesFrameConversionP95UnderThreshold_RGB565() {
        val frameData = generateFrameData(nesWidth, nesHeight, LibretroPixelFormat.RGB565)
        val buffers = TestFrameBuffers()
        buffers.ensureCapacity(nesWidth, nesHeight, LibretroPixelFormat.RGB565)

        val timings = measureConversionTimings(frameData, nesWidth, nesHeight, LibretroPixelFormat.RGB565, buffers)
        val p95 = percentile(timings, 0.95)

        assertTrue(
            p95 < 5.0,
            "RGB565 NES frame conversion p95 should be < 5ms, was ${p95}ms"
        )
    }

    @Test
    fun nesFrameConversionP95UnderThreshold_RGB1555() {
        val frameData = generateFrameData(nesWidth, nesHeight, LibretroPixelFormat.RGB1555)
        val buffers = TestFrameBuffers()
        buffers.ensureCapacity(nesWidth, nesHeight, LibretroPixelFormat.RGB1555)

        val timings = measureConversionTimings(frameData, nesWidth, nesHeight, LibretroPixelFormat.RGB1555, buffers)
        val p95 = percentile(timings, 0.95)

        assertTrue(
            p95 < 5.0,
            "RGB1555 NES frame conversion p95 should be < 5ms, was ${p95}ms"
        )
    }

    @Test
    fun snesFrameConversionP95UnderThreshold_XRGB8888() {
        val frameData = generateFrameData(snesWidth, snesHeight, LibretroPixelFormat.XRGB8888)
        val buffers = TestFrameBuffers()
        buffers.ensureCapacity(snesWidth, snesHeight, LibretroPixelFormat.XRGB8888)

        val timings = measureConversionTimings(frameData, snesWidth, snesHeight, LibretroPixelFormat.XRGB8888, buffers)
        val p95 = percentile(timings, 0.95)

        assertTrue(
            p95 < 5.0,
            "XRGB8888 SNES frame conversion p95 should be < 5ms, was ${p95}ms"
        )
    }

    @Test
    fun bufferReuseDoesNotReallocateForSameDimensions() {
        val buffers = TestFrameBuffers()

        buffers.ensureCapacity(nesWidth, nesHeight, LibretroPixelFormat.XRGB8888)
        val firstBuffer = buffers.pixelBuffer
        val firstBitmap = buffers.bitmap

        // Call again with same dimensions -- should reuse same buffer
        buffers.ensureCapacity(nesWidth, nesHeight, LibretroPixelFormat.XRGB8888)
        val secondBuffer = buffers.pixelBuffer
        val secondBitmap = buffers.bitmap

        assertTrue(
            firstBuffer === secondBuffer,
            "Pixel buffer should be reused for same dimensions"
        )
        assertTrue(
            firstBitmap === secondBitmap,
            "Bitmap should be reused for same dimensions"
        )
    }

    @Test
    fun bufferReallocatesWhenDimensionsChange() {
        val buffers = TestFrameBuffers()

        buffers.ensureCapacity(nesWidth, nesHeight, LibretroPixelFormat.XRGB8888)
        val nesBuffer = buffers.pixelBuffer

        buffers.ensureCapacity(snesWidth, snesHeight, LibretroPixelFormat.XRGB8888)
        val snesBuffer = buffers.pixelBuffer

        assertTrue(
            nesBuffer !== snesBuffer,
            "Pixel buffer should be reallocated when dimensions change"
        )
        assertTrue(
            snesBuffer.size == snesWidth * snesHeight * 4,
            "Pixel buffer should be sized for new dimensions"
        )
    }

    @Test
    fun bufferReallocatesWhenPixelFormatChanges() {
        val buffers = TestFrameBuffers()

        buffers.ensureCapacity(nesWidth, nesHeight, LibretroPixelFormat.XRGB8888)
        val firstBitmap = buffers.bitmap

        buffers.ensureCapacity(nesWidth, nesHeight, LibretroPixelFormat.RGB565)
        val secondBitmap = buffers.bitmap

        assertTrue(
            firstBitmap !== secondBitmap,
            "Bitmap should be reallocated when pixel format changes"
        )
    }

    @Test
    fun hundredFrameConversionWithBitmapInstallP95UnderThreshold() {
        val frameData = generateFrameData(nesWidth, nesHeight, LibretroPixelFormat.XRGB8888)
        val buffers = TestFrameBuffers()
        buffers.ensureCapacity(nesWidth, nesHeight, LibretroPixelFormat.XRGB8888)

        // Warm up
        repeat(10) {
            convertFrameInPlace(frameData, nesWidth, nesHeight, LibretroPixelFormat.XRGB8888, buffers.pixelBuffer)
            buffers.bitmap.installPixels(buffers.imageInfo, buffers.pixelBuffer, nesWidth * 4)
        }

        val timings = mutableListOf<Double>()
        repeat(100) {
            val start = System.nanoTime()
            convertFrameInPlace(frameData, nesWidth, nesHeight, LibretroPixelFormat.XRGB8888, buffers.pixelBuffer)
            buffers.bitmap.installPixels(buffers.imageInfo, buffers.pixelBuffer, nesWidth * 4)
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            timings.add(elapsed)
        }

        val p95 = percentile(timings, 0.95)

        assertTrue(
            p95 < 5.0,
            "Full frame pipeline (convert + install) p95 should be < 5ms, was ${p95}ms"
        )
    }

    /**
     * Measures 100 frame conversions (after 10 warm-up iterations) and returns
     * the list of elapsed times in milliseconds.
     */
    private fun measureConversionTimings(
        frameData: ByteArray,
        width: Int,
        height: Int,
        pixelFormat: Int,
        buffers: TestFrameBuffers,
    ): List<Double> {
        // Warm up
        repeat(10) {
            convertFrameInPlace(frameData, width, height, pixelFormat, buffers.pixelBuffer)
        }

        val timings = mutableListOf<Double>()
        repeat(100) {
            val start = System.nanoTime()
            convertFrameInPlace(frameData, width, height, pixelFormat, buffers.pixelBuffer)
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            timings.add(elapsed)
        }
        return timings
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        val s = sorted.sorted()
        val idx = (s.size * p).toInt().coerceAtMost(s.size - 1)
        return s[idx]
    }
}
