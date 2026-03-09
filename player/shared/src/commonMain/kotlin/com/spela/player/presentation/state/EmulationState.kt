package com.spela.player.presentation.state

import com.spela.player.domain.model.AchievementEvent
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.ShaderPreset

data class EmulationState(
    val gameId: String = "",
    val gameTitle: String = "",
    val consoleId: String = "",
    val consoleColorTheme: String? = null,
    val heroUrl: String? = null,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    /** True when paused by Android lifecycle (e.g. clamshell close). */
    val isLifecyclePaused: Boolean = false,
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
    val showGamepadConfig: Boolean = false,
    val error: String? = null,

    val achievementEvent: AchievementEvent? = null,
    val isHardcoreMode: Boolean = false,

    /** True when a secondary display is connected and showing content. */
    val secondaryDisplayActive: Boolean = false,

    /** True when running a dual-screen console game (e.g. Nintendo DS). */
    val isDualScreenConsole: Boolean = false,

    /** True when the loaded core uses HW rendering (OpenGL/Vulkan). */
    val isHwRenderEnabled: Boolean = false,

    /** Y pixel offset where the framebuffer splits (e.g. 192 for DS top/bottom). */
    val dualScreenSplitY: Int = 0,

    /** Width of the bottom screen in pixels (320 for 3DS, 256 for DS). */
    val dualScreenBottomWidth: Int = 0,
    /** X offset of the bottom screen within the framebuffer (40 for 3DS, 0 for DS). */
    val dualScreenBottomOffsetX: Int = 0,

    /** Elapsed play session time in seconds, updated every second while running. */
    val sessionElapsedSeconds: Long = 0,

    /** Shared session mode: set when playing a game through a shared session. */
    val sharedSessionId: String? = null,
    val turnToken: String? = null,

    /** Netplay mode: set when playing a game through a netplay session. */
    val netplaySessionId: String? = null,
    val netplayPeerUsername: String? = null,
    val netplayPeerLatencyMs: Int = 0,
    val netplayPeerDisconnected: Boolean = false,
    val netplayPausedByUsername: String? = null,
    val netplayShowLeaveConfirm: Boolean = false,
    val netplayPauseElapsedSeconds: Long = 0,
    val netplaySessionExpired: Boolean = false,

    /** Challenge mode: set when playing a challenge attempt. */
    val challengeId: String? = null,
    val challengeAttemptId: String? = null,
    val challengeObjective: String = "",
    val challengeElapsedMs: Long = 0,
    val showChallengeCreation: Boolean = false,
    val isCreatingChallenge: Boolean = false,
    val challengeCreationSuccess: Boolean = false,
    val showGiveUpConfirm: Boolean = false,
    val challengeCompletedAttempt: com.spela.player.domain.model.ChallengeAttempt? = null,

    /** BIOS: missing files detected before launch. */
    val showMissingBiosDialog: Boolean = false,
    val missingBiosFiles: List<BiosMissingFile> = emptyList(),
    val missingBiosConsoleName: String = "",

    /** Quick-save slots: currently selected slot number (1-10). */
    val activeSlot: Int = 1,

    /** Rewind: whether the rewind feature is enabled. */
    val rewindEnabled: Boolean = false,
    /** Rewind: whether rewind is currently active (holding down rewind). */
    val isRewinding: Boolean = false,

    /** Session: set when playing within a game session. */
    val sessionId: String? = null,

    /** Core mismatch: shown when auto-load detects a save from a different core. */
    val showCoreMismatchDialog: Boolean = false,
    val coreMismatchSaveCoreName: String = "",
    val coreMismatchCurrentCoreName: String = "",

    /** Cheats */
    val hasCheats: Boolean = false,
    val enabledCheatCount: Int = 0,
    val showCheatBrowser: Boolean = false,
    val cheats: List<com.spela.player.domain.model.Cheat> = emptyList(),
) {
    val isNetplayMode: Boolean get() = netplaySessionId != null
    val isChallengeMode: Boolean get() = challengeId != null
}
