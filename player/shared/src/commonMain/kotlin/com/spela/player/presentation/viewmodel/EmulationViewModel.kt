package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.domain.usecase.LoadGameStateUseCase
import com.spela.player.domain.usecase.PrepareGameUseCase
import com.spela.player.domain.usecase.SaveGameStateUseCase
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.state.EmulationState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val libretroController: LibretroController,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(EmulationState())
    val state: StateFlow<EmulationState> = _state.asStateFlow()

    private var currentPreferences = UserPreferences()

    fun onIntent(intent: EmulationIntent) {
        when (intent) {
            is EmulationIntent.StartGame -> startGame(intent.gameId)
            EmulationIntent.PauseGame -> pauseGame()
            EmulationIntent.ResumeGame -> resumeGame()
            EmulationIntent.StopGame -> stopGame()
            EmulationIntent.SaveState -> saveState()
            EmulationIntent.LoadState -> loadState()
            EmulationIntent.ToggleOverlay -> _state.update { it.copy(showOverlay = !it.showOverlay) }
            EmulationIntent.ToggleFastForward -> toggleFastForward()
            EmulationIntent.TakeScreenshot -> { /* Platform-specific capture */ }

            EmulationIntent.ShowExitConfirm -> {
                if (currentPreferences.autoSaveEnabled) {
                    // Auto-save is enabled, so progress won't be lost — exit immediately.
                    // Pause first to stop audio/video instantly, then save+stop async.
                    pauseGame()
                    _state.update { it.copy(showExitConfirm = false, requestExit = true) }
                    stopGame()
                } else {
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
        }
    }

    private fun startGame(gameId: String) {
        _state.update {
            it.copy(
                gameId = gameId,
                isLoading = true,
                showOverlay = false,
                showExitConfirm = false,

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
                _state.update { it.copy(gameTitle = detail.game.title) }
                consoleId = detail.game.consoleId
            }

            // Resolve shader using two-layer system
            val resolvedShader = preferencesRepository.resolveShader(consoleId)

            _state.update {
                it.copy(
                    showPerformanceOverlay = currentPreferences.showPerformanceOverlay,
                    selectedShader = resolvedShader,
                )
            }

            // Prepare game and core files
            prepareGameUseCase(gameId).fold(
                onSuccess = { (gamePath, corePath) ->
                    try {
                        libretroController.loadCore(corePath)
                        libretroController.loadGame(gamePath)

                        // Try to load auto-save if enabled
                        if (currentPreferences.autoLoadSaveEnabled) {
                            loadGameStateUseCase(gameId).onSuccess { saveData ->
                                libretroController.unserialize(saveData)
                            }
                        }

                        libretroController.start()
                        _state.update { it.copy(isRunning = true, isLoading = false) }

                        // Start FPS tracking
                        trackPerformance()
                    } catch (e: Exception) {
                        _state.update {
                            it.copy(error = "Failed to start emulation: ${e.message}", isLoading = false)
                        }
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = "Failed to prepare game: ${error.message}", isLoading = false)
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

    private fun stopGame() {
        scope.launch(dispatchers.io) {
            // Auto-save before stopping if enabled
            if (currentPreferences.autoSaveEnabled) {
                val gameId = _state.value.gameId
                try {
                    val saveData = libretroController.serialize()
                    if (saveData != null) {
                        saveGameStateUseCase(gameId, saveData)
                    }
                } catch (_: Exception) {
                    // Best effort auto-save
                }
            }

            libretroController.stop()
            _state.update {
                it.copy(isRunning = false, isPaused = false, fps = 0f, frameTime = 0f)
            }
        }
    }

    private fun saveState() {
        scope.launch(dispatchers.io) {
            val gameId = _state.value.gameId
            val saveData = libretroController.serialize() ?: return@launch
            saveGameStateUseCase(gameId, saveData).fold(
                onSuccess = {
                    _state.update { it.copy(statusMessage = "State saved") }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = "Failed to save: ${error.message}") }
                },
            )
        }
    }

    private fun loadState() {
        scope.launch(dispatchers.io) {
            val gameId = _state.value.gameId
            loadGameStateUseCase(gameId).fold(
                onSuccess = { saveData ->
                    libretroController.unserialize(saveData)
                    _state.update { it.copy(statusMessage = "State loaded") }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = "Failed to load save: ${error.message}") }
                },
            )
        }
    }

    private fun toggleFastForward() {
        val newState = !_state.value.isFastForward
        libretroController.setFastForward(newState)
        _state.update { it.copy(isFastForward = newState) }
    }

    private fun trackPerformance() {
        scope.launch(dispatchers.default) {
            libretroController.performanceStats().collect { (fps, frameTime) ->
                _state.update { it.copy(fps = fps, frameTime = frameTime) }
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
    fun serialize(): ByteArray?
    fun unserialize(data: ByteArray): Boolean
    fun setFastForward(enabled: Boolean)
    fun performanceStats(): kotlinx.coroutines.flow.Flow<Pair<Float, Float>>
}
