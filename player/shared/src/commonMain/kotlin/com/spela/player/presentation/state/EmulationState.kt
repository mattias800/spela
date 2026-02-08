package com.spela.player.presentation.state

data class EmulationState(
    val gameId: String = "",
    val gameTitle: String = "",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isLoading: Boolean = false,
    val showOverlay: Boolean = false,
    val fps: Float = 0f,
    val frameTime: Float = 0f,
    val isFastForward: Boolean = false,
    val error: String? = null,
)
