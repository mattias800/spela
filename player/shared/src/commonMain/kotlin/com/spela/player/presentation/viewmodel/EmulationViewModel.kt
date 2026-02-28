package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.PresenceService
import com.spela.player.data.repository.BiosRepository
import com.spela.player.domain.controller.AchievementsController
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.AchievementsRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.PrepareGameUseCase
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.presentation.state.EmulationState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REWIND_BUFFER_SIZE = 300 // ~60 seconds at 5fps capture rate
private const val REWIND_CAPTURE_INTERVAL_MS = 200L // Capture every 200ms (~5 per second)

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
) {
    val state: StateFlow<EmulationState> = _state.asStateFlow()

    private var currentPreferences = UserPreferences()
    private var sessionTimerJob: Job? = null
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
    }

    fun onIntent(intent: EmulationIntent) {
        when (intent) {
            is EmulationIntent.StartGame -> startGame(
                intent.gameId, intent.relayId, intent.turnToken,
                intent.netplaySessionId, intent.netplayLocalPort, intent.netplayInputDelay, intent.netplayIsHost,
                intent.challengeId, intent.challengeSaveData, intent.skipAutoLoad,
            )
            EmulationIntent.PauseGame -> pauseGame()
            EmulationIntent.ResumeGame -> resumeGame()
            EmulationIntent.StopGame -> stopGame()
            EmulationIntent.SaveState -> saveManager.saveState()
            EmulationIntent.LoadState -> saveManager.loadState()
            EmulationIntent.ToggleOverlay -> {
                val wasShowing = _state.value.showOverlay
                _state.update { it.copy(showOverlay = !it.showOverlay) }
                if (wasShowing) resumeGame() else pauseGame()
            }
            EmulationIntent.ToggleFastForward -> toggleFastForward()
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
            EmulationIntent.QuickSave -> quickSaveToSlot()
            EmulationIntent.QuickLoad -> quickLoadFromSlot()
            is EmulationIntent.SelectSlot -> _state.update { it.copy(activeSlot = intent.slot) }

            // Rewind
            EmulationIntent.RewindStep -> rewindStep()
            EmulationIntent.ToggleRewind -> toggleRewindEnabled()
        }
    }

    private fun startGame(
        gameId: String,
        relayId: String? = null,
        turnToken: String? = null,
        netplaySessionId: String? = null,
        netplayLocalPort: Int = 0,
        netplayInputDelay: Int = 3,
        netplayIsHost: Boolean = false,
        challengeId: String? = null,
        challengeSaveDataArg: ByteArray? = null,
        skipAutoLoad: Boolean = false,
    ) {
        _state.update {
            it.copy(
                gameId = gameId,
                isLoading = true,
                showOverlay = false,
                showExitConfirm = false,
                relayId = relayId,
                turnToken = turnToken,
                netplaySessionId = netplaySessionId,
                challengeId = challengeId,
                challengeAttemptId = null,
                challengeElapsedMs = 0,
                challengeCompletedAttempt = null,
                error = null,
                statusMessage = null,
                isFastForward = false,
                supportsSaveStates = true,
            )
        }

        scope.launch(dispatchers.io) {
            // Fetch user preferences (fallback to defaults on error)
            currentPreferences = preferencesRepository.getPreferences()
                .getOrDefault(UserPreferences())

            // Get game detail for consoleId
            var consoleId = ""
            var consoleName = ""
            getGameDetailUseCase(gameId).onSuccess { detail ->
                withContext(dispatchers.main) {
                    _state.update { it.copy(gameTitle = detail.game.title, consoleId = detail.game.consoleId) }
                }
                consoleId = detail.game.consoleId
                consoleName = detail.game.consoleName
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
            withContext(dispatchers.main) {
                _state.update { it.copy(isDualScreenConsole = isDualScreen, dualScreenSplitY = splitY) }
            }

            // Resolve shader using two-layer system
            val resolvedShader = preferencesRepository.resolveShader(consoleId)

            withContext(dispatchers.main) {
                _state.update {
                    it.copy(
                        showPerformanceOverlay = currentPreferences.showPerformanceOverlay,
                        selectedShader = resolvedShader,
                    )
                }
            }

            // Prepare game and core files
            prepareGameUseCase(gameId).fold(
                onSuccess = { (gamePath, corePath) ->
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
                        if (hwRender) {
                            withContext(dispatchers.main) {
                                _state.update { it.copy(isHwRenderEnabled = true) }
                            }
                        }

                        // Load SRAM (save data) before starting emulation
                        saveManager.loadSramOnStart(gameId)

                        // Store the core name for challenge creation
                        challengeManager.currentCoreName = corePath.substringAfterLast('/').substringBeforeLast('.')

                        // Challenge mode: load challenge save state and start attempt
                        if (challengeId != null) {
                            challengeManager.loadChallengeSave(challengeId, challengeSaveDataArg)
                        }
                        // Try to load auto-save: in relay mode, download relay auto-save
                        else if (relayId != null) {
                            netplayManager.loadRelaySave(relayId)
                        } else if (currentPreferences.autoLoadSaveEnabled && !skipAutoLoad && !hwRender) {
                            // For non-HW cores, load save state immediately.
                            // HW render cores (e.g. Dolphin) boot asynchronously — their
                            // GPU thread isn't ready for retro_unserialize yet. Deferred below.
                            saveManager.autoLoadSaveState(gameId)
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

                        // Re-check save state support after core has run a few frames
                        kotlinx.coroutines.delay(3000)
                        val saveStatesSupported = libretroController.supportsSaveStates()
                        withContext(dispatchers.main) {
                            _state.update { it.copy(supportsSaveStates = saveStatesSupported) }
                        }

                        // Deferred auto-load for HW render cores (e.g. Dolphin).
                        // These cores boot asynchronously and crash if retro_unserialize
                        // is called before their GPU thread is fully initialized.
                        if (hwRender && currentPreferences.autoLoadSaveEnabled && !skipAutoLoad
                            && challengeId == null && relayId == null
                        ) {
                            saveManager.autoLoadSaveState(gameId)
                        }

                        // Initialize achievements if RA is linked (skip for netplay)
                        if (netplaySessionId == null) {
                            initAchievements(gameId)
                        }

                        // Start play-time heartbeat for online presence
                        presenceService.startHeartbeat(gameId)

                        // Start relay heartbeat if in relay mode
                        if (relayId != null) {
                            netplayManager.startRelayHeartbeat(relayId)
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

    private fun pauseGame() {
        libretroController.pause()
        _state.update { it.copy(isPaused = true) }
    }

    private fun resumeGame() {
        libretroController.resume()
        _state.update { it.copy(isPaused = false) }
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
        // Dismiss secondary display immediately on the main thread, before async save operations
        dismissSecondaryDisplay()
        sessionTimerJob?.cancel()
        sessionTimerJob = null
        challengeManager.cleanup()
        netplayManager.cleanup()
        presenceService.stopHeartbeat()
        scope.launch(dispatchers.io) {
            val currentState = _state.value
            val relayId = currentState.relayId
            val turnToken = currentState.turnToken

            // Save before stopping
            if (relayId != null && turnToken != null) {
                netplayManager.saveRelayOnStop(relayId, turnToken)
            } else if (currentPreferences.autoSaveEnabled && !currentState.isChallengeMode) {
                saveManager.autoSaveOnStop(currentState.gameId)
            }

            // Save SRAM before stopping (best effort)
            saveManager.saveSramOnStop(currentState.gameId)

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
                        fps = 0f,
                        frameTime = 0f,
                        isHardcoreMode = false,
                        showExitConfirm = false,
                        showOverlay = false,
                        secondaryDisplayActive = false,
                        isDualScreenConsole = false,
                        dualScreenSplitY = 0,
                        relayId = null,
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

    // Quick-save slots
    private fun quickSaveToSlot() {
        if (_state.value.isChallengeMode || _state.value.isNetplayMode) return
        val gameId = _state.value.gameId
        val slot = _state.value.activeSlot
        scope.launch(dispatchers.io) {
            val data = libretroController.serialize() ?: return@launch
            saveManager.saveToSlot(gameId, slot, data).fold(
                onSuccess = {
                    withContext(dispatchers.main) {
                        _state.update { it.copy(statusMessage = "Saved to slot $slot") }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = "Quick-save failed: ${error.message}") }
                    }
                },
            )
        }
    }

    private fun quickLoadFromSlot() {
        if (_state.value.isChallengeMode || _state.value.isNetplayMode) return
        val gameId = _state.value.gameId
        val slot = _state.value.activeSlot
        scope.launch(dispatchers.io) {
            saveManager.loadFromSlot(gameId, slot).fold(
                onSuccess = { data ->
                    libretroController.unserialize(data)
                    withContext(dispatchers.main) {
                        _state.update { it.copy(statusMessage = "Loaded slot $slot") }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = "Quick-load failed: ${error.message}") }
                    }
                },
            )
        }
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

                // Collect achievement events for UI
                scope.launch(dispatchers.default) {
                    achievementsController.events.collect { event ->
                        withContext(dispatchers.main) {
                            _state.update { it.copy(achievementEvent = event) }
                        }
                    }
                }
            }
            // If getRAToken fails, RA is not linked — silently skip
        }
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
}
