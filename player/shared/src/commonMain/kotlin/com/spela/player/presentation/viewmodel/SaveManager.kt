package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.domain.controller.ScreenshotCapture
import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.domain.repository.SaveRepository
import com.spela.player.domain.usecase.LoadGameStateUseCase
import com.spela.player.domain.usecase.SaveGameStateUseCase
import com.spela.player.presentation.state.EmulationState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages all save-related operations: SRAM load/save, auto-save/load,
 * and manual save/load state.
 */
class SaveManager(
    private val saveGameStateUseCase: SaveGameStateUseCase,
    private val loadGameStateUseCase: LoadGameStateUseCase,
    private val saveDataRepository: SaveDataRepository,
    private val saveRepository: SaveRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val libretroController: LibretroController,
    private val screenshotCapture: ScreenshotCapture?,
    private val _state: MutableStateFlow<EmulationState>,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    /**
     * Load SRAM (save data) before starting emulation.
     * Tries local first, falls back to server if online.
     * Called from within an IO coroutine in startGame().
     */
    suspend fun loadSramOnStart(gameId: String) {
        try {
            val localSram = saveDataRepository.loadLocalSRAM(gameId)
            if (localSram != null) {
                libretroController.setSRAM(localSram)
            } else if (connectivityMonitor.isOnline.value) {
                saveDataRepository.downloadActiveSaveData(gameId).onSuccess { sram ->
                    libretroController.setSRAM(sram)
                    saveDataRepository.saveLocalSRAM(gameId, sram)
                }
            }
        } catch (_: Exception) {
            // SRAM loading is best-effort
        }
    }

    /**
     * Save SRAM on stop (best effort). Called from within an IO coroutine.
     */
    suspend fun saveSramOnStop(gameId: String) {
        try {
            val sramData = libretroController.getSRAM()
            if (sramData != null && sramData.isNotEmpty()) {
                saveDataRepository.saveLocalSRAM(gameId, sramData)
                if (connectivityMonitor.isOnline.value) {
                    runCatching { saveDataRepository.uploadActiveSaveData(gameId, sramData) }
                }
            }
        } catch (_: Exception) {
            // Best effort SRAM save
        }
    }

    /**
     * Auto-load save state on game start (if enabled and applicable).
     * Called from within an IO coroutine.
     */
    suspend fun autoLoadSaveState(gameId: String) {
        loadGameStateUseCase(gameId).onSuccess { saveData ->
            libretroController.unserialize(saveData)
        }
    }

    /**
     * Auto-save on stop (if enabled and not in challenge mode).
     * Called from within an IO coroutine.
     */
    suspend fun autoSaveOnStop(gameId: String) {
        try {
            val saveData = libretroController.serialize()
            if (saveData != null) {
                val screenshot = screenshotCapture?.captureScreenshot()
                saveGameStateUseCase(gameId, saveData, screenshot)
            }
        } catch (_: Exception) {
            // Best effort auto-save
        }
    }

    /**
     * Manual save state. Blocks in challenge mode and hardcore mode.
     */
    fun saveState() {
        if (_state.value.isChallengeMode) return
        if (_state.value.isHardcoreMode) {
            _state.update { it.copy(error = "Save states are disabled in hardcore mode") }
            return
        }
        scope.launch(dispatchers.io) {
            val gameId = _state.value.gameId
            val saveData = libretroController.serialize() ?: return@launch
            val screenshot = screenshotCapture?.captureScreenshot()
            saveGameStateUseCase(gameId, saveData, screenshot).fold(
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

    /**
     * Manual load state. Blocks in challenge mode and hardcore mode.
     */
    fun loadState() {
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

    /**
     * Save to a quick-save slot. Called from EmulationViewModel.
     */
    suspend fun saveToSlot(gameId: String, slot: Int, data: ByteArray): Result<Unit> {
        val screenshot = screenshotCapture?.captureScreenshot()
        return saveRepository.saveToSlot(gameId, slot, data, screenshot).map { }
    }

    /**
     * Load from a quick-save slot. Called from EmulationViewModel.
     */
    suspend fun loadFromSlot(gameId: String, slot: Int): Result<ByteArray> {
        return saveRepository.loadFromSlot(gameId, slot)
    }
}
