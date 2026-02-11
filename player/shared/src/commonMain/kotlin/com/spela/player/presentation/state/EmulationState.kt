package com.spela.player.presentation.state

import com.spela.player.domain.model.ShaderPreset

data class EmulationState(
    val gameId: String = "",
    val gameTitle: String = "",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isLoading: Boolean = false,
    val showOverlay: Boolean = false,
    val showPerformanceOverlay: Boolean = false,
    val selectedShader: ShaderPreset = ShaderPreset.NONE,

    val showExitConfirm: Boolean = false,
    val requestExit: Boolean = false,
    val statusMessage: String? = null,
    val fps: Float = 0f,
    val frameTime: Float = 0f,
    val isFastForward: Boolean = false,
    val error: String? = null,
)
