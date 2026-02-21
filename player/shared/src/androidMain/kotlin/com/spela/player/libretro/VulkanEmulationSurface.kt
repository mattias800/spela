package com.spela.player.libretro

import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.feature.shader.gpuShaderId

/**
 * Composable that renders emulation video via Vulkan GPU rendering.
 *
 * Uses [SurfaceView] with [SurfaceView.setZOrderOnTop] to render the Vulkan
 * swapchain above the app window. Without this, the SurfaceView renders behind
 * the app window, and the opaque Compose/theme layers occlude it.
 *
 * Compose overlays (touch gamepad, HUD, settings) render on top of this surface.
 */
@Composable
fun VulkanEmulationSurface(
    controller: AndroidLibretroController,
    selectedShader: ShaderPreset,
    isOverlayVisible: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                setZOrderOnTop(true)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.i(TAG, "Vulkan surface created")
                        val success = controller.gpuInit(holder.surface)
                        if (success) {
                            controller.gpuSetShader(selectedShader.gpuShaderId)
                            Log.i(TAG, "Vulkan GPU renderer initialized")
                        } else {
                            Log.w(TAG, "Vulkan GPU init failed, falling back to software")
                        }
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        Log.i(TAG, "Vulkan surface changed: ${width}x${height}")
                        if (controller.gpuIsActive()) {
                            controller.gpuResize(width, height)
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.i(TAG, "Vulkan surface destroyed")
                        if (controller.gpuIsActive()) {
                            controller.gpuDeinit()
                        }
                    }
                })
            }
        },
        update = { surfaceView ->
            // Hide the SurfaceView when an overlay is showing. Since setZOrderOnTop(true)
            // renders the Surface above the entire Compose layer, the overlay (which is
            // Compose content) would be invisible behind it. INVISIBLE keeps the Surface
            // alive (no surfaceDestroyed) while letting Compose overlays render on top.
            surfaceView.visibility = if (isOverlayVisible) {
                android.view.View.INVISIBLE
            } else {
                android.view.View.VISIBLE
            }
        },
        modifier = modifier.fillMaxSize(),
    )

    // Update shader when it changes
    DisposableEffect(selectedShader) {
        if (controller.gpuIsActive()) {
            controller.gpuSetShader(selectedShader.gpuShaderId)
        }
        onDispose { }
    }
}

private const val TAG = "VulkanEmulationSurface"
