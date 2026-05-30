package com.spela.player.presentation.ui.feature.ingame

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.libretro.EmulationSurface
import com.spela.player.libretro.VulkanEmulationSurface
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import org.koin.compose.koinInject

@Composable
actual fun PlatformEmulationSurface(
    controller: LibretroController,
    selectedShader: ShaderPreset,
    modifier: Modifier,
    onEscapePressed: (() -> Unit)?,
    // Unused on Android — the overlay is navigated via the platform back gesture
    // and Android's own gamepad key dispatch (#1211 is a desktop-input fix).
    overlayVisible: Boolean,
) {
    val androidController = controller as? AndroidLibretroController ?: return
    val emulationViewModel: EmulationViewModel = koinInject()
    val emulationState by emulationViewModel.state.collectAsState()

    val isDualScreenSplit = emulationState.isDualScreenConsole && emulationState.secondaryDisplayActive
    val isEmu = com.spela.player.util.isEmulator()
    val hwRenderEnabled = emulationState.isHwRenderEnabled
    val gpuActive = androidController.gpuIsActive()

    val useVulkanSurface = if (isEmu) {
        gpuActive
    } else {
        hwRenderEnabled || gpuActive
    }

    // #895 — log the surface-path decision whenever the inputs flip.
    // This is the data point the issue specifically calls out for
    // diagnosing the PSP black-screen / garbled-audio / exit-crash
    // regression: black screen on a HW-render core almost always
    // means we ended up on the EmulationSurface (Canvas/CPU) path
    // because gpuActive flipped false. Pair these lines with the
    // existing "Vulkan GPU renderer initialized" / "Vulkan GPU init
    // failed" markers from VulkanEmulationSurface.kt to trace where
    // the handshake breaks.
    LaunchedEffect(hwRenderEnabled, gpuActive, isDualScreenSplit, useVulkanSurface, isEmu) {
        Log.i(
            "PlatformEmulationSurface",
            "[#895] surface decision: hwRenderEnabled=$hwRenderEnabled gpuActive=$gpuActive " +
                "isDualScreenSplit=$isDualScreenSplit isEmu=$isEmu " +
                "→ useVulkanSurface=$useVulkanSurface " +
                "(canvas-path=${!useVulkanSurface || isDualScreenSplit})",
        )
    }

    // Set flag so emulation loop populates frameBitmap via CPU readback
    // (for both primary and secondary displays in dual-screen mode) and
    // skips GPU present (primary uses EmulationSurface instead).
    androidController.dualScreenSplitActive = isDualScreenSplit

    // IMPORTANT: VulkanEmulationSurface must ALWAYS be at the same position in the
    // composition tree. Putting it inside different if/else branches causes Compose
    // to destroy and recreate it when the branch changes, triggering gpuDeinit()
    // while the emulation thread is still running — a fatal race condition.
    VulkanEmulationSurface(
        controller = androidController,
        selectedShader = selectedShader,
        isHwRenderEnabled = hwRenderEnabled,
        modifier = modifier,
    )

    // Canvas-based rendering for primary display:
    // - Software cores (DS/desmume): always used (GPU renderer not active)
    // - Dual-screen split (DS or 3DS): crops to top screen via isDualScreenSplit.
    //   For 3DS, the emulation loop skips nativeGpuRender() and populates
    //   frameBitmap from CPU readback instead. The Y-flip is handled in
    //   updateVideoFrame() so the Bitmap is in correct orientation.
    if (!useVulkanSurface || isDualScreenSplit) {
        EmulationSurface(
            controller = androidController,
            selectedShader = selectedShader,
            isDualScreenSplit = isDualScreenSplit,
            splitY = if (isDualScreenSplit) emulationState.dualScreenSplitY else 0,
            modifier = modifier,
        )
    }
}
