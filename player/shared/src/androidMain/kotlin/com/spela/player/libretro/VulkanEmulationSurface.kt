package com.spela.player.libretro

import android.graphics.PixelFormat
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.feature.shader.gpuShaderId

/**
 * Composable that renders emulation video via Vulkan GPU rendering.
 *
 * This composable is always present in the tree (even before the core has loaded)
 * so that the Android SurfaceView gets a native surface from the window manager.
 * Dynamically adding a SurfaceView via a Compose conditional branch swap causes
 * the surface to never be created.
 *
 * The surface starts transparent (TRANSLUCENT format). GPU rendering is deferred
 * until [isHwRenderEnabled] becomes true (after the core requests HW rendering
 * during loadGame). For non-HW-render cores, the SurfaceView sits idle and the
 * software [EmulationSurface] renders via Canvas beneath it.
 *
 * The SurfaceView renders behind the Compose layer so that Compose-based UI
 * (touch controls, in-game overlay) is always visible on top of the game.
 */
@Composable
fun VulkanEmulationSurface(
    controller: AndroidLibretroController,
    selectedShader: ShaderPreset,
    isHwRenderEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Track whether the native surface is available
    val surfaceReady = remember { mutableStateOf(false) }
    val surfaceHolderRef = remember { mutableStateOf<SurfaceHolder?>(null) }

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                controller.vulkanSurfaceView = this
                // Start transparent so software EmulationSurface shows through
                // until the GPU renderer starts drawing.
                holder.setFormat(PixelFormat.TRANSLUCENT)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.i(TAG, "Vulkan surface created")
                        surfaceHolderRef.value = holder
                        surfaceReady.value = true
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
                        surfaceReady.value = false
                        surfaceHolderRef.value = null
                        if (controller.gpuIsActive()) {
                            // Suspend instead of full deinit — keeps Vulkan device
                            // and HW render context alive so the core isn't disrupted.
                            controller.gpuSuspend()
                        }
                    }
                })
            }
        },
        update = { _ -> },
        modifier = modifier.fillMaxSize(),
    )

    // Initialize the GPU renderer when BOTH conditions are met:
    // 1. The native surface is available (surfaceCreated has fired)
    // 2. The core has requested HW rendering (isHwRenderEnabled is true)
    // This handles both orderings: surface first then loadGame, or loadGame first then surface.
    LaunchedEffect(surfaceReady.value, isHwRenderEnabled) {
        if (!surfaceReady.value || !isHwRenderEnabled) return@LaunchedEffect
        val holder = surfaceHolderRef.value ?: return@LaunchedEffect

        if (controller.gpuIsActive()) {
            Log.i(TAG, "GPU already active, skipping init")
            return@LaunchedEffect
        }

        // Try to resume a suspended renderer first
        val resumed = controller.gpuResume(holder.surface)
        if (resumed) {
            controller.gpuSetShader(selectedShader.gpuShaderId)
            Log.i(TAG, "Vulkan GPU renderer resumed")
            return@LaunchedEffect
        }

        // First-time init
        val success = controller.gpuInit(holder.surface)
        if (success) {
            controller.gpuSetShader(selectedShader.gpuShaderId)
            Log.i(TAG, "Vulkan GPU renderer initialized with HW render")
        } else {
            Log.w(TAG, "Vulkan GPU init failed")
        }
    }

    // Update shader when it changes
    DisposableEffect(selectedShader) {
        if (controller.gpuIsActive()) {
            controller.gpuSetShader(selectedShader.gpuShaderId)
        }
        onDispose { }
    }

    // Log when composable is removed from the tree. GPU renderer cleanup is
    // handled by AndroidLibretroController.stop() which runs after the emulation
    // loop has stopped — calling gpuDeinit here would race with the emulation thread.
    DisposableEffect(Unit) {
        onDispose {
            controller.vulkanSurfaceView = null
            Log.i(TAG, "VulkanEmulationSurface composable disposed")
        }
    }
}

private const val TAG = "VulkanEmulationSurface"
