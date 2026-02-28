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
 * When the in-game overlay is shown, we toggle [SurfaceView.setZOrderOnTop] to
 * push the surface behind Compose content so the overlay is visible. This avoids
 * destroying the surface (and the Vulkan context), which would crash HW render
 * cores like Dolphin that can't handle context teardown/rebuild.
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
                        if (controller.gpuIsActive()) {
                            Log.i(TAG, "GPU already active, skipping init")
                            return
                        }
                        // Try to resume a suspended renderer first
                        val resumed = controller.gpuResume(holder.surface)
                        if (resumed) {
                            controller.gpuSetShader(selectedShader.gpuShaderId)
                            Log.i(TAG, "Vulkan GPU renderer resumed")
                            return
                        }
                        // First-time init
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
                            // Suspend instead of full deinit — keeps Vulkan device
                            // and HW render context alive so the core isn't disrupted.
                            controller.gpuSuspend()
                        }
                    }
                })
            }
        },
        update = { surfaceView ->
            // Toggle z-ordering instead of visibility. When the overlay is showing,
            // push the SurfaceView behind Compose so the overlay is visible on top.
            // This keeps the surface alive (no surfaceDestroyed), avoiding Vulkan
            // context teardown that crashes HW render cores like Dolphin.
            surfaceView.setZOrderOnTop(!isOverlayVisible)
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

    // Full cleanup when this composable is permanently removed from the tree
    DisposableEffect(Unit) {
        onDispose {
            Log.i(TAG, "VulkanEmulationSurface composable disposed")
            if (controller.gpuIsActive()) {
                controller.gpuDeinit()
            }
        }
    }
}

private const val TAG = "VulkanEmulationSurface"
