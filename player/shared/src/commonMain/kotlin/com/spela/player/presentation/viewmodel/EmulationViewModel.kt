package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.PresenceService
import com.spela.player.domain.controller.AchievementsController
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.AchievementsRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.domain.repository.RelayRepository
import com.spela.player.domain.usecase.LoadGameStateUseCase
import com.spela.player.domain.usecase.PrepareGameUseCase
import com.spela.player.domain.usecase.SaveGameStateUseCase
import com.spela.player.domain.usecase.GetGameDetailUseCase
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bridges between Compose UI and the platform-specific libretro core.
 * The actual emulation is driven by LibretroCore (platform-specific),
 * this ViewModel manages the lifecycle and state.
 */
class EmulationViewModel(
    private val prepareGameUseCase: PrepareGameUseCase,
    private val saveGameStateUseCase: SaveGameStateUseCase,
    private val loadGameStateUseCase: LoadGameStateUseCase,
    private val getGameDetailUseCase: GetGameDetailUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val achievementsRepository: AchievementsRepository,
    private val achievementsController: AchievementsController,
    private val libretroController: LibretroController,
    private val secondaryDisplay: PlatformSecondaryDisplay,
    private val presenceService: PresenceService,
    private val relayRepository: RelayRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(EmulationState())
    val state: StateFlow<EmulationState> = _state.asStateFlow()

    private var currentPreferences = UserPreferences()
    private var sessionTimerJob: Job? = null
    private var relayHeartbeatJob: Job? = null

    fun onIntent(intent: EmulationIntent) {
        when (intent) {
            is EmulationIntent.StartGame -> startGame(intent.gameId, intent.relayId, intent.turnToken)
            EmulationIntent.PauseGame -> pauseGame()
            EmulationIntent.ResumeGame -> resumeGame()
            EmulationIntent.StopGame -> stopGame()
            EmulationIntent.SaveState -> saveState()
            EmulationIntent.LoadState -> loadState()
            EmulationIntent.ToggleOverlay -> _state.update { it.copy(showOverlay = !it.showOverlay) }
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
                _state.update { it.copy(showExitConfirm = false) }
                stopGame()
            }
            EmulationIntent.DismissStatus -> _state.update { it.copy(statusMessage = null) }
            EmulationIntent.ClearExitRequest -> _state.update { it.copy(requestExit = false) }

            EmulationIntent.ShowKeyMapping -> _state.update { it.copy(showKeyMapping = true, showOverlay = false) }
            EmulationIntent.HideKeyMapping -> _state.update { it.copy(showKeyMapping = false, showOverlay = true) }

            EmulationIntent.DismissAchievement -> _state.update { it.copy(achievementEvent = null) }

            is EmulationIntent.SecondaryDisplayAvailabilityChanged -> onSecondaryDisplayAvailabilityChanged(intent.available)
        }
    }

    private fun startGame(gameId: String, relayId: String? = null, turnToken: String? = null) {
        _state.update {
            it.copy(
                gameId = gameId,
                isLoading = true,
                showOverlay = false,
                showExitConfirm = false,
                relayId = relayId,
                turnToken = turnToken,
                error = null,
                statusMessage = null,
                isFastForward = false,
            )
        }

        scope.launch(dispatchers.io) {
            // Fetch user preferences (fallback to defaults on error)
            currentPreferences = preferencesRepository.getPreferences()
                .getOrDefault(UserPreferences())

            // Get game detail for consoleId
            var consoleId = ""
            getGameDetailUseCase(gameId).onSuccess { detail ->
                withContext(dispatchers.main) {
                    _state.update { it.copy(gameTitle = detail.game.title, consoleId = detail.game.consoleId) }
                }
                consoleId = detail.game.consoleId
            }

            // Detect dual-screen consoles (Nintendo DS)
            val isDualScreen = consoleId.lowercase() == "nds"
            val splitY = if (isDualScreen) 192 else 0
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
                        // Set DS core options before loading
                        if (isDualScreen) {
                            libretroController.setCoreVariable("desmume_screens_layout", "vertical")
                            libretroController.setCoreVariable("desmume_screens_gap", "0")
                        }

                        libretroController.loadCore(corePath)
                        libretroController.loadGame(gamePath)

                        // Try to load auto-save: in relay mode, download relay auto-save
                        if (relayId != null) {
                            relayRepository.downloadRelayAutoSave(relayId).onSuccess { saveData ->
                                libretroController.unserialize(saveData)
                            }
                        } else if (currentPreferences.autoLoadSaveEnabled) {
                            loadGameStateUseCase(gameId).onSuccess { saveData ->
                                libretroController.unserialize(saveData)
                            }
                        }

                        libretroController.start()
                        val saveStatesSupported = libretroController.supportsSaveStates()
                        withContext(dispatchers.main) {
                            _state.update { it.copy(isRunning = true, isLoading = false, supportsSaveStates = saveStatesSupported, sessionElapsedSeconds = 0) }
                        }

                        // Initialize achievements if RA is linked
                        initAchievements(gameId)

                        // Start play-time heartbeat for online presence
                        presenceService.startHeartbeat(gameId)

                        // Start relay heartbeat if in relay mode
                        if (relayId != null) {
                            startRelayHeartbeat(relayId)
                        }

                        // Start FPS tracking and session timer
                        trackPerformance()
                        startSessionTimer()

                        // Show secondary display if available
                        showSecondaryDisplayIfAvailable()
                    } catch (e: Exception) {
                        withContext(dispatchers.main) {
                            _state.update {
                                it.copy(error = "Failed to start emulation: ${e.message}", isLoading = false)
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
                if (!_state.value.isPaused) {
                    withContext(dispatchers.main) {
                        _state.update { it.copy(sessionElapsedSeconds = it.sessionElapsedSeconds + 1) }
                    }
                }
            }
        }
    }

    private fun stopGame() {
        sessionTimerJob?.cancel()
        sessionTimerJob = null
        relayHeartbeatJob?.cancel()
        relayHeartbeatJob = null
        presenceService.stopHeartbeat()
        scope.launch(dispatchers.io) {
            val currentState = _state.value
            val relayId = currentState.relayId
            val turnToken = currentState.turnToken

            // Save before stopping
            if (relayId != null && turnToken != null) {
                // Relay mode: upload auto-save to relay, then release turn
                try {
                    val saveData = libretroController.serialize()
                    if (saveData != null) {
                        relayRepository.uploadRelayAutoSave(relayId, turnToken, saveData)
                    }
                } catch (_: Exception) {
                    // Best effort relay auto-save
                }
                try {
                    relayRepository.releaseTurn(relayId)
                } catch (_: Exception) {
                    // Best effort release turn
                }
            } else if (currentPreferences.autoSaveEnabled) {
                // Normal mode: auto-save to personal saves
                val gameId = currentState.gameId
                try {
                    val saveData = libretroController.serialize()
                    if (saveData != null) {
                        saveGameStateUseCase(gameId, saveData)
                    }
                } catch (_: Exception) {
                    // Best effort auto-save
                }
            }

            try {
                achievementsController.deinit()
            } catch (_: Exception) {
                // Best effort
            }

            dismissSecondaryDisplay()
            libretroController.stop()
            withContext(dispatchers.main) {
                _state.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        fps = 0f,
                        frameTime = 0f,
                        isHardcoreMode = false,
                        secondaryDisplayActive = false,
                        relayId = null,
                        turnToken = null,
                    )
                }
            }
        }
    }

    private fun saveState() {
        if (_state.value.isHardcoreMode) {
            _state.update { it.copy(error = "Save states are disabled in hardcore mode") }
            return
        }
        scope.launch(dispatchers.io) {
            val gameId = _state.value.gameId
            val saveData = libretroController.serialize() ?: return@launch
            saveGameStateUseCase(gameId, saveData).fold(
                onSuccess = {
                    withContext(dispatchers.main) {
                        _state.update { it.copy(statusMessage = "State saved") }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = "Failed to save: ${error.message}") }
                    }
                },
            )
        }
    }

    private fun loadState() {
        if (_state.value.isHardcoreMode) {
            _state.update { it.copy(error = "Save states are disabled in hardcore mode") }
            return
        }
        scope.launch(dispatchers.io) {
            val gameId = _state.value.gameId
            loadGameStateUseCase(gameId).fold(
                onSuccess = { saveData ->
                    libretroController.unserialize(saveData)
                    withContext(dispatchers.main) {
                        _state.update { it.copy(statusMessage = "State loaded") }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = "Failed to load save: ${error.message}") }
                    }
                },
            )
        }
    }

    private fun toggleFastForward() {
        val newState = !_state.value.isFastForward
        libretroController.setFastForward(newState)
        _state.update { it.copy(isFastForward = newState) }
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

    private fun startRelayHeartbeat(relayId: String) {
        relayHeartbeatJob?.cancel()
        relayHeartbeatJob = scope.launch(dispatchers.io) {
            while (isActive) {
                delay(60_000) // 60 seconds
                try {
                    relayRepository.heartbeat(relayId)
                } catch (_: Exception) {
                    // Best effort heartbeat
                }
            }
        }
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
}
