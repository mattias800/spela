package com.spela.player.presentation.state

import com.spela.player.domain.model.AchievementEvent
import com.spela.player.domain.model.ShaderPreset

data class EmulationState(
    val gameId: String = "",
    val gameTitle: String = "",
    val consoleId: String = "",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isLoading: Boolean = false,
    val showOverlay: Boolean = false,
    val showPerformanceOverlay: Boolean = false,
    val selectedShader: ShaderPreset = ShaderPreset.NONE,

    val supportsSaveStates: Boolean = false,
    val showExitConfirm: Boolean = false,
    val requestExit: Boolean = false,
    val statusMessage: String? = null,
    val fps: Float = 0f,
    val frameTime: Float = 0f,
    val isFastForward: Boolean = false,
    val showKeyMapping: Boolean = false,
    val error: String? = null,

    val achievementEvent: AchievementEvent? = null,
    val isHardcoreMode: Boolean = false,

    /** True when a secondary display is connected and showing content. */
    val secondaryDisplayActive: Boolean = false,

    /** True when running a dual-screen console game (e.g. Nintendo DS). */
    val isDualScreenConsole: Boolean = false,

    /** Y pixel offset where the framebuffer splits (e.g. 192 for DS top/bottom). */
    val dualScreenSplitY: Int = 0,

    /** Elapsed play session time in seconds, updated every second while running. */
    val sessionElapsedSeconds: Long = 0,

    /** Relay mode: set when playing a game through a relay. */
    val relayId: String? = null,
    val turnToken: String? = null,
)
