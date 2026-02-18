package com.spela.player.libretro

import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.feature.shader.gpuShaderId

/**
 * Composable that renders emulation video via Vulkan GPU rendering.
 *
 * Wraps an Android [SurfaceView] which provides the native window handle
 * for Vulkan swapchain creation. The emulation thread uploads frames directly
 * to the Vulkan staging buffer (no JNI byte array copy), and GPU rendering
 * happens with real SPIR-V fragment shaders.
 *
 * Compose overlays (touch gamepad, HUD, settings) render on top of this surface.
 */
@Composable
fun VulkanEmulationSurface(
    controller: AndroidLibretroController,
    selectedShader: ShaderPreset,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
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
            modifier = Modifier.fillMaxSize(),
        )
    }

    // Update shader when it changes
    DisposableEffect(selectedShader) {
        if (controller.gpuIsActive()) {
            controller.gpuSetShader(selectedShader.gpuShaderId)
        }
        onDispose { }
    }
}

private const val TAG = "VulkanEmulationSurface"
