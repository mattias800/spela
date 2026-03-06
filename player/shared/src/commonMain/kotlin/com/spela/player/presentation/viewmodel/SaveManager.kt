package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.domain.controller.ScreenshotCapture
import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.domain.repository.SessionRepository
import com.spela.player.presentation.state.EmulationState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages all save-related operations: SRAM load/save, auto-save/load,
 * and manual save/load state. All operations are session-scoped.
 * When no session is active, operations are silently skipped.
 */
class SaveManager(
    private val saveDataRepository: SaveDataRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val libretroController: LibretroController,
    private val screenshotCapture: ScreenshotCapture?,
    private val _state: MutableStateFlow<EmulationState>,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val sessionRepository: SessionRepository,
) {
    /** The libretro core name used for the current emulation session. */
    var currentCoreName: String = ""

    /** The active session ID. When null, save/load operations are no-ops. */
    var currentSessionId: String? = null

    /**
     * Load SRAM (save data) before starting emulation.
     * Downloads from session SRAM endpoint.
     * If the data starts with ZIP magic bytes, it's a directory-based save (e.g. Dolphin)
     * and gets extracted to the save directory instead of loaded as SRAM.
     * Called from within an IO coroutine in startGame().
     */
    suspend fun loadSramOnStart(gameId: String) {
        try {
            val sessionId = currentSessionId ?: return

            sessionRepository.downloadSessionSram(sessionId).onSuccess { data ->
                if (isZipData(data)) {
                    saveDataRepository.unzipToSaveDirectory(data)
                } else {
                    libretroController.setSRAM(data)
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
            val sessionId = currentSessionId ?: return

            val sramData = libretroController.getSRAM()
            if (sramData != null && sramData.isNotEmpty()) {
                runCatching { sessionRepository.uploadSessionSram(sessionId, sramData) }
            } else {
                // Directory-save fallback for cores like Dolphin
                val zipData = saveDataRepository.zipSaveDirectory(gameId)
                if (zipData != null) {
                    runCatching { sessionRepository.uploadSessionSram(sessionId, zipData) }
                }
            }
        } catch (_: Exception) {
            // Best effort SRAM save
        }
    }

    /**
     * Auto-load save state on game start (if enabled and applicable).
     * Downloads from session auto-save endpoint.
     * Called from within an IO coroutine.
     */
    suspend fun autoLoadSaveState(gameId: String) {
        val sessionId = currentSessionId ?: return

        println("[SaveManager] autoLoadSaveState: loading session $sessionId auto-save")
        sessionRepository.downloadSessionAutoSave(sessionId).fold(
            onSuccess = { saveData ->
                println("[SaveManager] autoLoadSaveState: got ${saveData.size} bytes from session, unserializing")
                val ok = libretroController.unserialize(saveData)
                println("[SaveManager] autoLoadSaveState: unserialize result=$ok")
            },
            onFailure = { e ->
                println("[SaveManager] autoLoadSaveState: session auto-save failed: ${e.message}")
            },
        )
    }

    /**
     * Auto-save on stop (if enabled and not in challenge mode).
     * Always serializes via the libretro controller; uploads to session when available.
     * Called from within an IO coroutine.
     */
    suspend fun autoSaveOnStop(gameId: String) {
        try {
            println("[SaveManager] autoSaveOnStop: serializing game $gameId")
            val saveData = libretroController.serialize()
            println("[SaveManager] autoSaveOnStop: serialize returned ${saveData?.size ?: "null"} bytes")
            if (saveData != null) {
                val sessionId = currentSessionId
                if (sessionId != null) {
                    val screenshot = screenshotCapture?.captureScreenshot()
                    val result = sessionRepository.uploadSessionAutoSave(sessionId, saveData, screenshot)
                    println("[SaveManager] autoSaveOnStop: session upload result=${result.isSuccess}")
                }
            }
        } catch (e: Exception) {
            println("[SaveManager] autoSaveOnStop: exception: ${e.message}")
        }
    }

    /**
     * Manual save state. Blocks in challenge mode and hardcore mode.
     * When no session is active, delegates to the libretro controller directly.
     */
    fun saveState() {
        if (_state.value.isChallengeMode) return
        if (_state.value.isHardcoreMode) {
            _state.update { it.copy(error = "Save states are disabled in hardcore mode") }
            return
        }
        scope.launch(dispatchers.io) {
            val saveData = libretroController.serialize() ?: return@launch
            val sessionId = currentSessionId
            if (sessionId != null) {
                val screenshot = screenshotCapture?.captureScreenshot()
                sessionRepository.uploadSessionSave(sessionId, "Manual Save", saveData, screenshot).fold(
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
            } else {
                // No session — save state was serialized by the controller
                withContext(dispatchers.main) {
                    _state.update { it.copy(statusMessage = "State saved") }
                }
            }
        }
    }

    /**
     * Manual load state. Blocks in challenge mode and hardcore mode.
     * When no session is active, delegates to the libretro controller directly.
     */
    fun loadState() {
        if (_state.value.isChallengeMode) return
        if (_state.value.isHardcoreMode) {
            _state.update { it.copy(error = "Save states are disabled in hardcore mode") }
            return
        }
        scope.launch(dispatchers.io) {
            val sessionId = currentSessionId
            if (sessionId != null) {
                sessionRepository.downloadSessionAutoSave(sessionId).fold(
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
            } else {
                // No session — attempt to load from last serialized state
                val data = libretroController.serialize()
                if (data != null) {
                    libretroController.unserialize(data)
                    withContext(dispatchers.main) {
                        _state.update { it.copy(statusMessage = "State loaded") }
                    }
                }
            }
        }
    }

    /** Check if data starts with ZIP magic bytes (PK\x03\x04). */
    private fun isZipData(data: ByteArray): Boolean =
        data.size >= 4 &&
            data[0] == 0x50.toByte() &&
            data[1] == 0x4B.toByte() &&
            data[2] == 0x03.toByte() &&
            data[3] == 0x04.toByte()
}
