package com.spela.player.libretro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.screen.gpuShaderId
import java.awt.Canvas
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * macOS Metal emulation surface for Compose Desktop.
 *
 * Uses [SwingPanel] wrapping a [java.awt.Canvas] to get a native window handle.
 * The native code extracts the NSView via JAWT and creates a CAMetalLayer for
 * GPU-accelerated rendering.
 *
 * On non-macOS platforms, this composable is not used (VulkanEmulationSurface is used instead).
 */
@Composable
fun MetalEmulationSurface(
    controller: DesktopLibretroController,
    selectedShader: ShaderPreset,
    modifier: Modifier = Modifier,
) {
    var gpuInitialized by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        SwingPanel(
            factory = {
                object : Canvas() {
                    override fun addNotify() {
                        super.addNotify()
                        // Canvas is now displayable -- native code uses JAWT to extract
                        // the NSObject<JAWT_SurfaceLayers> and attaches a CAMetalLayer.
                        try {
                            val success = controller.gpuInit(this)
                            if (success) {
                                controller.gpuSetShader(selectedShader.gpuShaderId)
                                // Send initial size
                                if (width > 0 && height > 0) {
                                    controller.gpuResize(width, height)
                                }
                                gpuInitialized = true
                            }
                        } catch (e: Exception) {
                            println("Metal GPU init failed: ${e.message}")
                        }

                        // Track size changes
                        addComponentListener(object : ComponentAdapter() {
                            override fun componentResized(e: ComponentEvent) {
                                if (gpuInitialized && width > 0 && height > 0) {
                                    controller.gpuResize(width, height)
                                }
                            }
                        })
                    }

                    override fun removeNotify() {
                        if (gpuInitialized) {
                            controller.gpuDeinit()
                            gpuInitialized = false
                        }
                        super.removeNotify()
                    }

                    override fun paint(g: Graphics) {
                        // Metal renders via its own CALayer. Do not paint over it.
                        if (!gpuInitialized) {
                            g.color = java.awt.Color.BLACK
                            g.fillRect(0, 0, width, height)
                        }
                    }

                    override fun update(g: Graphics) {
                        // Skip default clear + paint to avoid flicker over Metal layer.
                        paint(g)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // Update shader when it changes
    DisposableEffect(selectedShader) {
        if (gpuInitialized) {
            controller.gpuSetShader(selectedShader.gpuShaderId)
        }
        onDispose { }
    }

    // Handle cleanup on dispose
    DisposableEffect(controller) {
        onDispose {
            if (gpuInitialized) {
                controller.gpuDeinit()
            }
        }
    }
}

