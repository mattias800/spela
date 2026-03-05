package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
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

    val isOverlayVisible = emulationState.showOverlay || emulationState.showKeyMapping || emulationState.showGamepadConfig

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
        isOverlayVisible = isOverlayVisible,
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
