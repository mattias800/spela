package com.spela.player.libretro

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import com.spela.player.domain.controller.ScreenshotCapture
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Android screenshot capture for save state thumbnails.
 *
 * Two capture paths:
 * - Software cores (GPU not active): reads the current frame from
 *   [AndroidLibretroController.frameBitmap] which is double-buffered and
 *   updated every frame by the emulation loop.
 * - GPU cores (GLES/Vulkan): uses [PixelCopy] to read from the
 *   [SurfaceView] registered on the controller. PixelCopy works regardless
 *   of the rendering backend (GLES or Vulkan).
 */
class AndroidScreenshotCapture(
    private val controller: AndroidLibretroController,
) : ScreenshotCapture {

    override fun captureScreenshot(): ByteArray? {
        if (controller.gpuIsActive()) {
            return captureFromSurfaceView()
        }
        return captureFromFrameBitmap()
    }

    private fun captureFromFrameBitmap(): ByteArray? {
        val bitmap = controller.frameBitmap.value ?: return null
        return compressToPng(bitmap)
    }

    private fun captureFromSurfaceView(): ByteArray? {
        val surfaceView = controller.vulkanSurfaceView ?: return null
        val width = surfaceView.width
        val height = surfaceView.height
        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var success = false

        PixelCopy.request(surfaceView, bitmap, { result ->
            success = result == PixelCopy.SUCCESS
            latch.countDown()
        }, Handler(Looper.getMainLooper()))

        if (!latch.await(2, TimeUnit.SECONDS) || !success) {
            bitmap.recycle()
            return null
        }

        val result = compressToPng(bitmap)
        bitmap.recycle()
        return result
    }

    private fun compressToPng(bitmap: Bitmap): ByteArray? {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        return if (bytes.isNotEmpty()) bytes else null
    }
}
