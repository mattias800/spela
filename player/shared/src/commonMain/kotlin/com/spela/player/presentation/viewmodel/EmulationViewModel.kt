package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.PresenceService
import com.spela.player.data.repository.BiosRepository
import com.spela.player.domain.controller.AchievementsController
import com.spela.player.domain.model.AchievementEventType
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.AchievementsRepository
import com.spela.player.domain.repository.GameStatsRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.repository.CheatRepository
import com.spela.player.domain.usecase.PrepareGameUseCase
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.state.SessionAchievementUnlock
import com.spela.player.util.DispatcherProvider
import com.spela.player.presentation.state.GameSyncState
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REWIND_BUFFER_SIZE = 300 // ~60 seconds at 5fps capture rate
private const val REWIND_CAPTURE_INTERVAL_MS = 200L // Capture every 200ms (~5 per second)

/** Stores parameters for a pending game launch while pre-launch sync is in progress. */
data class PendingLaunch(
    val gameId: String,
    val skipAutoLoad: Boolean,
    val forceNewSession: Boolean = false,
    val sessionId: String? = null,
)

/**
 * Bridges between Compose UI and the platform-specific libretro core.
 * The actual emulation is driven by LibretroCore (platform-specific),
 * this ViewModel manages the lifecycle and state.
 */
class EmulationViewModel(
    private val prepareGameUseCase: PrepareGameUseCase,
    private val getGameDetailUseCase: GetGameDetailUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val achievementsRepository: AchievementsRepository,
    private val achievementsController: AchievementsController,
    private val libretroController: LibretroController,
    private val secondaryDisplay: PlatformSecondaryDisplay,
    private val presenceService: PresenceService,
    private val gamepadPortManager: GamepadPortManager,
    private val saveManager: SaveManager,
    private val challengeManager: ChallengeManager,
    private val netplayManager: NetplayManager,
    private val _state: MutableStateFlow<EmulationState>,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val biosRepository: BiosRepository? = null,
    private val cheatRepository: CheatRepository? = null,
    private val gameStatsRepository: GameStatsRepository? = null,
) {
    val state: StateFlow<EmulationState> = _state.asStateFlow()

    private val _syncState = MutableStateFlow<GameSyncState?>(null)
    val syncState: StateFlow<GameSyncState?> = _syncState.asStateFlow()

    private val _launchReady = MutableSharedFlow<PendingLaunch>(replay = 0, extraBufferCapacity = 1)
    val launchReady: SharedFlow<PendingLaunch> = _launchReady.asSharedFlow()

    private var pendingLaunch: PendingLaunch? = null

    /**
     * Stored [EmulationIntent.StartGame] captured when the
     * PrepareGameUseCase reports an UpgradeAvailable decision (#672).
     * Re-fired with `skipCoreDecisionPrompt = true` once the user
     * picks an action via Sheet A. `null` while no decision is
     * pending.
     *
     * `@Volatile` is load-bearing here — the field is written from
     * the coroutine running on the IO dispatcher (inside `startGame`)
     * and read from the main-thread intent dispatcher in
     * `resolveCoreDecision`. Without the annotation, the resolution
     * could see a stale `null` if the sheet is dismissed immediately
     * after the writer coroutine suspends.
     */
    @Volatile
    private var pendingCoreDecisionStart: EmulationIntent.StartGame? = null

    /**
     * #672 PR 3c.ii — when the user accepts rehearsal via Sheet A's
     * "Try with my save", we stash the RehearsalPrompt here and apply
     * it to [EmulationState.coreDecision] once the re-fired StartGame
     * has finished initial setup. Writing it directly inside
     * [resolveCoreDecision] would be overwritten by startGame's bulk
     * state reset; staging it here and consuming it in startGame keeps
     * the banner visible from the first composed frame.
     *
     * `@Volatile` for the same cross-dispatcher reason as
     * [pendingCoreDecisionStart].
     */
    @Volatile
    private var pendingRehearsalPrompt: com.spela.player.presentation.state.CoreDecision.RehearsalPrompt? = null

    private var currentPreferences = UserPreferences()
    private var sessionTimerJob: Job? = null
    private var stopJob: Job? = null
    private var skipBiosCheck = false

    init {
        // Observe game/console changes and reload gamepad mappings for all ports.
        // Centralized here so both Android and Desktop get mapping reloads.
        scope.launch(dispatchers.io) {
            _state
                .map { Pair(it.gameId, it.consoleId) }
                .distinctUntilChanged()
                .collect { (gameId, consoleId) ->
                    if (consoleId.isNotEmpty()) {
                        try {
                            if (gameId.isNotEmpty()) {
                                gamepadPortManager.loadAllGameMappings(gameId, consoleId)
                            } else {
                                gamepadPortManager.loadAllMappings(consoleId)
                            }
                        } catch (_: Exception) {
                            // Best effort - defaults will be used
                        }
                    }
                }
        }

        // #672 PR 3c.ii — surface the rehearsal "saves are paused"
        // snackbar whenever SaveManager silently drops a save write
        // while rehearsalMode is active. Uses `statusMessage` so the
        // existing dismissable-snackbar UI picks it up without a new
        // channel. The copy is the canonical `core_upd.snack.rehearsal_save`
        // string from the spec.
        scope.launch(dispatchers.main) {
            saveManager.rehearsalSaveBlocked.collect {
                _state.update {
                    it.copy(
                        statusMessage = "You're in a trial run — saves are paused until you " +
                            "keep this version. Tap 'Did this work?' above to decide.",
                    )
                }
            }
        }
    }

    fun onIntent(intent: EmulationIntent) {
        when (intent) {
            is EmulationIntent.StartGame -> startGame(
                intent.gameId, intent.sharedSessionId, intent.turnToken,
                intent.netplaySessionId, intent.netplayLocalPort, intent.netplayInputDelay, intent.netplayIsHost,
                intent.challengeId, intent.challengeSaveData, intent.skipAutoLoad,
                intent.forceNewSession, intent.sessionId,
                skipCoreDecisionPrompt = intent.skipCoreDecisionPrompt,
            )
            EmulationIntent.PauseGame -> pauseGame()
            EmulationIntent.ResumeGame -> resumeGame()
            EmulationIntent.StopGame -> stopGame()
            EmulationIntent.SaveState -> {
                if (checkCoreMismatchBeforeSave("manual")) {
                    saveManager.saveState()
                }
            }
            EmulationIntent.LoadState -> saveManager.loadState()
            EmulationIntent.ToggleOverlay -> {
                val wasShowing = _state.value.showOverlay
                _state.update { it.copy(showOverlay = !it.showOverlay) }
                if (wasShowing) resumeGame() else pauseGame()
            }
            EmulationIntent.ToggleFastForward -> toggleFastForward()
            is EmulationIntent.SetVolume -> {
                val vol = intent.volume.coerceIn(0f, 1f)
                libretroController.setVolume(vol)
                _state.update { it.copy(volume = vol) }
            }
            EmulationIntent.TakeScreenshot -> { /* Platform-specific capture */ }

            EmulationIntent.ShowExitConfirm -> {
                if (_state.value.supportsSaveStates) {
                    // Game supports save states — exit immediately.
                    // If auto-save is on, stopGame() will save. If off, user chose to skip saves.
                    pauseGame()
                    _state.update { it.copy(showExitConfirm = false, requestExit = true) }
                    stopGame()
                } else {
                    // Game doesn't support save states — warn the user.
                    _state.update { it.copy(showExitConfirm = true) }
                }
            }
            EmulationIntent.DismissExitConfirm -> _state.update { it.copy(showExitConfirm = false) }
            EmulationIntent.ConfirmExit -> {
                _state.update { it.copy(showExitConfirm = false, requestExit = true) }
                stopGame()
            }
            EmulationIntent.DismissStatus -> _state.update { it.copy(statusMessage = null) }
            EmulationIntent.ClearExitRequest -> _state.update { it.copy(requestExit = false) }

            EmulationIntent.LifecyclePause -> lifecyclePause()
            EmulationIntent.LifecycleResume -> lifecycleResume()

            EmulationIntent.ShowKeyMapping -> _state.update { it.copy(showKeyMapping = true, showOverlay = false, showGamepadConfig = false) }
            EmulationIntent.HideKeyMapping -> _state.update { it.copy(showKeyMapping = false, showOverlay = true) }
            EmulationIntent.ShowGamepadConfig -> _state.update { it.copy(showGamepadConfig = true, showOverlay = false) }
            EmulationIntent.HideGamepadConfig -> _state.update { it.copy(showGamepadConfig = false, showOverlay = true) }

            EmulationIntent.DismissAchievement -> _state.update { it.copy(achievementEvent = null) }

            is EmulationIntent.SecondaryDisplayAvailabilityChanged -> onSecondaryDisplayAvailabilityChanged(intent.available)

            EmulationIntent.ShowNetplayLeaveConfirm -> _state.update { it.copy(netplayShowLeaveConfirm = true) }
            EmulationIntent.DismissNetplayLeaveConfirm -> _state.update { it.copy(netplayShowLeaveConfirm = false) }
            EmulationIntent.ConfirmNetplayLeave -> {
                _state.update { it.copy(netplayShowLeaveConfirm = false, requestExit = true) }
                stopGame()
            }

            // Challenge intents
            EmulationIntent.CreateChallenge -> challengeManager.initChallengeCreation { pauseGame() }
            is EmulationIntent.SubmitChallenge -> challengeManager.submitChallenge(intent.name, intent.description, intent.type, intent.difficulty) { resumeGame() }
            EmulationIntent.DismissChallengeCreation -> challengeManager.dismissChallengeCreation { resumeGame() }
            EmulationIntent.CompleteChallenge -> challengeManager.completeChallenge { pauseGame() }
            EmulationIntent.RestartChallenge -> challengeManager.restartChallenge { resumeGame() }
            EmulationIntent.ShowGiveUpConfirm -> _state.update { it.copy(showGiveUpConfirm = true) }
            EmulationIntent.DismissGiveUpConfirm -> _state.update { it.copy(showGiveUpConfirm = false) }
            EmulationIntent.ConfirmGiveUp -> challengeManager.giveUpChallenge { stopGame() }
            EmulationIntent.DismissChallengeResult -> _state.update { it.copy(challengeCompletedAttempt = null) }

            // BIOS
            EmulationIntent.DismissMissingBiosDialog -> {
                _state.update { it.copy(showMissingBiosDialog = false, missingBiosFiles = emptyList(), isLoading = false) }
            }
            EmulationIntent.TryAnywayMissingBios -> {
                _state.update { it.copy(showMissingBiosDialog = false) }
                skipBiosCheck = true
                val currentState = _state.value
                startGame(currentState.gameId)
            }

            // Quick-save slots
            EmulationIntent.QuickSave -> {
                if (checkCoreMismatchBeforeSave("quick")) {
                    quickSaveToSlot()
                }
            }
            EmulationIntent.QuickLoad -> quickLoadFromSlot()
            is EmulationIntent.SelectSlot -> _state.update { it.copy(activeSlot = intent.slot) }
            is EmulationIntent.SaveToSlot -> {
                _state.update { it.copy(activeSlot = intent.slot) }
                if (checkCoreMismatchBeforeSave("slot", intent.slot)) {
                    saveManager.saveToSlot(intent.slot)
                }
            }
            is EmulationIntent.LoadFromSlot -> {
                _state.update { it.copy(activeSlot = intent.slot) }
                saveManager.loadFromSlot(intent.slot)
            }

            // Rewind
            EmulationIntent.RewindStep -> rewindStep()
            EmulationIntent.ToggleRewind -> toggleRewindEnabled()

            // Pre-launch sync
            is EmulationIntent.PrepareLaunch -> prepareLaunch(intent.gameId, intent.skipAutoLoad, intent.forceNewSession, intent.sessionId)
            EmulationIntent.PlayWithLocalSave -> playWithLocalSave()
            EmulationIntent.CancelLaunch -> cancelLaunch()

            // Core mismatch
            EmulationIntent.CoreMismatchTryAnyway -> handleCoreMismatchTryAnyway()
            EmulationIntent.CoreMismatchGameSaveOnly -> handleCoreMismatchGameSaveOnly()
            EmulationIntent.CoreMismatchStartFresh -> handleCoreMismatchStartFresh()
            EmulationIntent.ConfirmCoreMismatchSave -> {
                val pendingType = _state.value.pendingSaveType
                val pendingSlot = _state.value.pendingSaveSlot
                _state.update { it.copy(showCoreMismatchSaveDialog = false) }
                when (pendingType) {
                    "auto" -> scope.launch(dispatchers.io) {
                        saveManager.autoSaveOnStop(_state.value.gameId)
                        finishStopGame()
                    }
                    "manual" -> saveManager.saveState()
                    "slot" -> saveManager.saveToSlot(pendingSlot)
                    "quick" -> quickSaveToSlot()
                }
            }
            EmulationIntent.SkipCoreMismatchSave -> {
                val pendingType = _state.value.pendingSaveType
                _state.update { it.copy(showCoreMismatchSaveDialog = false) }
                if (pendingType == "auto") {
                    scope.launch(dispatchers.io) { finishStopGame() }
                }
            }

            // Cheats
            EmulationIntent.ShowCheatBrowser -> _state.update { it.copy(showCheatBrowser = true) }
            EmulationIntent.HideCheatBrowser -> _state.update { it.copy(showCheatBrowser = false) }
            is EmulationIntent.ToggleCheatInGame -> toggleCheatInGame(intent.cheatId, intent.enabled)

            // Secondary screen toast
            EmulationIntent.ClearSecondaryToast -> _state.update { it.copy(secondaryToast = null) }

            // Secondary screen touch control port
            is EmulationIntent.SelectTouchControlPort -> _state.update { it.copy(touchControlPort = intent.port.coerceIn(0, 1)) }

            // Secondary screen control tab
            is EmulationIntent.SelectControlTab -> {
                _state.update { it.copy(selectedControlTab = intent.tab) }
                val consoleId = _state.value.consoleId
                if (consoleId.isNotEmpty()) {
                    preferencesRepository.setControlTab(consoleId, intent.tab.id)
                }
            }

            // #672 core-upgrade decision — Sheet A/B resolution
            EmulationIntent.ResolveCoreDecisionTry -> resolveCoreDecision(RehearsalEntry.Try)
            EmulationIntent.ResolveCoreDecisionKeepNew -> resolveCoreDecision(RehearsalEntry.KeepNew)
            EmulationIntent.ResolveCoreDecisionLockOld -> resolveCoreDecision(RehearsalEntry.LockOld)
            EmulationIntent.ResolveCoreDecisionRemindLater -> resolveCoreDecision(RehearsalEntry.RemindLater)
            EmulationIntent.ResolveCoreDecisionStartFresh -> resolveCoreDecision(RehearsalEntry.StartFresh)

            // #672 PR 3c.ii — rehearsal flow (Sheets C/D + banner)
            EmulationIntent.RehearsalShowConfirmation ->
                _state.update { it.copy(showRehearsalConfirmSheet = true) }
            EmulationIntent.RehearsalDismissConfirmation,
            EmulationIntent.RehearsalContinueLonger ->
                _state.update { it.copy(showRehearsalConfirmSheet = false) }
            EmulationIntent.RehearsalCompletedKeepNew -> rehearsalKeepNew()
            EmulationIntent.RehearsalCompletedLockOld -> rehearsalLockOld()
            is EmulationIntent.RehearsalCrashed ->
                _state.update {
                    it.copy(
                        coreDecision = com.spela.player.presentation.state.CoreDecision.RehearsalCrashed(
                            coreName = intent.coreName,
                            coreDisplayName = intent.coreDisplayName.ifEmpty { intent.coreName },
                        ),
                        showRehearsalConfirmSheet = false,
                    )
                }
            EmulationIntent.RehearsalCrashStartFresh -> rehearsalCrashStartFresh()
            EmulationIntent.RehearsalCrashDismiss -> rehearsalCrashDismiss()
        }
    }

    /**
     * Sheet D more-options — "Just go back — I'll try again later".
     * Closes the sheet without changing core-mode or auto-load state.
     * Clears the crash-recovery sentinel because the user explicitly
     * acknowledged the crash here; leaving it set would re-route them
     * to Sheet D on the next launch, which contradicts the "try again
     * later" intent.
     */
    private fun rehearsalCrashDismiss() {
        saveManager.rehearsalMode = false
        val sessionId = _state.value.sessionId
        _state.update { it.copy(coreDecision = null) }
        scope.launch(dispatchers.io) {
            saveManager.setSessionRehearsalCrashPending(sessionId, pending = false)
        }
    }

    /**
     * Sheet C primary — "Yes, keep the new version". Exits rehearsal
     * mode, clears the banner / sheet, and resumes normal play. The
     * pin itself is not written here; `Phase 3` pin-advancement fires
     * on the first successful save-write on the new core, matching
     * Sheet A's "Keep the new version anyway" behaviour.
     *
     * Also clears the #672 crash-recovery sentinel — the run reached a
     * clean resolution without the process dying, so the next launch
     * must not route to Sheet D.
     */
    private fun rehearsalKeepNew() {
        saveManager.rehearsalMode = false
        val sessionId = _state.value.sessionId
        _state.update {
            it.copy(
                coreDecision = null,
                showRehearsalConfirmSheet = false,
            )
        }
        scope.launch(dispatchers.io) {
            saveManager.setSessionRehearsalCrashPending(sessionId, pending = false)
        }
    }

    /**
     * Sheet C secondary / Sheet D primary — "Lock to the older version".
     * Writes `UserLockedCoreVersion = true` on the server, then (only
     * on success) clears rehearsal mode and re-launches the session so
     * detection picks the pinned binary. Reconstructs StartGame from
     * the current state snapshot because the rehearsal path already
     * consumed `pendingCoreDecisionStart`.
     *
     * Failure handling: if the server write fails we keep the user in
     * rehearsal mode — saves stay paused, banner/Sheet state is
     * preserved-ish (the sheet closes so the error snackbar is
     * visible) — and surface a retry-able error. Re-firing StartGame
     * anyway would silently leave the user on the new core while the
     * UI told them they locked to the old one, which is the opposite
     * of what they asked for.
     */
    private fun rehearsalLockOld() {
        val snapshot = _state.value
        // Close the sheet so the snackbar can surface, but do NOT yet
        // clear `coreDecision` or `rehearsalMode` — those rollback
        // states matter if the server write fails below.
        _state.update { it.copy(showRehearsalConfirmSheet = false) }
        val sessionId = snapshot.sessionId
        scope.launch(dispatchers.io) {
            val locked = if (!sessionId.isNullOrEmpty()) {
                saveManager.setSessionCoreLock(sessionId, locked = true)
            } else {
                false
            }
            if (!locked) {
                withContext(dispatchers.main) {
                    _state.update {
                        it.copy(
                            statusMessage = "Couldn't save the lock — tap 'No, lock to the older version' again to retry.",
                        )
                    }
                }
                return@launch
            }
            // Write succeeded — now safe to exit rehearsal + relaunch.
            saveManager.rehearsalMode = false
            // Clear the crash-recovery sentinel: the user resolved the
            // rehearsal cleanly. Losing the clear would cause the next
            // launch to spuriously route to Sheet D, so we fire-and-
            // continue (best effort) but don't block the relaunch.
            saveManager.setSessionRehearsalCrashPending(sessionId, pending = false)
            withContext(dispatchers.main) {
                _state.update { it.copy(coreDecision = null) }
                // skipCoreDecisionPrompt=true is defence in depth —
                // detection will also short-circuit because
                // UserLockedCoreVersion is now true on the server.
                onIntent(
                    EmulationIntent.StartGame(
                        gameId = snapshot.gameId,
                        sessionId = sessionId,
                        skipCoreDecisionPrompt = true,
                    ),
                )
            }
        }
    }

    /**
     * Sheet D secondary — "Start fresh on the new version". Writes
     * `AutoLoadSuppressed = true` so future launches also skip the
     * stale save, then re-launches with `skipAutoLoad = true` for the
     * current run. Unlike `rehearsalLockOld`, this path proceeds even
     * if the server write fails: the local `skipAutoLoad` override
     * already addresses the immediate crash-loop risk, so the user
     * lands on a playable session either way. A retry-able error
     * surfaces when persistence fails.
     */
    private fun rehearsalCrashStartFresh() {
        saveManager.rehearsalMode = false
        val snapshot = _state.value
        _state.update {
            it.copy(
                coreDecision = null,
                showRehearsalConfirmSheet = false,
            )
        }
        val sessionId = snapshot.sessionId
        scope.launch(dispatchers.io) {
            val persisted = if (!sessionId.isNullOrEmpty()) {
                saveManager.setSessionAutoLoadSuppressed(sessionId, suppressed = true)
            } else {
                true // no session — nothing to persist
            }
            // Clear the crash-recovery sentinel: the user resolved the
            // rehearsal cleanly (even if through a crash), so we don't
            // want the next launch to re-route to Sheet D.
            saveManager.setSessionRehearsalCrashPending(sessionId, pending = false)
            if (!persisted) {
                withContext(dispatchers.main) {
                    _state.update {
                        it.copy(
                            statusMessage = "Skipping the save for this run worked, " +
                                "but we couldn't remember that choice — it may re-prompt next time.",
                        )
                    }
                }
            }
            withContext(dispatchers.main) {
                onIntent(
                    EmulationIntent.StartGame(
                        gameId = snapshot.gameId,
                        sessionId = sessionId,
                        skipAutoLoad = true,
                        skipCoreDecisionPrompt = true,
                    ),
                )
            }
        }
    }

    /**
     * Classifies the user's Sheet A/B choice so [resolveCoreDecision]
     * can branch without duplicating side effects for each intent.
     */
    private enum class RehearsalEntry { Try, KeepNew, LockOld, RemindLater, StartFresh }

    /**
     * Applies the side effects of a Sheet A choice (rehearsal mode,
     * server lock flag) and re-fires the stashed StartGame intent
     * with `skipCoreDecisionPrompt = true` so the sheet doesn't
     * reappear immediately. Each branch maps to the spec's
     * "Pin advancement rules" in
     * `design-proposals/core-upgrade-decision-spec.md`.
     *
     * Surfaces a status message when the server-side lock write
     * fails so the user isn't led to believe they locked the session
     * when really the next launch will re-prompt. The launch still
     * proceeds with the pinned binary locally; we optimistically
     * re-attempt the lock on subsequent resolves.
     */
    private fun resolveCoreDecision(entry: RehearsalEntry) {
        val pending = pendingCoreDecisionStart ?: return
        val stashedDecision = _state.value.coreDecision
        pendingCoreDecisionStart = null
        _state.update { it.copy(coreDecision = null) }

        scope.launch(dispatchers.io) {
            var refiredIntent = pending.copy(skipCoreDecisionPrompt = true)
            when (entry) {
                RehearsalEntry.Try -> {
                    saveManager.rehearsalMode = true
                    // #672 PR 3c.iii — set the crash-recovery sentinel
                    // BEFORE the new core gets a chance to crash. If
                    // the player process dies mid-rehearsal, this flag
                    // surviving on the server tells the next launch to
                    // route the user to Sheet D. Failure to write is
                    // logged but doesn't block the rehearsal — losing
                    // the sentinel only degrades crash-recovery, not
                    // the active session.
                    saveManager.setSessionRehearsalCrashPending(pending.sessionId, pending = true)
                    // Build a RehearsalPrompt from whatever Sheet A was
                    // showing so the banner renders the same core name
                    // the user just acknowledged. UpgradeAvailable →
                    // NEW core is running. PinPruned reaches Try via
                    // the same intent but the old binary is gone, so
                    // we're also on the new/latest core.
                    pendingRehearsalPrompt = when (val d = stashedDecision) {
                        is com.spela.player.presentation.state.CoreDecision.UpgradeAvailable ->
                            com.spela.player.presentation.state.CoreDecision.RehearsalPrompt(
                                coreName = d.coreName,
                                coreDisplayName = d.coreDisplayName,
                                usingNewSha = true,
                            )
                        is com.spela.player.presentation.state.CoreDecision.PinPruned ->
                            com.spela.player.presentation.state.CoreDecision.RehearsalPrompt(
                                coreName = d.coreName,
                                coreDisplayName = d.coreDisplayName,
                                usingNewSha = true,
                            )
                        else -> null
                    }
                }
                RehearsalEntry.KeepNew,
                RehearsalEntry.RemindLater -> {
                    saveManager.rehearsalMode = false
                }
                RehearsalEntry.LockOld -> {
                    saveManager.rehearsalMode = false
                    val locked = saveManager.setSessionCoreLock(
                        pending.sessionId,
                        locked = true,
                    )
                    if (!locked) {
                        withContext(dispatchers.main) {
                            _state.update {
                                it.copy(
                                    statusMessage = "Couldn't save the lock — we'll try again next time.",
                                )
                            }
                        }
                    }
                }
                RehearsalEntry.StartFresh -> {
                    saveManager.rehearsalMode = false
                    saveManager.setSessionAutoLoadSuppressed(
                        pending.sessionId,
                        suppressed = true,
                    )
                    // Locally also skip the auto-load so this launch
                    // doesn't try to restore the stale save state —
                    // the server flag caught on next launch, this
                    // one needs an immediate runtime override.
                    refiredIntent = refiredIntent.copy(skipAutoLoad = true)
                }
            }
            withContext(dispatchers.main) {
                onIntent(refiredIntent)
            }
        }
    }

    private fun startGame(
        gameId: String,
        sharedSessionId: String? = null,
        turnToken: String? = null,
        netplaySessionId: String? = null,
        netplayLocalPort: Int = 0,
        netplayInputDelay: Int = 3,
        netplayIsHost: Boolean = false,
        challengeId: String? = null,
        challengeSaveDataArg: ByteArray? = null,
        skipAutoLoad: Boolean = false,
        forceNewSession: Boolean = false,
        sessionId: String? = null,
        skipCoreDecisionPrompt: Boolean = false,
    ) {
        saveManager.currentSessionId = sessionId
        // Consume the pending rehearsal prompt (set by resolveCoreDecision
        // when the user picked Sheet A's "Try"). Consumed here rather
        // than left on the field so a subsequent non-rehearsal launch
        // doesn't inherit a stale banner.
        val rehearsalPrompt = pendingRehearsalPrompt
        pendingRehearsalPrompt = null
        _state.update {
            it.copy(
                gameId = gameId,
                isLoading = true,
                isPaused = false,
                showOverlay = false,
                showExitConfirm = false,
                sharedSessionId = sharedSessionId,
                turnToken = turnToken,
                netplaySessionId = netplaySessionId,
                sessionId = sessionId, // may be updated by ensureSession below
                challengeId = challengeId,
                challengeAttemptId = null,
                challengeElapsedMs = 0,
                challengeCompletedAttempt = null,
                achievementEvent = null,
                achievements = emptyList(),
                achievementProgress = emptyList(),
                achievementTotalPoints = 0,
                achievementsLoading = false,
                sessionAchievementUnlocks = emptyList(),
                error = null,
                statusMessage = null,
                isFastForward = false,
                supportsSaveStates = true,
                isHwRenderEnabled = false,
                secondaryToast = null,
                touchControlPort = 0,
                // RehearsalPrompt survives the state reset so the
                // in-game banner is visible the moment the emulator
                // surface composes. Closes the gap where the user
                // would briefly see a naked emulator without knowing
                // they're in rehearsal mode.
                coreDecision = rehearsalPrompt,
                showRehearsalConfirmSheet = false,
            )
        }

        scope.launch(dispatchers.io) {
            // Ensure any previous emulation is fully stopped before starting a new one.
            // stopGame() runs asynchronously (auto-save, network uploads) — if the user
            // starts a new game before it finishes, the old emulation thread may still be
            // alive, causing a crash when we load a new core/game.
            //
            // Cancel the old stopJob to prevent it from calling stop() later, which
            // would kill the NEW emulation thread and unload the NEW game.
            // Note: the auto-save in stopGame() runs in a separate saveJob that is
            // NOT cancelled here, so save data is preserved across restarts.
            stopJob?.cancel()
            stopJob = null
            libretroController.stop()
            println("[Emulation] Previous emulation stopped (if any)")

            // Fetch user preferences (fallback to defaults on error)
            currentPreferences = preferencesRepository.getPreferences()
                .getOrDefault(UserPreferences())

            // Get game detail for consoleId
            var consoleId = ""
            var consoleName = ""
            getGameDetailUseCase(gameId).onSuccess { detail ->
                consoleId = detail.game.consoleId
                consoleName = detail.game.consoleName
                withContext(dispatchers.main) {
                    _state.update {
                        it.copy(
                            gameTitle = detail.game.title,
                            consoleId = detail.game.consoleId,
                            consoleName = detail.game.consoleName,
                            heroUrl = detail.game.heroUrl,
                            gameDescription = detail.game.description,
                            gameDeveloper = detail.game.developer,
                            gamePublisher = detail.game.publisher,
                            gameReleaseDate = detail.game.releaseDate,
                            gameGenre = detail.game.genre,
                            gameRating = detail.game.igdbCriticsRating,
                            gamePlayers = detail.game.players,
                        )
                    }
                }
            }

            // Auto-create or reuse session for normal play (not shared/netplay/challenge).
            // forceNewSession creates a brand new session (e.g. "New Game"), while
            // skipAutoLoad alone reuses the existing session but skips save state restore.
            if (sharedSessionId == null && netplaySessionId == null && challengeId == null) {
                val resolvedSessionId = saveManager.ensureSession(gameId, sessionId, forceNew = forceNewSession)
                saveManager.currentSessionId = resolvedSessionId
                if (resolvedSessionId != null && resolvedSessionId != sessionId) {
                    withContext(dispatchers.main) {
                        _state.update { it.copy(sessionId = resolvedSessionId) }
                    }
                }
            }

            // Pre-launch BIOS check
            if (biosRepository != null && !skipBiosCheck) {
                val missingFiles = biosRepository.preLaunchBiosCheck(consoleId)
                if (missingFiles.isNotEmpty()) {
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(
                                showMissingBiosDialog = true,
                                missingBiosFiles = missingFiles,
                                missingBiosConsoleName = consoleName,
                                isLoading = false,
                            )
                        }
                    }
                    return@launch
                }
            }
            skipBiosCheck = false

            // Detect dual-screen consoles (Nintendo DS, Nintendo 3DS)
            val lc = consoleId.lowercase()
            val isDualScreen = lc == "nds" || lc == "3ds"
            val splitY = when (lc) {
                "nds" -> 192   // 256×192 top + 256×192 bottom = 256×384
                "3ds" -> 240   // 400×240 top + 320×240 bottom = 400×480
                else -> 0
            }
            val bottomWidth = when (lc) {
                "nds" -> 256   // Same width as top screen
                "3ds" -> 320   // Narrower than top (400px)
                else -> 0
            }
            val bottomOffsetX = when (lc) {
                "nds" -> 0     // No padding
                "3ds" -> 40    // (400 - 320) / 2 = 40px padding each side
                else -> 0
            }
            withContext(dispatchers.main) {
                _state.update {
                    it.copy(
                        isDualScreenConsole = isDualScreen,
                        dualScreenSplitY = splitY,
                        dualScreenBottomWidth = bottomWidth,
                        dualScreenBottomOffsetX = bottomOffsetX,
                    )
                }
            }

            // ScummVM: set port 0 to RETRO_DEVICE_MOUSE for point-and-click input
            if (lc == "scummvm") {
                libretroController.setControllerPortDevice(0, 2) // RETRO_DEVICE_MOUSE = 2
            }

            // Restore persisted control tab for this console
            val savedControlTab = com.spela.player.presentation.state.ControlTab.fromId(
                preferencesRepository.getControlTab(consoleId)
            )
            withContext(dispatchers.main) {
                _state.update { it.copy(selectedControlTab = savedControlTab) }
            }

            // Resolve shader using two-layer system
            val resolvedShader = preferencesRepository.resolveShader(consoleId)

            withContext(dispatchers.main) {
                _state.update {
                    it.copy(
                        showPerformanceOverlay = currentPreferences.showPerformanceOverlay,
                        selectedShader = resolvedShader,
                        defaultSecondScreenPage = currentPreferences.defaultSecondScreenPage,
                    )
                }
            }

            // Prepare game and core files. When the session has a
            // pinnedCoreSha256 (#555 Phase 3) we pass it through so the
            // use case can attempt a versioned core download first.
            // autoUpdateCoresEnabled gates the unpinned cache-invalidation
            // check (#555 Phase 2 opt-out). userLockedCoreVersion +
            // sessionHasSaves + skipCoreDecisionPrompt drive the #672
            // core-upgrade decision flow — see decision handling block
            // below the fold().
            val flags = saveManager.coreDecisionFlagsFor(saveManager.currentSessionId)
            val pinnedSha = flags?.pinnedCoreSha256
            val userLocked = flags?.userLockedCoreVersion ?: false
            val hasSaves = saveManager.sessionSaveCount(saveManager.currentSessionId) > 0
            prepareGameUseCase(
                gameId,
                pinnedSha,
                autoUpdateCoresEnabled = currentPreferences.autoUpdateCoresEnabled,
                userLockedCoreVersion = userLocked,
                sessionHasSaves = hasSaves,
                skipCoreDecisionPrompt = skipCoreDecisionPrompt,
            ).fold(
                onSuccess = { prepared ->
                    val gamePath = prepared.gamePath
                    val corePath = prepared.corePath
                    // #672 core-upgrade decision gate. When the use
                    // case reports UpgradeAvailable we stash the
                    // originating StartGame intent, surface the sheet
                    // via `state.coreDecision`, and return BEFORE
                    // touching the emulator. Resolution intents below
                    // clear the stash and re-fire StartGame with
                    // `skipCoreDecisionPrompt = true`.
                    // #672 per spec §"Edge cases": netplay, shared
                    // session, and challenge runs MUST skip the sheet.
                    // Netplay requires binary parity across peers and
                    // challenges lock their own core at creation; both
                    // have their own core-mismatch negotiation.
                    val suppressForSpecialMode =
                        netplaySessionId != null
                            || sharedSessionId != null
                            || challengeId != null
                    if (prepared.decisionKind == com.spela.player.domain.usecase.DecisionKind.PinPruned
                        && !suppressForSpecialMode
                    ) {
                        pendingCoreDecisionStart = EmulationIntent.StartGame(
                            gameId = gameId,
                            sharedSessionId = sharedSessionId,
                            turnToken = turnToken,
                            netplaySessionId = netplaySessionId,
                            netplayLocalPort = netplayLocalPort,
                            netplayInputDelay = netplayInputDelay,
                            netplayIsHost = netplayIsHost,
                            challengeId = challengeId,
                            challengeSaveData = challengeSaveDataArg,
                            skipAutoLoad = skipAutoLoad,
                            forceNewSession = forceNewSession,
                            sessionId = saveManager.currentSessionId,
                        )
                        withContext(dispatchers.main) {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    showCoreMismatchDialog = false,
                                    coreDecision = com.spela.player.presentation.state.CoreDecision.PinPruned(
                                        coreName = prepared.coreName,
                                        coreDisplayName = prepared.coreDisplayName.ifEmpty { prepared.coreName },
                                        gameTitle = it.gameTitle.ifEmpty { gameId },
                                        prunedSha = pinnedSha ?: "",
                                    ),
                                )
                            }
                        }
                        return@fold
                    }
                    if (prepared.decisionKind == com.spela.player.domain.usecase.DecisionKind.UpgradeAvailable
                        && !suppressForSpecialMode
                    ) {
                        pendingCoreDecisionStart = EmulationIntent.StartGame(
                            gameId = gameId,
                            sharedSessionId = sharedSessionId,
                            turnToken = turnToken,
                            netplaySessionId = netplaySessionId,
                            netplayLocalPort = netplayLocalPort,
                            netplayInputDelay = netplayInputDelay,
                            netplayIsHost = netplayIsHost,
                            challengeId = challengeId,
                            challengeSaveData = challengeSaveDataArg,
                            skipAutoLoad = skipAutoLoad,
                            forceNewSession = forceNewSession,
                            sessionId = saveManager.currentSessionId,
                        )
                        withContext(dispatchers.main) {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    // Suppress any post-load dialog that
                                    // might otherwise stack on top of
                                    // Sheet A — Sheet A is the pre-load
                                    // decision; CoreMismatchDialog only
                                    // fires after auto-load fails.
                                    showCoreMismatchDialog = false,
                                    coreDecision = com.spela.player.presentation.state.CoreDecision.UpgradeAvailable(
                                        coreName = prepared.coreName,
                                        coreDisplayName = prepared.coreDisplayName.ifEmpty { prepared.coreName },
                                        consoleName = it.consoleName,
                                        gameTitle = it.gameTitle.ifEmpty { gameId },
                                        oldSha = pinnedSha ?: "",
                                        newSha = prepared.serverCoreSha,
                                        lastPlayedAt = null,
                                    ),
                                )
                            }
                        }
                        return@fold
                    }
                    // Surface the fallback warning (non-blocking) if the
                    // pinned binary was pruned and we fell back to latest.
                    if (prepared.coreVersionWarning != null) {
                        withContext(dispatchers.main) {
                            _state.update { it.copy(coreVersionWarning = prepared.coreVersionWarning) }
                        }
                    }
                    try {
                        // Set core options BEFORE loadCore so they're already in
                        // the variable store when SET_VARIABLES runs during init.
                        if (isDualScreen) {
                            libretroController.setCoreVariable("desmume_screens_layout", "vertical")
                            libretroController.setCoreVariable("desmume_screens_gap", "0")
                            libretroController.setCoreVariable("desmume_pointer_type", "touch")
                            libretroController.setCoreVariable("desmume_pointer_mouse", "enabled")
                        }

                        libretroController.loadCore(corePath)

                        // On Android emulators (SwiftShader), paraLLEl-RDP Vulkan crashes
                        if (com.spela.player.util.isEmulator() && corePath.contains("mupen64plus")) {
                            libretroController.setCoreVariable("mupen64plus-rdp-plugin", "angrylion")
                            libretroController.setCoreVariable("mupen64plus-rsp-plugin", "parallel")
                            libretroController.setCoreVariable("mupen64plus-angrylion-multithread", "all threads")
                            libretroController.setCoreVariable("mupen64plus-angrylion-sync", "Low")
                        }

                        println("[Emulation] Loading game: path=$gamePath core=$corePath")
                        libretroController.loadGame(gamePath)

                        // Set HW render state early so Compose creates the
                        // VulkanEmulationSurface before emulation starts.
                        // Without this, GLES HW render cores (GLideN64) produce
                        // frames that can't be displayed until the surface exists.
                        val hwRender = libretroController.isHwRenderEnabled()
                        println("[Emulation] isHwRenderEnabled=$hwRender after loadGame")
                        if (hwRender) {
                            withContext(dispatchers.main) {
                                _state.update { it.copy(isHwRenderEnabled = true) }
                                println("[Emulation] State updated: isHwRenderEnabled=true")
                            }
                        }

                        // Load SRAM (save data) before starting emulation
                        saveManager.loadSramOnStart(gameId)

                        // Apply enabled cheats to core
                        applyCheatsToCore(gameId)

                        // Store the core name for challenge creation and save states
                        val resolvedCoreName = corePath.substringAfterLast('/').substringBeforeLast('.')
                        challengeManager.currentCoreName = resolvedCoreName
                        saveManager.currentCoreName = resolvedCoreName

                        // Update session with current core name (best effort)
                        saveManager.updateSessionCoreName()

                        // Challenge mode: load challenge save state and start attempt
                        if (challengeId != null) {
                            challengeManager.loadChallengeSave(challengeId, challengeSaveDataArg)
                        }
                        // Try to load auto-save: in shared session mode, download shared session auto-save
                        else if (sharedSessionId != null) {
                            netplayManager.loadSharedSessionSave(sharedSessionId)
                        } else if (currentPreferences.autoLoadSaveEnabled && !skipAutoLoad && !hwRender) {
                            // For non-HW cores, load save state immediately.
                            // HW render cores (e.g. Dolphin) boot asynchronously — their
                            // GPU thread isn't ready for retro_unserialize yet. Deferred below.
                            handleAutoLoadResult(saveManager.autoLoadSaveState(gameId))
                        }

                        // Set up netplay transport if in netplay mode
                        if (netplaySessionId != null) {
                            netplayManager.setupNetplayTransport(netplaySessionId, netplayLocalPort, netplayInputDelay, netplayIsHost)
                        }

                        libretroController.start()
                        // Don't probe save state support immediately — some cores (e.g. Dolphin)
                        // boot asynchronously and crash if retro_serialize_size is called too early.
                        // Default to true, then re-check after the core has had time to initialize.
                        withContext(dispatchers.main) {
                            _state.update { it.copy(isRunning = true, isLoading = false, supportsSaveStates = true, sessionElapsedSeconds = 0, isHwRenderEnabled = hwRender) }
                        }
                        // Show secondary display as soon as possible — must be before
                        // the 3-second delay below, otherwise the display stays blank
                        // until achievements/heartbeats finish initializing.
                        showSecondaryDisplayIfAvailable()

                        // Populate save slot thumbnails for the secondary screen
                        saveManager.refreshSaveSlots()

                        // Re-check save state support after core has run a few frames.
                        // Skip for GL HW render cores (e.g. PPSSPP) — calling
                        // nativeSerializeSize triggers GL calls that crash on the
                        // IO dispatcher thread. Default of true (set above) is fine.
                        println("[Emulation] Starting 3-second delay for HW render init")
                        kotlinx.coroutines.delay(3000)
                        println("[Emulation] 3-second delay completed, checking save state support")
                        val saveStatesSupported = if (hwRender) true else libretroController.supportsSaveStates()
                        println("[Emulation] saveStatesSupported=$saveStatesSupported")
                        withContext(dispatchers.main) {
                            _state.update { it.copy(supportsSaveStates = saveStatesSupported) }
                        }

                        // Deferred auto-load for HW render cores (e.g. Dolphin).
                        // These cores boot asynchronously and crash if retro_unserialize
                        // is called before their GPU thread is fully initialized.
                        println("[Emulation] Deferred auto-load check: hwRender=$hwRender autoLoad=${currentPreferences.autoLoadSaveEnabled} skipAutoLoad=$skipAutoLoad challengeId=$challengeId sharedSessionId=$sharedSessionId")
                        if (hwRender && currentPreferences.autoLoadSaveEnabled && !skipAutoLoad
                            && challengeId == null && sharedSessionId == null
                        ) {
                            handleAutoLoadResult(saveManager.autoLoadSaveState(gameId))
                        }

                        // Initialize achievements if RA is linked (skip for netplay)
                        if (netplaySessionId == null) {
                            initAchievements(gameId)
                        }

                        // Start play-time heartbeat for online presence
                        presenceService.startHeartbeat(gameId)

                        // Start shared session heartbeat if in shared session mode
                        if (sharedSessionId != null) {
                            netplayManager.startSharedSessionHeartbeat(sharedSessionId)
                        }

                        // Start FPS tracking and session timer
                        trackPerformance()
                        startSessionTimer()

                        // Start challenge timer if in challenge mode
                        if (challengeId != null) {
                            challengeManager.startChallengeTimer()
                        }
                    } catch (e: Exception) {
                        val errorMsg = if (_state.value.missingBiosFiles.isNotEmpty()) {
                            "Emulation failed -- this is likely because required BIOS files are missing"
                        } else {
                            "Failed to start emulation: ${e.message}"
                        }
                        withContext(dispatchers.main) {
                            _state.update {
                                it.copy(error = errorMsg, isLoading = false)
                            }
                        }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(error = "Failed to prepare game: ${error.message}", isLoading = false)
                        }
                    }
                },
            )
        }
    }

    private fun prepareLaunch(gameId: String, skipAutoLoad: Boolean, forceNewSession: Boolean = false, sessionId: String? = null) {
        pendingLaunch = PendingLaunch(gameId, skipAutoLoad, forceNewSession, sessionId)
        scope.launch(dispatchers.io) {
            _syncState.update { null }
            val pending = pendingLaunch ?: return@launch
            pendingLaunch = null
            _launchReady.emit(pending)
        }
    }

    private fun playWithLocalSave() {
        val pending = pendingLaunch ?: return
        pendingLaunch = null
        _syncState.update { null }
        scope.launch(dispatchers.io) {
            _launchReady.emit(pending)
        }
    }

    private fun cancelLaunch() {
        pendingLaunch = null
        _syncState.update { null }
    }

    private fun pauseGame() {
        libretroController.pause()
        presenceService.paused = true
        _state.update { it.copy(isPaused = true) }
    }

    private fun resumeGame() {
        libretroController.resume()
        presenceService.paused = false
        _state.update { it.copy(isPaused = false) }
    }

    private fun lifecyclePause() {
        val current = _state.value
        if (current.isRunning && !current.isPaused) {
            libretroController.pause()
            presenceService.paused = true
            _state.update { it.copy(isLifecyclePaused = true, isPaused = true) }
        }
    }

    private fun lifecycleResume() {
        val current = _state.value
        if (current.isLifecyclePaused) {
            _state.update { it.copy(isLifecyclePaused = false) }
            // Only resume emulation if it wasn't user-paused before lifecycle pause
            libretroController.resume()
            presenceService.paused = false
            _state.update { it.copy(isPaused = false) }
        }
    }

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = scope.launch(dispatchers.default) {
            while (isActive) {
                delay(1000)
                val current = _state.value
                if (!current.isPaused) {
                    withContext(dispatchers.main) {
                        _state.update {
                            val newElapsed = it.sessionElapsedSeconds + 1
                            it.copy(
                                sessionElapsedSeconds = newElapsed,
                                netplayPauseElapsedSeconds = 0,
                                // 15-minute session expiration for netplay (AC-12)
                                netplaySessionExpired = it.isNetplayMode && newElapsed >= 900,
                            )
                        }
                    }
                } else if (current.isNetplayMode) {
                    // Track how long netplay has been paused (for AC-8 5-min timeout)
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(netplayPauseElapsedSeconds = it.netplayPauseElapsedSeconds + 1)
                        }
                    }
                }
            }
        }
    }

    private fun stopGame() {
        println("[Emulation] stopGame() called")
        val stoppingGameId = _state.value.gameId
        // Set sync state before navigating so GameDetail sees it immediately
        if (stoppingGameId.isNotEmpty()) {
            _syncState.update { GameSyncState(stoppingGameId, "Uploading save\u2026") }
        }
        // Dismiss secondary display immediately on the main thread, before async save operations
        dismissSecondaryDisplay()
        sessionTimerJob?.cancel()
        sessionTimerJob = null
        challengeManager.cleanup()
        netplayManager.cleanup()
        presenceService.stopHeartbeat()
        stopJob = scope.launch(dispatchers.io) {
            val currentState = _state.value
            val sharedSessionId = currentState.sharedSessionId
            val turnToken = currentState.turnToken

            // Save before stopping. autoSaveOnStop serializes synchronously (fast,
            // game is paused) then fires off the upload in the background. This means
            // stopJob completes quickly even if the upload is slow, so startGame()
            // won't hang if it cancels this job during a restart.
            if (sharedSessionId != null && turnToken != null) {
                netplayManager.saveSharedSessionOnStop(sharedSessionId, turnToken)
            } else if (currentPreferences.autoSaveEnabled && !currentState.isChallengeMode) {
                if (currentState.isCoreMismatched) {
                    // SRAM is saved below in finishStopGame().
                    // Show dialog and pause the stop flow — the dialog handlers will complete it.
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(
                                showCoreMismatchSaveDialog = true,
                                pendingSaveType = "auto",
                            )
                        }
                    }
                    return@launch // Stop flow pauses here; dialog handlers complete it
                } else {
                    saveManager.autoSaveOnStop(currentState.gameId)
                }
            }

            finishStopGame()
        }
    }

    /**
     * Completes the stop-game flow after auto-save decision has been made.
     * Saves SRAM, deinitializes achievements, stops the core, and resets state.
     * Called from both the normal stopGame path and the core-mismatch dialog handlers.
     */
    private suspend fun finishStopGame() {
        val currentState = _state.value
        val stoppingGameId = currentState.gameId

        // Save SRAM before stopping (best effort) — this is the upload the sync
        // status refers to. Clear sync state once it completes.
        // Timeout after 15 seconds to prevent stuck "Uploading save…" indicator.
        try {
            kotlinx.coroutines.withTimeout(15_000L) {
                saveManager.saveSramOnStop(currentState.gameId)
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            println("[Emulation] SRAM upload timed out after 15s for game ${currentState.gameId}")
        } catch (_: Exception) {
            println("[Emulation] SRAM upload failed for game ${currentState.gameId}")
        }
        _syncState.update { current ->
            if (current?.gameId == stoppingGameId) null else current
        }

        try {
            achievementsController.deinit()
        } catch (_: Exception) {
            // Best effort
        }

        libretroController.stop()
        withContext(dispatchers.main) {
            _state.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    isLifecyclePaused = false,
                    fps = 0f,
                    frameTime = 0f,
                    isHardcoreMode = false,
                    showExitConfirm = false,
                    showOverlay = false,
                    secondaryDisplayActive = false,
                    isDualScreenConsole = false,
                    dualScreenSplitY = 0,
                    dualScreenBottomWidth = 0,
                    dualScreenBottomOffsetX = 0,
                    sharedSessionId = null,
                    turnToken = null,
                    netplaySessionId = null,
                    netplayPeerUsername = null,
                    netplayPeerLatencyMs = 0,
                    netplayPeerDisconnected = false,
                    netplayPausedByUsername = null,
                    netplayShowLeaveConfirm = false,
                    netplayPauseElapsedSeconds = 0,
                    netplaySessionExpired = false,
                    challengeId = null,
                    challengeAttemptId = null,
                    challengeElapsedMs = 0,
                    showChallengeCreation = false,
                    isCreatingChallenge = false,
                    challengeCreationSuccess = false,
                    showGiveUpConfirm = false,
                    challengeCompletedAttempt = null,
                    sessionId = null,
                    consoleColorTheme = null,
                    heroUrl = null,
                    gameDescription = null,
                    gameDeveloper = null,
                    gamePublisher = null,
                    gameReleaseDate = null,
                    gameGenre = null,
                    gameRating = 0.0,
                    gamePlayers = 0,
                    showCoreMismatchDialog = false,
                    coreMismatchSaveCoreName = "",
                    coreMismatchCurrentCoreName = "",
                    hasCheats = false,
                    enabledCheatCount = 0,
                    showCheatBrowser = false,
                    cheats = emptyList(),
                    achievements = emptyList(),
                    achievementProgress = emptyList(),
                    achievementTotalPoints = 0,
                    achievementsLoading = false,
                    sessionAchievementUnlocks = emptyList(),
                    achievementEvent = null,
                    secondaryToast = null,
                    touchControlPort = 0,
                )
            }
            saveManager.currentSessionId = null
        }
    }

    /** Returns true if save should proceed, false if warning dialog was shown. */
    private fun checkCoreMismatchBeforeSave(saveType: String, slot: Int = 0): Boolean {
        if (_state.value.isCoreMismatched) {
            _state.update {
                it.copy(
                    showCoreMismatchSaveDialog = true,
                    pendingSaveType = saveType,
                    pendingSaveSlot = slot,
                )
            }
            return false
        }
        return true
    }

    /**
     * Handles the result of an auto-load attempt.
     * On core mismatch, pauses emulation and shows a dialog.
     */
    private suspend fun handleAutoLoadResult(result: AutoLoadResult) {
        when (result) {
            is AutoLoadResult.Loaded -> {
                // Already unserialized by SaveManager — nothing else to do
            }
            is AutoLoadResult.CoreMismatch -> {
                println("[Emulation] Core mismatch detected: save='${result.saveCoreName}' current='${result.currentCoreName}'")
                withContext(dispatchers.main) {
                    _state.update {
                        it.copy(
                            showCoreMismatchDialog = true,
                            coreMismatchSaveCoreName = result.saveCoreName,
                            coreMismatchCurrentCoreName = result.currentCoreName,
                            isCoreMismatched = true,
                            mismatchedOriginalCore = result.saveCoreName,
                        )
                    }
                }
            }
            is AutoLoadResult.NoSave -> {
                // No save to load — game starts fresh
            }
            is AutoLoadResult.Error -> {
                println("[Emulation] Auto-load error: ${result.message}")
            }
        }
    }

    private fun handleCoreMismatchTryAnyway() {
        _state.update { it.copy(showCoreMismatchDialog = false) }
        scope.launch(dispatchers.io) {
            val success = saveManager.forceAutoLoadSaveState()
            val message = if (success) {
                "Save state loaded (compatibility not guaranteed)"
            } else {
                "Failed to load save state \u2014 starting without it"
            }
            withContext(dispatchers.main) {
                _state.update { it.copy(statusMessage = message) }
            }
        }
    }

    private fun handleCoreMismatchGameSaveOnly() {
        // SRAM was already loaded before the auto-save check, so just dismiss the dialog.
        // The game will start from the title screen with in-game save progress intact.
        _state.update { it.copy(showCoreMismatchDialog = false) }
    }

    private fun handleCoreMismatchStartFresh() {
        // Dismiss dialog and clear SRAM so the game truly starts fresh with no
        // in-game save data. This is destructive — the user chose to discard both
        // the save state AND the in-game save progress.
        _state.update { it.copy(showCoreMismatchDialog = false) }
        scope.launch(dispatchers.io) {
            libretroController.setSRAM(ByteArray(0))
        }
    }

    private fun applyCheatsToCore(gameId: String) {
        val repo = cheatRepository ?: return
        val cheats = repo.getEnabledCheats(gameId)
        libretroController.cheatReset()
        cheats.forEach { cheat ->
            libretroController.cheatSet(cheat.index, true, cheat.code)
        }
        scope.launch(dispatchers.main) {
            _state.update { it.copy(hasCheats = cheats.isNotEmpty(), enabledCheatCount = cheats.size) }
        }
    }

    private fun toggleCheatInGame(cheatId: String, enabled: Boolean) {
        val repo = cheatRepository ?: return
        val gameId = _state.value.gameId
        scope.launch(dispatchers.io) {
            repo.setCheatEnabled(cheatId, enabled)
            val updatedCheats = repo.getEnabledCheats(gameId)
            libretroController.cheatReset()
            updatedCheats.forEach { cheat ->
                libretroController.cheatSet(cheat.index, true, cheat.code)
            }
            val allCheats = repo.getCheatsForGame(gameId).getOrDefault(emptyList())
            withContext(dispatchers.main) {
                _state.update {
                    it.copy(
                        cheats = allCheats,
                        enabledCheatCount = updatedCheats.size,
                        hasCheats = updatedCheats.isNotEmpty(),
                    )
                }
            }
        }
    }

    private fun toggleFastForward() {
        if (_state.value.isChallengeMode) return
        val newState = !_state.value.isFastForward
        libretroController.setFastForward(newState)
        _state.update { it.copy(isFastForward = newState) }
    }

    // Quick-save/load — uses the currently active slot
    private fun quickSaveToSlot() {
        saveManager.saveToSlot(_state.value.activeSlot)
    }

    private fun quickLoadFromSlot() {
        saveManager.loadFromSlot(_state.value.activeSlot)
    }

    // Rewind
    private val rewindBuffer = ArrayDeque<ByteArray>(REWIND_BUFFER_SIZE)
    private var rewindCaptureJob: Job? = null

    private fun toggleRewindEnabled() {
        if (_state.value.isChallengeMode || _state.value.isNetplayMode) return
        val newEnabled = !_state.value.rewindEnabled
        _state.update { it.copy(rewindEnabled = newEnabled) }
        if (newEnabled) {
            startRewindCapture()
        } else {
            rewindCaptureJob?.cancel()
            rewindCaptureJob = null
            rewindBuffer.clear()
        }
    }

    private fun startRewindCapture() {
        rewindCaptureJob?.cancel()
        rewindCaptureJob = scope.launch(dispatchers.default) {
            while (isActive) {
                delay(REWIND_CAPTURE_INTERVAL_MS)
                if (!_state.value.isPaused && _state.value.isRunning && !_state.value.isRewinding) {
                    val frame = libretroController.serialize()
                    if (frame != null) {
                        if (rewindBuffer.size >= REWIND_BUFFER_SIZE) {
                            rewindBuffer.removeFirst()
                        }
                        rewindBuffer.addLast(frame)
                    }
                }
            }
        }
    }

    private fun rewindStep() {
        if (!_state.value.rewindEnabled || rewindBuffer.isEmpty()) return
        val frame = rewindBuffer.removeLastOrNull() ?: return
        libretroController.unserialize(frame)
    }

    private fun initAchievements(gameId: String) {
        // Fetch achievement list + progress from server for the tracker panel
        fetchAchievements(gameId)

        scope.launch(dispatchers.io) {
            achievementsRepository.getRAToken().onSuccess { credentials ->
                achievementsController.init()
                achievementsController.login(credentials.username, credentials.token)

                // Check if hardcore mode is enabled
                achievementsRepository.getRAStatus().onSuccess { status ->
                    if (status.hardcoreEnabled) {
                        achievementsController.setHardcore(true)
                        withContext(dispatchers.main) {
                            _state.update { it.copy(isHardcoreMode = true) }
                        }
                    }
                }

                // Use gameId as hash for now (server can provide a proper hash later)
                achievementsController.loadGame(gameId)

                // Collect achievement events for UI + track session unlocks
                scope.launch(dispatchers.default) {
                    achievementsController.events.collect { event ->
                        withContext(dispatchers.main) {
                            _state.update { state ->
                                // Enrich event with rarity from cached achievements
                                val enrichedEvent = if (event.achievementId != 0L) {
                                    val cached = state.achievements.find { it.id == event.achievementId }
                                    if (cached != null) {
                                        event.copy(rarityPercent = cached.rarityPercent)
                                    } else event
                                } else event
                                val newState = state.copy(achievementEvent = enrichedEvent)
                                if (event.type == AchievementEventType.ACHIEVEMENT_TRIGGERED) {
                                    handleAchievementTriggered(newState, event.title, event.points)
                                } else {
                                    newState
                                }
                            }
                        }
                    }
                }
            }
            // If getRAToken fails, RA is not linked — silently skip
        }
    }

    /**
     * Fetches achievement list and progress from the server API.
     * Updates state with achievements data for the tracker panel on the secondary screen.
     */
    private fun fetchAchievements(gameId: String) {
        val repo = gameStatsRepository ?: return
        scope.launch(dispatchers.io) {
            withContext(dispatchers.main) {
                _state.update { it.copy(achievementsLoading = true) }
            }
            try {
                val achievementsResult = repo.getGameAchievements(gameId)
                val progressResult = repo.getAchievementProgress(gameId)

                val achievements = achievementsResult.getOrDefault(emptyList())
                val progress = progressResult.getOrDefault(emptyList())
                val totalPoints = achievements.sumOf { it.points }

                withContext(dispatchers.main) {
                    _state.update {
                        it.copy(
                            achievements = achievements,
                            achievementProgress = progress,
                            achievementTotalPoints = totalPoints,
                            achievementsLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                println("[Emulation] Failed to fetch achievements: ${e.message}")
                withContext(dispatchers.main) {
                    _state.update { it.copy(achievementsLoading = false) }
                }
            }
        }
    }

    /**
     * Handles an ACHIEVEMENT_TRIGGERED event by updating the local progress
     * (marking the achievement as unlocked) and adding it to the session feed.
     */
    private fun handleAchievementTriggered(
        state: EmulationState,
        title: String,
        points: Int,
    ): EmulationState {
        val nowMs = Clock.System.now().toEpochMilliseconds()

        // Find the matching achievement by title to get its ID
        val matchedAchievement = state.achievements.find { it.title == title }
        val achievementId = matchedAchievement?.id ?: 0L

        // Add to session unlocks
        val sessionUnlock = SessionAchievementUnlock(
            achievementId = achievementId,
            title = title,
            points = points,
            unlockedAtMs = nowMs,
        )
        val updatedSessionUnlocks = state.sessionAchievementUnlocks + sessionUnlock

        // Update local progress — mark this achievement as unlocked if not already
        val updatedProgress = if (achievementId != 0L && state.achievementProgress.none { it.achievementId == achievementId && it.unlockedAt != null }) {
            state.achievementProgress + com.spela.player.domain.model.AchievementProgress(
                achievementId = achievementId,
                unlockedAt = Clock.System.now().toString(),
                isHardcore = state.isHardcoreMode,
                playTimeAtUnlock = state.sessionElapsedSeconds,
            )
        } else {
            state.achievementProgress
        }

        return state.copy(
            sessionAchievementUnlocks = updatedSessionUnlocks,
            achievementProgress = updatedProgress,
        )
    }

    private fun trackPerformance() {
        scope.launch(dispatchers.default) {
            libretroController.performanceStats().collect { (fps, frameTime) ->
                withContext(dispatchers.main) {
                    _state.update { it.copy(fps = fps, frameTime = frameTime) }
                }
            }
        }
    }

    private fun onSecondaryDisplayAvailabilityChanged(available: Boolean) {
        if (_state.value.isRunning && available) {
            if (!_state.value.secondaryDisplayActive) {
                secondaryDisplay.show()
                _state.update { it.copy(secondaryDisplayActive = true) }
            }
        } else if (_state.value.secondaryDisplayActive) {
            secondaryDisplay.dismiss()
            _state.update { it.copy(secondaryDisplayActive = false) }
        }
    }

    private fun showSecondaryDisplayIfAvailable() {
        if (secondaryDisplay.isAvailable.value) {
            secondaryDisplay.show()
            _state.update { it.copy(secondaryDisplayActive = true) }
        }
    }

    private fun dismissSecondaryDisplay() {
        secondaryDisplay.dismiss()
        _state.update { it.copy(secondaryDisplayActive = false) }
    }
}

/**
 * Interface for platform-specific libretro core control.
 * Implemented on Android (via JNI/NDK) and Desktop (via JNI).
 */
interface LibretroController {
    fun loadCore(corePath: String)
    fun loadGame(gamePath: String)
    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun supportsSaveStates(): Boolean
    fun serialize(): ByteArray?
    fun unserialize(data: ByteArray): Boolean
    fun setFastForward(enabled: Boolean)
    fun performanceStats(): kotlinx.coroutines.flow.Flow<Pair<Float, Float>>

    /** Set pointer/touch state for the given port (used for DS touch screen). */
    fun setPointer(port: Int, x: Int, y: Int, pressed: Boolean) {}

    /** Set mouse relative movement and button state. */
    fun setMouse(port: Int, dx: Short, dy: Short, left: Boolean, right: Boolean) {}

    /** Set keyboard key state (RETROK_* constants). */
    fun setKeyboardKey(key: Int, pressed: Boolean) {}

    /** Set the input device type for a controller port. */
    fun setControllerPortDevice(port: Int, device: Int) {}

    /** Set audio volume (0.0 = mute, 1.0 = full). */
    fun setVolume(volume: Float) {}

    /** Set a core option variable (e.g. DeSmuME screen layout). */
    fun setCoreVariable(key: String, value: String) {}

    /** Returns true if the loaded core uses HW rendering (OpenGL/Vulkan). */
    fun isHwRenderEnabled(): Boolean = false

    /**
     * Enter netplay lockstep mode. The emulation loop will synchronize inputs
     * with the remote player via the transport layer before advancing each frame.
     *
     * @param transport The netplay transport for sending/receiving inputs
     * @param inputBuffer The shared input buffer for frame synchronization
     * @param localPort The local player's port (0 for host, 1 for client)
     * @param inputDelay Number of frames of input delay for synchronization
     */
    fun setNetplayMode(
        transport: com.spela.player.netplay.NetplayTransport,
        inputBuffer: com.spela.player.netplay.NetplayInputBuffer,
        localPort: Int,
        inputDelay: Int,
    ) {}

    /** Exit netplay mode and return to normal emulation. */
    fun clearNetplayMode() {}

    /** Get the current SRAM data from the running core. */
    fun getSRAM(): ByteArray? = null

    /** Set SRAM data into the running core's memory. */
    fun setSRAM(data: ByteArray): Boolean = false

    /** Reset all active cheats in the running core. */
    fun cheatReset() {}

    /** Set a cheat code in the running core. */
    fun cheatSet(index: Int, enabled: Boolean, code: String) {}
}
