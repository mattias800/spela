package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.PresenceService
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.controller.AchievementsController
import com.spela.player.domain.controller.ScreenshotCapture
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.AchievementsRepository
import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.domain.repository.RelayRepository
import com.spela.player.domain.usecase.LoadGameStateUseCase
import com.spela.player.domain.usecase.PrepareGameUseCase
import com.spela.player.domain.usecase.SaveGameStateUseCase
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.netplay.NetplayInputBuffer
import com.spela.player.netplay.NetplaySignaling
import com.spela.player.netplay.NetplayTransport
import com.spela.player.netplay.RemoteInput
import com.spela.player.netplay.WebSocketRelayTransport
import com.spela.player.netplay.ControlMessage
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
import kotlinx.coroutines.flow.takeWhile
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
    private val challengeRepository: ChallengeRepository,
    private val screenshotCapture: ScreenshotCapture?,
    private val apiClient: SpelaApiClient,
    private val engineFactory: io.ktor.client.engine.HttpClientEngineFactory<*>,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(EmulationState())
    val state: StateFlow<EmulationState> = _state.asStateFlow()

    private var currentPreferences = UserPreferences()
    private var sessionTimerJob: Job? = null
    private var challengeTimerJob: Job? = null
    private var challengeSaveData: ByteArray? = null
    private var challengeCreationSaveData: ByteArray? = null
    private var challengeCreationScreenshot: ByteArray? = null
    private var currentCoreName: String = ""
    private var relayHeartbeatJob: Job? = null
    private var netplayTransport: NetplayTransport? = null
    private var netplayInputCollectorJob: Job? = null
    private var netplayControlCollectorJob: Job? = null

    fun onIntent(intent: EmulationIntent) {
        when (intent) {
            is EmulationIntent.StartGame -> startGame(
                intent.gameId, intent.relayId, intent.turnToken,
                intent.netplaySessionId, intent.netplayLocalPort, intent.netplayInputDelay, intent.netplayIsHost,
                intent.challengeId, intent.challengeSaveData,
            )
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

            EmulationIntent.ShowNetplayLeaveConfirm -> _state.update { it.copy(netplayShowLeaveConfirm = true) }
            EmulationIntent.DismissNetplayLeaveConfirm -> _state.update { it.copy(netplayShowLeaveConfirm = false) }
            EmulationIntent.ConfirmNetplayLeave -> {
                _state.update { it.copy(netplayShowLeaveConfirm = false, requestExit = true) }
                stopGame()
            }

            // Challenge intents
            EmulationIntent.CreateChallenge -> initChallengeCreation()
            is EmulationIntent.SubmitChallenge -> submitChallenge(intent.name, intent.description, intent.type, intent.difficulty)
            EmulationIntent.DismissChallengeCreation -> {
                challengeCreationSaveData = null
                challengeCreationScreenshot = null
                _state.update { it.copy(showChallengeCreation = false, challengeCreationSuccess = false) }
                resumeGame()
            }
            EmulationIntent.CompleteChallenge -> completeChallenge()
            EmulationIntent.RestartChallenge -> restartChallenge()
            EmulationIntent.ShowGiveUpConfirm -> _state.update { it.copy(showGiveUpConfirm = true) }
            EmulationIntent.DismissGiveUpConfirm -> _state.update { it.copy(showGiveUpConfirm = false) }
            EmulationIntent.ConfirmGiveUp -> giveUpChallenge()
            EmulationIntent.DismissChallengeResult -> _state.update { it.copy(challengeCompletedAttempt = null) }
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

                        // Store the core name for challenge creation
                        currentCoreName = corePath.substringAfterLast('/').substringBeforeLast('.')

                        // Challenge mode: load challenge save state and start attempt
                        if (challengeId != null) {
                            val saveBytes = challengeSaveDataArg
                                ?: challengeRepository.downloadChallengeSave(challengeId).getOrNull()
                            if (saveBytes != null) {
                                this@EmulationViewModel.challengeSaveData = saveBytes
                                libretroController.unserialize(saveBytes)
                                // Start the attempt server-side
                                challengeRepository.startAttempt(challengeId).onSuccess { attempt ->
                                    withContext(dispatchers.main) {
                                        _state.update { it.copy(challengeAttemptId = attempt.id) }
                                    }
                                }
                            }
                        }

                        // Try to load auto-save: in relay mode, download relay auto-save
                        else if (relayId != null) {
                            relayRepository.downloadRelayAutoSave(relayId).onSuccess { saveData ->
                                libretroController.unserialize(saveData)
                            }
                        } else if (currentPreferences.autoLoadSaveEnabled) {
                            loadGameStateUseCase(gameId).onSuccess { saveData ->
                                libretroController.unserialize(saveData)
                            }
                        }

                        // Set up netplay transport if in netplay mode
                        if (netplaySessionId != null) {
                            setupNetplayTransport(netplaySessionId, netplayLocalPort, netplayInputDelay, netplayIsHost)
                        }

                        libretroController.start()
                        val saveStatesSupported = libretroController.supportsSaveStates()
                        withContext(dispatchers.main) {
                            _state.update { it.copy(isRunning = true, isLoading = false, supportsSaveStates = saveStatesSupported, sessionElapsedSeconds = 0) }
                        }

                        // Initialize achievements if RA is linked (skip for netplay)
                        if (netplaySessionId == null) {
                            initAchievements(gameId)
                        }

                        // Start play-time heartbeat for online presence
                        presenceService.startHeartbeat(gameId)

                        // Start relay heartbeat if in relay mode
                        if (relayId != null) {
                            startRelayHeartbeat(relayId)
                        }

                        // Start FPS tracking and session timer
                        trackPerformance()
                        startSessionTimer()

                        // Start challenge timer if in challenge mode
                        if (challengeId != null) {
                            startChallengeTimer()
                        }

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

    /**
     * Set up the netplay transport, connect to the WebSocket, configure lockstep mode
     * on the controller, and start collecting remote inputs.
     *
     * For the host: sends initial state to the client via STATE_CHUNK messages.
     * For the client: receives and applies the initial state from the host.
     */
    private suspend fun setupNetplayTransport(
        sessionId: String,
        localPort: Int,
        inputDelay: Int,
        isHost: Boolean,
    ) {
        val signaling = NetplaySignaling(
            apiClient = apiClient,
            engineFactory = engineFactory,
            dispatchers = dispatchers,
            scope = scope,
            sessionId = sessionId,
        )
        val transport = WebSocketRelayTransport(signaling, scope)
        this.netplayTransport = transport

        // Connect to the WebSocket
        transport.connect()

        // Small delay to let the WebSocket connection establish
        delay(500)

        val inputBuffer = NetplayInputBuffer()

        // Initial state sync: host serializes and sends, client waits to receive
        if (isHost) {
            val stateData = libretroController.serialize()
            if (stateData != null) {
                sendStateChunks(transport, stateData)
            }
        } else {
            // Client waits for state chunks from host
            val stateData = receiveStateChunks(transport)
            if (stateData != null) {
                libretroController.unserialize(stateData)
            }
        }

        // Configure lockstep mode on the controller
        libretroController.setNetplayMode(transport, inputBuffer, localPort, inputDelay)

        // Collect remote inputs from transport and feed into input buffer
        netplayInputCollectorJob = scope.launch(dispatchers.io) {
            transport.remoteInputs.collect { remote ->
                inputBuffer.setRemoteInput(remote.frame, remote.port, remote.input)
            }
        }

        // Collect control messages for disconnect/reconnect handling
        netplayControlCollectorJob = scope.launch(dispatchers.io) {
            transport.controlMessages.collect { msg ->
                when (msg) {
                    is ControlMessage.PlayerLeft -> {
                        withContext(dispatchers.main) {
                            _state.update { it.copy(netplayPeerDisconnected = true) }
                        }
                    }
                    is ControlMessage.PlayerJoined -> {
                        withContext(dispatchers.main) {
                            _state.update { it.copy(netplayPeerDisconnected = false) }
                        }
                    }
                    else -> { /* other messages handled elsewhere */ }
                }
            }
        }
    }

    private fun sendStateChunks(transport: NetplayTransport, stateData: ByteArray) {
        val chunkSize = 16_384 // 16 KB chunks
        val totalChunks = (stateData.size + chunkSize - 1) / chunkSize
        for (i in 0 until totalChunks) {
            val offset = i * chunkSize
            val end = minOf(offset + chunkSize, stateData.size)
            val chunk = stateData.copyOfRange(offset, end)
            val encoded = com.spela.player.netplay.NetplayProtocol.encodeStateChunk(
                chunkIndex = i,
                totalChunks = totalChunks,
                totalSize = stateData.size,
                data = chunk,
            )
            transport.sendBinary(encoded)
        }
    }

    private suspend fun receiveStateChunks(transport: NetplayTransport): ByteArray? {
        val receivedChunks = mutableMapOf<Int, ByteArray>()
        var totalChunks = -1
        var totalSize = -1

        // Wait for all state chunks with a timeout.
        // Use takeWhile to break out of collect when all chunks arrive.
        kotlinx.coroutines.withTimeoutOrNull(10_000) {
            transport.remoteBinary.takeWhile { msg ->
                val chunk = com.spela.player.netplay.NetplayProtocol.decodeStateChunk(msg.data)
                if (chunk != null) {
                    totalChunks = chunk.totalChunks
                    totalSize = chunk.totalSize
                    receivedChunks[chunk.chunkIndex] = chunk.data
                }
                // Continue collecting while we don't have all chunks yet
                receivedChunks.size < totalChunks || totalChunks < 0
            }.collect {}
        } ?: return null

        if (totalSize < 0 || receivedChunks.size < totalChunks) return null

        // Reassemble in order
        val result = ByteArray(totalSize)
        var offset = 0
        for (i in 0 until totalChunks) {
            val chunkData = receivedChunks[i] ?: return null
            chunkData.copyInto(result, offset)
            offset += chunkData.size
        }
        return result
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
        sessionTimerJob?.cancel()
        sessionTimerJob = null
        challengeTimerJob?.cancel()
        challengeTimerJob = null
        challengeSaveData = null
        challengeCreationSaveData = null
        challengeCreationScreenshot = null
        relayHeartbeatJob?.cancel()
        relayHeartbeatJob = null
        netplayInputCollectorJob?.cancel()
        netplayInputCollectorJob = null
        netplayControlCollectorJob?.cancel()
        netplayControlCollectorJob = null
        netplayTransport?.disconnect()
        netplayTransport = null
        libretroController.clearNetplayMode()
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
            } else if (currentPreferences.autoSaveEnabled && !currentState.isChallengeMode) {
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

    private fun saveState() {
        if (_state.value.isChallengeMode) return
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
        if (_state.value.isChallengeMode) return
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
        if (_state.value.isChallengeMode) return
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

    // Challenge mode methods

    private var challengeTimerPausedAccumulatedMs: Long = 0
    private var challengeTimerPauseStartNanos: Long = 0

    private fun startChallengeTimer() {
        challengeTimerJob?.cancel()
        val startNanos = System.nanoTime()
        challengeTimerPausedAccumulatedMs = 0
        challengeTimerPauseStartNanos = 0
        challengeTimerJob = scope.launch(dispatchers.default) {
            while (isActive) {
                delay(100) // 100ms tick resolution for display-only timer
                val current = _state.value
                if (!current.isPaused && !current.showOverlay && current.isChallengeMode) {
                    if (challengeTimerPauseStartNanos != 0L) {
                        // Resuming from pause: accumulate the paused duration
                        challengeTimerPausedAccumulatedMs += (System.nanoTime() - challengeTimerPauseStartNanos) / 1_000_000
                        challengeTimerPauseStartNanos = 0
                    }
                    val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000 - challengeTimerPausedAccumulatedMs
                    withContext(dispatchers.main) {
                        _state.update { it.copy(challengeElapsedMs = elapsedMs) }
                    }
                } else if (challengeTimerPauseStartNanos == 0L) {
                    // Entering paused state: record when pause started
                    challengeTimerPauseStartNanos = System.nanoTime()
                }
            }
        }
    }

    private fun initChallengeCreation() {
        pauseGame()
        scope.launch(dispatchers.io) {
            challengeCreationSaveData = libretroController.serialize()
            challengeCreationScreenshot = screenshotCapture?.captureScreenshot()
            withContext(dispatchers.main) {
                _state.update {
                    it.copy(
                        showChallengeCreation = true,
                        showOverlay = false,
                    )
                }
            }
        }
    }

    private fun submitChallenge(name: String, description: String, type: String, difficulty: String) {
        val saveData = challengeCreationSaveData ?: return
        val screenshot = challengeCreationScreenshot
        val gameId = _state.value.gameId
        _state.update { it.copy(isCreatingChallenge = true) }
        scope.launch(dispatchers.io) {
            challengeRepository.createChallenge(
                gameId = gameId,
                name = name,
                description = description,
                type = type,
                difficulty = difficulty,
                coreName = currentCoreName,
                saveData = saveData,
                screenshotData = screenshot,
            ).fold(
                onSuccess = {
                    challengeCreationSaveData = null
                    challengeCreationScreenshot = null
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(
                                showChallengeCreation = false,
                                isCreatingChallenge = false,
                                challengeCreationSuccess = true,
                                statusMessage = "Challenge created!",
                            )
                        }
                    }
                    resumeGame()
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(
                                isCreatingChallenge = false,
                                error = "Failed to create challenge: ${error.message}",
                            )
                        }
                    }
                },
            )
        }
    }

    private fun completeChallenge() {
        val challengeId = _state.value.challengeId ?: return
        val attemptId = _state.value.challengeAttemptId ?: return
        pauseGame()
        challengeTimerJob?.cancel()
        scope.launch(dispatchers.io) {
            challengeRepository.completeAttempt(challengeId, attemptId).fold(
                onSuccess = { attempt ->
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(
                                challengeCompletedAttempt = attempt,
                                showOverlay = false,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = "Failed to submit: ${error.message}") }
                    }
                    // Restart timer since submission failed
                    startChallengeTimer()
                    resumeGame()
                },
            )
        }
    }

    private fun restartChallenge() {
        val challengeId = _state.value.challengeId ?: return
        val saveData = challengeSaveData ?: return
        val currentAttemptId = _state.value.challengeAttemptId

        challengeTimerJob?.cancel()

        scope.launch(dispatchers.io) {
            // Abandon current attempt first (await completion before starting new one)
            if (currentAttemptId != null) {
                challengeRepository.abandonAttempt(challengeId, currentAttemptId)
            }

            // Reload challenge save state
            libretroController.unserialize(saveData)

            // Start new server-side attempt
            challengeRepository.startAttempt(challengeId).fold(
                onSuccess = { attempt ->
                    withContext(dispatchers.main) {
                        _state.update {
                            it.copy(
                                challengeAttemptId = attempt.id,
                                challengeElapsedMs = 0,
                                showOverlay = false,
                            )
                        }
                    }
                    startChallengeTimer()
                    resumeGame()
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = "Failed to restart: ${error.message}") }
                    }
                },
            )
        }
    }

    private fun giveUpChallenge() {
        val challengeId = _state.value.challengeId ?: return
        val attemptId = _state.value.challengeAttemptId

        challengeTimerJob?.cancel()
        _state.update { it.copy(showGiveUpConfirm = false, requestExit = true) }

        if (attemptId != null) {
            scope.launch(dispatchers.io) {
                challengeRepository.abandonAttempt(challengeId, attemptId)
            }
        }

        stopGame()
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
}
