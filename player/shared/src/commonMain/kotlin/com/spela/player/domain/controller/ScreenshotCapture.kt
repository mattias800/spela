package com.spela.player.domain.controller

/**
 * Platform-specific screenshot capture from the running emulation.
 * Returns a PNG-encoded screenshot of the current game frame.
 */
interface ScreenshotCapture {
    /**
     * Capture the current emulation frame as PNG bytes.
     * Returns null if no frame is available (e.g. emulation not running).
     */
    fun captureScreenshot(): ByteArray?
}
