package com.spela.player.presentation.state

import com.spela.player.domain.model.AchievementEvent
import com.spela.player.domain.model.AchievementProgress
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.GameAchievement
import com.spela.player.domain.model.SaveStatePolicyTier
import com.spela.player.domain.model.ShaderPreset

/**
 * Mode of the in-game slot-picker modal that the slot-primary UX
 * (medium / large console tiers) opens when the user taps Save or
 * Load on the in-game overlay. See #804 phase 5.
 */
enum class SlotPickerMode { Save, Load }

/** Input mode tabs available on the secondary screen controls page. */
enum class ControlTab(val id: String) {
    GAMEPAD("gamepad"),
    KEYBOARD("keyboard"),
    TRACKPAD("trackpad");

    companion object {
        fun fromId(id: String): ControlTab = entries.find { it.id == id } ?: GAMEPAD
    }
}

/**
 * Type of secondary screen toast notification.
 */
enum class SecondaryToastType {
    SAVE,
    LOAD,
}

/**
 * Data for a brief toast notification shown on the secondary screen
 * after save/load operations complete.
 */
data class SecondaryToastData(
    val message: String,
    val type: SecondaryToastType,
    val id: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
)

/**
 * Information about a single save slot, used for the secondary screen save slots page.
 */
data class SaveSlotInfo(
    val screenshotUrl: String? = null,
    val timestamp: String? = null,
    val isFilled: Boolean = false,
    /** Server save id backing this slot. Null when the slot is empty
     *  or when the source predates the slot-id wiring (#831). Required
     *  for the rename / delete actions on the in-game slot picker. */
    val saveId: String? = null,
    /** Existing user-supplied save name, if any. Surfaced into the
     *  rename dialog as the prefill (#831). */
    val name: String? = null,
)

/**
 * An achievement unlocked during the current play session.
 */
data class SessionAchievementUnlock(
    val achievementId: Long,
    val title: String,
    val points: Int,
    val unlockedAtMs: Long,
)

data class EmulationState(
    val gameId: String = "",
    val gameTitle: String = "",
    val consoleId: String = "",
    val consoleName: String = "",
    val consoleColorTheme: String? = null,
    val heroUrl: String? = null,
    val gameDescription: String? = null,
    val gameDeveloper: String? = null,
    val gamePublisher: String? = null,
    val gameReleaseDate: String? = null,
    val gameGenre: String? = null,
    val gameRating: Double = 0.0,
    val gamePlayers: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    /** True when paused by Android lifecycle (e.g. clamshell close). */
    val isLifecyclePaused: Boolean = false,
    val isLoading: Boolean = false,
    val showOverlay: Boolean = false,
    val showPerformanceOverlay: Boolean = false,
    val selectedShader: ShaderPreset = ShaderPreset.NONE,

    val supportsSaveStates: Boolean = false,
    /**
     * True when the user has explicitly opted out of save states for
     * this console (`consoleSaveStatePolicies[abbr] == disabled`). The
     * in-game overlay greys the Save and Load action buttons when set,
     * with a tooltip pointing back to Settings. The toggle is read-
     * only here on purpose — flipping mid-session creates ambiguity
     * about in-flight uploads, see #804 phase 4 spec point (d).
     */
    val saveStatesOptedOut: Boolean = false,
    /**
     * Save-state size tier of the current game's console (small /
     * medium / large). Drives slot-primary UX on medium/large tiers
     * (#804 phase 5): tapping Save opens a slot picker rather than
     * performing a free-form named save. Small-tier UX is unchanged.
     * Defaults to small so unknown / unset cases keep the historical
     * behaviour.
     */
    val consoleSaveStatePolicyTier: SaveStatePolicyTier = SaveStatePolicyTier.Small,
    /**
     * Non-null when the in-game slot picker modal is open. The user
     * dismisses it via back / scrim tap, or commits by picking a
     * slot which triggers SaveToSlot or LoadFromSlot. See #804
     * phase 5.
     */
    val slotPickerMode: SlotPickerMode? = null,
    /**
     * True when the medium-tier "Save with name…" dialog is open
     * (#830). The dialog is reachable from the bottom of the slot
     * picker — slot-primary stays the default, named save is the
     * secondary affordance for "I want a deliberate marker before
     * the boss fight" cases. Hidden on small (free-form named saves
     * are already the default) and large (slot-only by spec) tiers.
     */
    val showNamedSaveDialog: Boolean = false,
    /**
     * Non-null when the user long-presses a filled slot cell on the
     * in-game slot picker. Drives the slot-actions bottom sheet
     * (Rename / Delete / Cancel). See #831.
     */
    val slotActionsSheetSlot: Int? = null,
    /**
     * Non-null when the rename dialog is open for the given slot.
     * Pre-fills with the slot's existing name. See #831.
     */
    val slotRenameTarget: Int? = null,
    /**
     * Non-null when the delete-confirmation dialog is open for the
     * given slot. Two-step delete is deliberate — the user can dismiss
     * before the irreversible call hits the server. See #831.
     */
    val slotDeleteConfirmSlot: Int? = null,
    /**
     * True when the user is launching a large-tier console game for
     * the first time (no override + tier == large → AskOnce). Drives
     * the first-launch dialog. See #804 phase 4b spec point (b).
     */
    val showSaveStatePrompt: Boolean = false,
    /** Console abbreviation backing [showSaveStatePrompt] (e.g. "gc"). */
    val saveStatePromptConsoleAbbr: String = "",
    /** Display name backing [showSaveStatePrompt] (e.g. "Nintendo GameCube"). */
    val saveStatePromptConsoleName: String = "",
    val showExitConfirm: Boolean = false,
    val requestExit: Boolean = false,
    val statusMessage: String? = null,
    /** True from the moment the user taps the in-game overlay's Save
     *  button until the upload completes (success or failure). Drives
     *  the inline button feedback in the overlay so the user sees
     *  "Saving…" without having to wait for a toast. See #803. */
    val isSaveInProgress: Boolean = false,
    /** Sticky error message from the most recent failed manual save.
     *  Cleared at the start of the next save attempt so the button
     *  doesn't show stale failure text indefinitely. See #803. */
    val saveStateError: String? = null,
    /** True briefly after a manual save succeeds so the in-game
     *  overlay can flash a "Saved" checkmark on the Save button.
     *  Auto-cleared by SaveManager ~1.5 s after the upload settles. */
    val saveStateJustSucceeded: Boolean = false,
    /**
     * True when one or more save uploads are sitting in the persistent
     * pending-upload queue (#804 phase 6). The Save button shows
     * "Saved locally · syncing" while this is set so the user knows
     * their save is captured even though the upload hasn't completed
     * yet. SaveManager flips this off when the queue empties.
     */
    val hasPendingUploads: Boolean = false,
    /**
     * Number of pending uploads whose retry count has reached the
     * "stuck" threshold — failed enough times that they're probably
     * not just slow. The in-game overlay surfaces a "Sync paused — N
     * saves waiting" line when this is > 0 so the user can tell
     * stuck-on-error apart from in-flight-and-slow without having to
     * dig into logs. See #804 phase 6 slice 4.
     */
    val stuckUploadCount: Int = 0,
    /**
     * Non-blocking warning shown when the session's pinned core
     * version (see `GameSession.pinnedCoreSha256`) is no longer
     * available on the server (pruned from history retention) and
     * emulation fell back to the latest core. `null` when there's no
     * issue. #555 Phase 3.
     */
    val coreVersionWarning: String? = null,
    val fps: Float = 0f,
    val frameTime: Float = 0f,
    val isFastForward: Boolean = false,
    val volume: Float = 1f,
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

    /** Quick-save slots: metadata for each slot (1-10), keyed by slot number. */
    val saveSlots: Map<Int, SaveSlotInfo> = emptyMap(),

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

    /** Persistent core mismatch flag: true for the entire session if cores differ. */
    val isCoreMismatched: Boolean = false,
    /** The original core name from the session (for save warning dialog). */
    val mismatchedOriginalCore: String = "",
    /** Show the save warning dialog when attempting to save with mismatched core. */
    val showCoreMismatchSaveDialog: Boolean = false,
    /** The type of save that triggered the warning ("auto", "manual", "slot", "quick"). */
    val pendingSaveType: String = "",
    /** The slot number for pending slot saves. */
    val pendingSaveSlot: Int = 0,

    /** Default second screen page from user preferences. */
    val defaultSecondScreenPage: String = "art",

    /** Cheats */
    val hasCheats: Boolean = false,
    val enabledCheatCount: Int = 0,
    val showCheatBrowser: Boolean = false,
    val cheats: List<com.spela.player.domain.model.Cheat> = emptyList(),

    /** Achievements: fetched from server for the current game. */
    val achievements: List<GameAchievement> = emptyList(),
    val achievementProgress: List<AchievementProgress> = emptyList(),
    val achievementTotalPoints: Int = 0,
    val achievementsLoading: Boolean = false,
    val sessionAchievementUnlocks: List<SessionAchievementUnlock> = emptyList(),

    /** Secondary screen toast: brief notification after save/load operations. */
    val secondaryToast: SecondaryToastData? = null,

    /** Which port the touch controls target (0 = Player 1, 1 = Player 2). */
    val touchControlPort: Int = 0,

    /** Which input tab is active on the secondary screen controls page. */
    val selectedControlTab: ControlTab = ControlTab.GAMEPAD,

    /**
     * When non-null, a #672 core-upgrade decision is pending and the
     * UI layer should render the matching sheet BEFORE emulation
     * starts, OR (for [CoreDecision.RehearsalPrompt] /
     * [CoreDecision.RehearsalCrashed]) the rehearsal banner / crash
     * sheet should overlay the running emulator. `null` while
     * emulation runs normally outside the rehearsal flow. The VM
     * clears this as each sheet action resolves; see
     * [com.spela.player.presentation.intent.EmulationIntent] for the
     * intents that drive resolution.
     */
    val coreDecision: CoreDecision? = null,

    /**
     * True when the rehearsal "Did this work?" confirmation Sheet C
     * is visible on top of the rehearsal banner. Distinct from
     * `coreDecision` so the banner stays visible behind the sheet —
     * the user can dismiss the sheet without leaving rehearsal mode.
     * Always false when `coreDecision !is CoreDecision.RehearsalPrompt`.
     * See #672 PR 3c.ii.
     */
    val showRehearsalConfirmSheet: Boolean = false,
) {
    val isNetplayMode: Boolean get() = netplaySessionId != null
    val isChallengeMode: Boolean get() = challengeId != null

    val hasAchievements: Boolean get() = achievements.isNotEmpty()
    val achievementUnlockedCount: Int get() = achievementProgress.count { it.unlockedAt != null }
    val achievementEarnedPoints: Int get() {
        val unlockedIds = achievementProgress.filter { it.unlockedAt != null }.map { it.achievementId }.toSet()
        return achievements.filter { it.id in unlockedIds }.sumOf { it.points }
    }
}
