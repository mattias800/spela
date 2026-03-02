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
    /** The libretro core name used for the current emulation session. */
    var currentCoreName: String = ""
    /**
     * Load SRAM (save data) before starting emulation.
     * Tries local first, falls back to server if online.
     * If the data starts with ZIP magic bytes, it's a directory-based save (e.g. Dolphin)
     * and gets extracted to the save directory instead of loaded as SRAM.
     * Called from within an IO coroutine in startGame().
     */
    suspend fun loadSramOnStart(gameId: String) {
        try {
            val localSram = saveDataRepository.loadLocalSRAM(gameId)
            if (localSram != null) {
                if (isZipData(localSram)) {
                    saveDataRepository.unzipToSaveDirectory(localSram)
                } else {
                    libretroController.setSRAM(localSram)
                }
            } else if (connectivityMonitor.isOnline.value) {
                saveDataRepository.downloadActiveSaveData(gameId).onSuccess { data ->
                    if (isZipData(data)) {
                        saveDataRepository.unzipToSaveDirectory(data)
                    } else {
                        libretroController.setSRAM(data)
                    }
                    saveDataRepository.saveLocalSRAM(gameId, data)
                }
            }
        } catch (_: Exception) {
            // SRAM loading is best-effort
        }
    }

    /**
     * Save SRAM on stop (best effort). Called from within an IO coroutine.
     * If the core doesn't provide SRAM (e.g. Dolphin), falls back to zipping
     * the save directory and uploading that instead.
     */
    suspend fun saveSramOnStop(gameId: String) {
        try {
            val sramData = libretroController.getSRAM()
            if (sramData != null && sramData.isNotEmpty()) {
                saveDataRepository.saveLocalSRAM(gameId, sramData)
                if (connectivityMonitor.isOnline.value) {
                    runCatching { saveDataRepository.uploadActiveSaveData(gameId, sramData) }
                }
            } else {
                // Directory-save fallback for cores like Dolphin
                val zipData = saveDataRepository.zipSaveDirectory(gameId)
                if (zipData != null) {
                    saveDataRepository.saveLocalSRAM(gameId, zipData)
                    if (connectivityMonitor.isOnline.value) {
                        runCatching { saveDataRepository.uploadActiveSaveData(gameId, zipData) }
                    }
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
        println("[SaveManager] autoLoadSaveState: loading game $gameId")
        loadGameStateUseCase(gameId).fold(
            onSuccess = { saveData ->
                println("[SaveManager] autoLoadSaveState: got ${saveData.size} bytes, unserializing")
                val ok = libretroController.unserialize(saveData)
                println("[SaveManager] autoLoadSaveState: unserialize result=$ok")
            },
            onFailure = { e ->
                println("[SaveManager] autoLoadSaveState: failed: ${e.message}")
            },
        )
    }

    /**
     * Auto-save on stop (if enabled and not in challenge mode).
     * Called from within an IO coroutine.
     */
    suspend fun autoSaveOnStop(gameId: String) {
        try {
            println("[SaveManager] autoSaveOnStop: serializing game $gameId")
            val saveData = libretroController.serialize()
            println("[SaveManager] autoSaveOnStop: serialize returned ${saveData?.size ?: "null"} bytes")
            if (saveData != null) {
                // Save to local filesystem + SQLite only (fast, no network).
                val result = saveRepository.saveLocally(gameId, "Auto Save", saveData, isAuto = true)
                println("[SaveManager] autoSaveOnStop: saveLocally result=${result.isSuccess}")
            }
        } catch (e: Exception) {
            println("[SaveManager] autoSaveOnStop: exception: ${e.message}")
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
            saveGameStateUseCase(gameId, saveData, screenshot, currentCoreName.ifEmpty { null }).fold(
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
        return saveRepository.saveToSlot(gameId, slot, data, screenshot, currentCoreName.ifEmpty { null }).map { }
    }

    /**
     * Load from a quick-save slot. Called from EmulationViewModel.
     */
    suspend fun loadFromSlot(gameId: String, slot: Int): Result<ByteArray> {
        return saveRepository.loadFromSlot(gameId, slot)
    }

    /** Check if data starts with ZIP magic bytes (PK\x03\x04). */
    private fun isZipData(data: ByteArray): Boolean =
        data.size >= 4 &&
            data[0] == 0x50.toByte() &&
            data[1] == 0x4B.toByte() &&
            data[2] == 0x03.toByte() &&
            data[3] == 0x04.toByte()
}
