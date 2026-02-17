package com.spela.player.libretro

import com.spela.player.domain.controller.ScreenshotCapture
import com.spela.player.presentation.viewmodel.LibretroPixelFormat
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Desktop screenshot capture using Skia for PNG encoding.
 *
 * Supports two rendering paths:
 * - GPU/Metal offscreen: reads BGRA pixels from [DesktopLibretroController.latestRenderedFrame]
 * - Software fallback: reads raw libretro frame via [DesktopLibretroController.getVideoFrame]
 *   and converts to BGRA
 */
class DesktopScreenshotCapture(
    private val controller: DesktopLibretroController,
) : ScreenshotCapture {

    override fun captureScreenshot(): ByteArray? {
        // Prefer GPU-rendered frame (already BGRA with shaders applied)
        val gpuFrame = controller.latestRenderedFrame
        if (gpuFrame != null && gpuFrame.width > 0 && gpuFrame.height > 0) {
            return encodeBgraToPng(gpuFrame.data, gpuFrame.width, gpuFrame.height)
        }

        // Fallback to software frame
        val frameData = controller.getVideoFrame() ?: return null
        val width = controller.getVideoWidth()
        val height = controller.getVideoHeight()
        if (width <= 0 || height <= 0) return null

        val pixelFormat = controller.getPixelFormat()
        val bgra = convertToBgra(frameData, width, height, pixelFormat)
        return encodeBgraToPng(bgra, width, height)
    }

    companion object {
        /**
         * Encode BGRA pixel data to PNG using Skia.
         */
        fun encodeBgraToPng(bgra: ByteArray, width: Int, height: Int): ByteArray? {
            if (width <= 0 || height <= 0) return null
            val imageInfo = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            val bitmap = Bitmap()
            bitmap.allocPixels(imageInfo)
            bitmap.installPixels(imageInfo, bgra, width * 4)

            val image = Image.makeFromBitmap(bitmap)
            val data = image.encodeToData(EncodedImageFormat.PNG) ?: return null
            return data.bytes
        }

        /**
         * Convert raw libretro frame data to BGRA8888.
         */
        fun convertToBgra(
            data: ByteArray,
            width: Int,
            height: Int,
            pixelFormat: Int,
        ): ByteArray {
            val totalPixels = width * height
            val out = ByteArray(totalPixels * 4)
            when (pixelFormat) {
                LibretroPixelFormat.XRGB8888 -> convertXRGB8888toBGRA(data, out)
                LibretroPixelFormat.RGB565 -> convertRGB565toBGRA(data, totalPixels, out)
                else -> convertRGB1555toBGRA(data, totalPixels, out) // 0RGB1555
            }
            return out
        }

        private fun convertXRGB8888toBGRA(data: ByteArray, out: ByteArray) {
            var i = 0
            while (i < data.size - 3) {
                out[i] = data[i]           // B
                out[i + 1] = data[i + 1]   // G
                out[i + 2] = data[i + 2]   // R
                out[i + 3] = 0xFF.toByte() // A
                i += 4
            }
        }

        private fun convertRGB565toBGRA(data: ByteArray, totalPixels: Int, out: ByteArray) {
            var srcIdx = 0
            var dstIdx = 0
            for (p in 0 until totalPixels) {
                if (srcIdx + 1 >= data.size) break
                val lo = data[srcIdx].toInt() and 0xFF
                val hi = data[srcIdx + 1].toInt() and 0xFF
                val pixel = lo or (hi shl 8)

                out[dstIdx] = ((pixel and 0x1F) * 255 / 31).toByte()         // B
                out[dstIdx + 1] = (((pixel shr 5) and 0x3F) * 255 / 63).toByte() // G
                out[dstIdx + 2] = (((pixel shr 11) and 0x1F) * 255 / 31).toByte() // R
                out[dstIdx + 3] = 0xFF.toByte()                                   // A

                srcIdx += 2
                dstIdx += 4
            }
        }

        private fun convertRGB1555toBGRA(data: ByteArray, totalPixels: Int, out: ByteArray) {
            var srcIdx = 0
            var dstIdx = 0
            for (p in 0 until totalPixels) {
                if (srcIdx + 1 >= data.size) break
                val lo = data[srcIdx].toInt() and 0xFF
                val hi = data[srcIdx + 1].toInt() and 0xFF
                val pixel = lo or (hi shl 8)

                out[dstIdx] = ((pixel and 0x1F) * 255 / 31).toByte()          // B
                out[dstIdx + 1] = (((pixel shr 5) and 0x1F) * 255 / 31).toByte()  // G
                out[dstIdx + 2] = (((pixel shr 10) and 0x1F) * 255 / 31).toByte() // R
                out[dstIdx + 3] = 0xFF.toByte()                                    // A

                srcIdx += 2
                dstIdx += 4
            }
        }
    }
}
