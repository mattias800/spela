package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.domain.controller.ScreenshotCapture
import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.domain.repository.SessionRepository
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.state.SaveSlotInfo
import com.spela.player.presentation.state.SecondaryToastData
import com.spela.player.presentation.state.SecondaryToastType
import com.spela.player.util.DispatcherProvider
import com.spela.player.util.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Result of attempting to auto-load a save state.
 * Used to communicate core compatibility issues to the caller.
 */
sealed class AutoLoadResult {
    /** Save state loaded successfully. */
    data object Loaded : AutoLoadResult()

    /** Save state exists but was created with a different core. */
    data class CoreMismatch(val saveCoreName: String, val currentCoreName: String) : AutoLoadResult()

    /** No auto-save exists for this session. */
    data object NoSave : AutoLoadResult()

    /** An error occurred while loading. */
    data class Error(val message: String) : AutoLoadResult()
}

/**
 * Classifies a save attempt that was intercepted while the SaveManager
 * was in rehearsal mode (#672 "Try with my save" flow). The UI
 * subscribes to these events to surface the
 * `core_upd.snack.rehearsal_save` snackbar.
 */
enum class RehearsalSaveKind { Manual, Auto, Slot, Sram }

/**
 * Event emitted every time [SaveManager] silently drops a save write
 * because [SaveManager.rehearsalMode] is active. The UI shows the
 * "You're in a trial run — saves are paused until you keep this
 * version" snackbar in response.
 */
data class RehearsalSaveBlocked(val kind: RehearsalSaveKind)

/**
 * Compact view of the fields the ViewModel needs from a [GameSession]
 * to decide whether the #672 core-upgrade sheet should fire on
 * `startEmulation`. Fetched by [SaveManager.coreDecisionFlagsFor] so
 * callers don't have to re-hydrate the full session record in hot paths.
 */
data class CoreDecisionFlags(
    val pinnedCoreSha256: String?,
    val userLockedCoreVersion: Boolean,
    /**
     * When true, the previous run on this session entered rehearsal
     * mode and the player process didn't reach a clean Sheet C/D
     * resolution before dying. The next launch must route the user to
     * Sheet D so they can recover (lock to old / start fresh /
     * dismiss). Cleared by all of the rehearsal exit handlers.
     */
    val rehearsalCrashPending: Boolean = false,
)

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
    private val fileStorage: FileStorage,
) {
    /** The libretro core name used for the current emulation session. */
    var currentCoreName: String = ""

    /** The active session ID. When null, save/load operations are no-ops. */
    var currentSessionId: String? = null

    /**
     * When true, every save-upload path in this manager silently drops
     * the write and emits a [RehearsalSaveBlocked] event on
     * [rehearsalSaveBlocked]. Used by the #672 "Try with my save"
     * rehearsal flow: the user tries the new core binary against an
     * existing save without risking the durable save file on disk.
     *
     * Game-state save/load via the libretro controller is unaffected —
     * only the server-upload side is suppressed. The emulator's RAM
     * state continues normally for the rehearsal's lifetime.
     */
    var rehearsalMode: Boolean = false

    private val _rehearsalSaveBlocked = MutableSharedFlow<RehearsalSaveBlocked>(
        extraBufferCapacity = 8,
    )

    /**
     * Stream of save-write events that were silently dropped because
     * [rehearsalMode] was active. See #672.
     */
    val rehearsalSaveBlocked: SharedFlow<RehearsalSaveBlocked> =
        _rehearsalSaveBlocked.asSharedFlow()

    /**
     * Called from every upload call site while [rehearsalMode] is true
     * so the UI can surface the `core_upd.snack.rehearsal_save`
     * snackbar. tryEmit is used (not emit) so the call site stays
     * non-suspending from the save-state-save path.
     */
    private fun emitRehearsalBlocked(kind: RehearsalSaveKind) {
        _rehearsalSaveBlocked.tryEmit(RehearsalSaveBlocked(kind))
    }

    /**
     * Ensures a session exists for the given game. If sessionId is already set,
     * returns it. Otherwise tries to reuse the most recent session, or creates
     * a new one named "Default" (matching the web UI behavior).
     *
     * When [forceNew] is true (user chose "New Game"), always creates a fresh
     * session instead of reusing an existing one. This prevents overwriting
     * auto-saves from a previous playthrough.
     *
     * Returns the resolved session ID, or null if offline/failed.
     */
    suspend fun ensureSession(gameId: String, sessionId: String?, forceNew: Boolean = false): String? {
        if (sessionId != null) return sessionId
        return try {
            if (!forceNew) {
                val existing = sessionRepository.getSessionsForGame(gameId).getOrNull()
                if (!existing.isNullOrEmpty()) {
                    return existing.first().id
                }
            }
            val existing = sessionRepository.getSessionsForGame(gameId).getOrNull()
            val number = (existing?.size ?: 0) + 1
            val name = if (number == 1) "Default" else "Playthrough $number"
            sessionRepository.createSession(gameId, name).getOrNull()?.id
        } catch (e: Exception) {
            println("[SaveManager] Failed to ensure session: ${e.message}")
            null
        }
    }

    /**
     * Returns the [com.spela.player.domain.model.GameSession.pinnedCoreSha256]
     * for [sessionId], or null when no session / no pin / network failure.
     * Used by EmulationViewModel to steer [PrepareGameUseCase] onto the
     * versioned-core-download path when the session has a pin.
     */
    suspend fun pinnedCoreSha256For(sessionId: String?): String? {
        if (sessionId.isNullOrEmpty()) return null
        return try {
            sessionRepository.getSession(sessionId).getOrNull()?.pinnedCoreSha256
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads the #672 core-upgrade decision flags for [sessionId] in a
     * single network call. Returns `null` when no session is active or
     * the fetch fails — callers should treat that as "no decision
     * needed" and fall back to default behaviour.
     */
    suspend fun coreDecisionFlagsFor(sessionId: String?): CoreDecisionFlags? {
        if (sessionId.isNullOrEmpty()) return null
        return try {
            val session = sessionRepository.getSession(sessionId).getOrNull()
                ?: return null
            CoreDecisionFlags(
                pinnedCoreSha256 = session.pinnedCoreSha256,
                userLockedCoreVersion = session.userLockedCoreVersion,
                rehearsalCrashPending = session.rehearsalCrashPending,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Counts the save states on [sessionId]. Feeds the "brand new
     * session — skip the decision sheet" branch of #672's decision
     * flow. Returns 0 on any error so a network hiccup doesn't surface
     * a prompt for a session the user never saved to.
     */
    suspend fun sessionSaveCount(sessionId: String?): Int {
        if (sessionId.isNullOrEmpty()) return 0
        return try {
            sessionRepository.getSessionSaves(sessionId).getOrNull()?.size ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Writes `userLockedCoreVersion` on [sessionId]. Called after the
     * user picks "Lock this session to the older version" on Sheet A
     * (or the equivalent on Sheets C / D in PR 3c). Returns false on
     * failure so the VM can still launch the game with the pinned
     * binary locally — the server-side flag catches up on the next
     * attempt.
     */
    suspend fun setSessionCoreLock(sessionId: String?, locked: Boolean): Boolean {
        if (sessionId.isNullOrEmpty()) return false
        return try {
            sessionRepository.updateSessionCoreFlags(
                sessionId,
                userLockedCoreVersion = locked,
            ).isSuccess
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Writes `autoLoadSuppressed` on [sessionId]. Called after the
     * user picks "Start fresh" on Sheet B / D — the next launch of
     * the session must skip the stale save auto-load. Cleared by the
     * session handler on the first successful manual save. See #672.
     */
    suspend fun setSessionAutoLoadSuppressed(sessionId: String?, suppressed: Boolean): Boolean {
        if (sessionId.isNullOrEmpty()) return false
        return try {
            sessionRepository.updateSessionCoreFlags(
                sessionId,
                autoLoadSuppressed = suppressed,
            ).isSuccess
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Writes `rehearsalCrashPending` on [sessionId]. Called immediately
     * before entering rehearsal mode (with [pending] = true) and after
     * a clean Sheet C / Sheet D resolution (with [pending] = false).
     * Surviving the app process means the previous run crashed mid-
     * rehearsal — on next session restore the player checks this flag
     * and routes the user directly to Sheet D so they can recover.
     *
     * Returns `true` on success, `false` if the write failed; callers
     * may proceed with the local rehearsal anyway because losing the
     * sentinel only degrades crash-recovery, not the current session.
     * See #672 spec §"Crash recovery" / PR 3c.iii.
     */
    suspend fun setSessionRehearsalCrashPending(sessionId: String?, pending: Boolean): Boolean {
        if (sessionId.isNullOrEmpty()) return false
        return try {
            sessionRepository.updateSessionCoreFlags(
                sessionId,
                rehearsalCrashPending = pending,
            ).isSuccess
        } catch (_: Exception) {
            false
        }
    }

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
        if (rehearsalMode) {
            emitRehearsalBlocked(RehearsalSaveKind.Sram)
            return
        }
        try {
            val sessionId = currentSessionId ?: return

            val sramData = libretroController.getSRAM()
            if (sramData != null && sramData.isNotEmpty()) {
                runCatching { sessionRepository.uploadSessionSram(sessionId, sramData, currentCoreName) }
            } else {
                // Directory-save fallback for cores like Dolphin
                val zipData = saveDataRepository.zipSaveDirectory(gameId)
                if (zipData != null) {
                    runCatching { sessionRepository.uploadSessionSram(sessionId, zipData, currentCoreName) }
                }
            }
        } catch (_: Exception) {
            // Best effort SRAM save
        }
    }

    /**
     * Auto-load save state on game start (if enabled and applicable).
     * Downloads from session auto-save endpoint.
     * Checks core compatibility before loading: if the save was created with a
     * different core, returns [AutoLoadResult.CoreMismatch] instead of loading.
     * Called from within an IO coroutine.
     */
    suspend fun autoLoadSaveState(gameId: String): AutoLoadResult {
        val sessionId = currentSessionId ?: return AutoLoadResult.NoSave

        println("[SaveManager] autoLoadSaveState: checking session $sessionId for core compatibility")

        // Fetch session detail to check core name before downloading save data
        val sessionCoreName = sessionRepository.getSession(sessionId).getOrNull()?.coreName
        if (!sessionCoreName.isNullOrEmpty() && currentCoreName.isNotEmpty()
            && sessionCoreName != currentCoreName
        ) {
            println("[SaveManager] autoLoadSaveState: core mismatch — save='$sessionCoreName' current='$currentCoreName'")
            return AutoLoadResult.CoreMismatch(
                saveCoreName = sessionCoreName,
                currentCoreName = currentCoreName,
            )
        }

        println("[SaveManager] autoLoadSaveState: loading session $sessionId auto-save")
        val tempPath = "${fileStorage.getSavesDir()}/.tmp-load-$sessionId"
        return sessionRepository.downloadSessionAutoSaveToFile(sessionId, tempPath).fold(
            onSuccess = {
                val size = runCatching { fileStorage.getFileSize(tempPath) }.getOrDefault(-1L)
                println("[SaveManager] autoLoadSaveState: streamed $size bytes to disk, unserializing from file")
                val ok = libretroController.unserializeFromFile(tempPath)
                runCatching { fileStorage.deleteFile(tempPath) }
                println("[SaveManager] autoLoadSaveState: unserialize result=$ok")
                AutoLoadResult.Loaded
            },
            onFailure = { e ->
                runCatching { fileStorage.deleteFile(tempPath) }
                println("[SaveManager] autoLoadSaveState: session auto-save failed: ${e.message}")
                if (e.message?.contains("404") == true || e.message?.contains("not found", ignoreCase = true) == true) {
                    AutoLoadResult.NoSave
                } else {
                    AutoLoadResult.Error(e.message ?: "Unknown error")
                }
            },
        )
    }

    /**
     * Force-load the auto-save despite a core mismatch.
     * Called when the user chooses "Try Loading Anyway" after a mismatch warning.
     * @return true if the save state was loaded successfully, false otherwise.
     */
    suspend fun forceAutoLoadSaveState(): Boolean {
        val sessionId = currentSessionId ?: return false
        println("[SaveManager] forceAutoLoadSaveState: loading session $sessionId auto-save (ignoring core mismatch)")
        val tempPath = "${fileStorage.getSavesDir()}/.tmp-load-force-$sessionId"
        return sessionRepository.downloadSessionAutoSaveToFile(sessionId, tempPath).fold(
            onSuccess = {
                val ok = libretroController.unserializeFromFile(tempPath)
                runCatching { fileStorage.deleteFile(tempPath) }
                println("[SaveManager] forceAutoLoadSaveState: unserialize result=$ok")
                ok
            },
            onFailure = { e ->
                runCatching { fileStorage.deleteFile(tempPath) }
                println("[SaveManager] forceAutoLoadSaveState: failed: ${e.message}")
                false
            },
        )
    }

    /**
     * Auto-save on stop (if enabled and not in challenge mode).
     * Always serializes via the libretro controller; uploads to session when available.
     * Called from within an IO coroutine.
     */
    suspend fun autoSaveOnStop(gameId: String) {
        if (rehearsalMode) {
            emitRehearsalBlocked(RehearsalSaveKind.Auto)
            return
        }
        println("[SaveManager] autoSaveOnStop: staging save state to disk…")
        val staged = try { stageSaveToTempFile() } catch (e: Exception) {
            println("[SaveManager] autoSaveOnStop: stage failed: ${e.message}")
            null
        } ?: run {
            println("[SaveManager] autoSaveOnStop: stageSaveToTempFile returned null")
            return
        }
        println("[SaveManager] autoSaveOnStop: staged ${staged.size} bytes at ${staged.path}, uploading…")
        try {
            val sessionId = currentSessionId
            if (sessionId != null) {
                val screenshot = screenshotCapture?.captureScreenshot()
                val result = sessionRepository.uploadSessionAutoSaveFromFile(
                    sessionId, staged.path, staged.size, screenshot, currentCoreName,
                )
                if (result.isSuccess) {
                    println("[SaveManager] autoSaveOnStop: upload OK")
                }
                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                    println("[SaveManager] autoSaveOnStop: upload failed: $msg")
                    val userMsg = if (msg.contains("413") || msg.contains("quota", ignoreCase = true)) {
                        "Auto-save failed: storage quota exceeded. Delete old saves to free space."
                    } else {
                        "Auto-save upload failed: $msg"
                    }
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = userMsg) }
                    }
                }
            }
        } catch (e: Exception) {
            println("[SaveManager] autoSaveOnStop: exception: ${e.message}")
        } finally {
            staged.cleanup()
        }
    }

    /**
     * Manual save state. Blocks in challenge mode and hardcore mode.
     * When no session is active, delegates to the libretro controller directly.
     */
    private companion object {
        /** How long the "Saved" checkmark stays on the in-game overlay's
         *  Save button after a successful upload (#803). */
        const val SAVE_SUCCESS_FLASH_MS = 1_500L
    }

    fun saveState() {
        if (_state.value.isChallengeMode) return
        if (_state.value.isHardcoreMode) {
            _state.update { it.copy(error = "Save states are disabled in hardcore mode") }
            return
        }
        if (rehearsalMode) {
            emitRehearsalBlocked(RehearsalSaveKind.Manual)
            return
        }
        // Inline button feedback: tap → "Saving…" immediately, even
        // if the user dismisses the overlay before the toast renders.
        // Clears any stale error from a prior failed attempt so the
        // button doesn't read "Save failed" while a fresh save is
        // in flight (#803).
        _state.update {
            it.copy(
                isSaveInProgress = true,
                saveStateError = null,
                saveStateJustSucceeded = false,
            )
        }
        scope.launch(dispatchers.io) {
            // Wrap staging in try/catch so a thrown exception still
            // clears isSaveInProgress — otherwise the button is stuck
            // on "Saving…" for the rest of the session. The inner
            // runCatching in stageSaveToTempFile only covers the
            // file write, not the controller / FileStorage calls
            // around it.
            val staged = try {
                stageSaveToTempFile() ?: run {
                    _state.update { it.copy(isSaveInProgress = false) }
                    return@launch
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaveInProgress = false,
                        saveStateError = "Failed to save: ${e.message}",
                    )
                }
                return@launch
            }
            try {
                val sessionId = currentSessionId
                if (sessionId != null) {
                    val screenshot = screenshotCapture?.captureScreenshot()
                    sessionRepository.uploadSessionSaveFromFile(
                        sessionId, "Manual Save", staged.path, staged.size, screenshot, currentCoreName,
                    ).fold(
                        onSuccess = {
                            withContext(dispatchers.main) {
                                _state.update {
                                    it.copy(
                                        statusMessage = "State saved",
                                        isSaveInProgress = false,
                                        saveStateJustSucceeded = true,
                                        secondaryToast = SecondaryToastData(
                                            message = "Saved to Slot ${it.activeSlot}",
                                            type = SecondaryToastType.SAVE,
                                        ),
                                    )
                                }
                            }
                            refreshSaveSlots()
                            // Hold the "Saved" checkmark briefly so the
                            // user catches it even if the toast is
                            // dismissed first, then drop the flag so
                            // the button returns to idle. (#803)
                            scope.launch(dispatchers.io) {
                                kotlinx.coroutines.delay(SAVE_SUCCESS_FLASH_MS)
                                withContext(dispatchers.main) {
                                    _state.update { it.copy(saveStateJustSucceeded = false) }
                                }
                            }
                        },
                        onFailure = { error ->
                            withContext(dispatchers.main) {
                                // saveStateError is the canonical surface for
                                // manual-save failures (sticky inline label on
                                // the Save button). Don't *also* fire the
                                // top-of-screen error toast — duplicate UX.
                                _state.update {
                                    it.copy(
                                        isSaveInProgress = false,
                                        saveStateError = "Failed to save: ${error.message}",
                                    )
                                }
                            }
                        },
                    )
                } else {
                    // No session — save state was serialized by the controller
                    withContext(dispatchers.main) {
                        _state.update { it.copy(statusMessage = "State saved", isSaveInProgress = false) }
                    }
                }
            } finally {
                staged.cleanup()
            }
        }
    }

    /**
     * Pulls the current save state out of the libretro core via the native
     * fast path when available — Android's [LibretroController.serializeToFile]
     * writes directly into a temp file in the saves dir, never allocating a
     * jbyteArray on the Java heap. We then read the temp file back into a
     * ByteArray for the upload call. Even though the read-back allocation
     * is the same size as the legacy in-memory serialize, the JNI handoff
     * no longer holds the buffer in two places at once — see #798.
     *
     * Falls back to [LibretroController.serialize] if the controller has no
     * native fast path (desktop / fakes).
     */
    /**
     * Result of [stageSaveToTempFile]. The file at [path] is owned by the
     * caller — they must invoke [cleanup] after the upload regardless of
     * outcome to avoid leaking temp files.
     */
    private data class StagedSave(val path: String, val size: Long, val cleanup: suspend () -> Unit)

    /**
     * Serialize the current core state to a temp file using the native
     * fast path when available. Returns the path + byte count for a
     * caller to stream-upload directly from disk; the JVM never holds
     * the full save in a ByteArray. See #798 (Dolphin GameCube ~90 MB).
     *
     * Returns null when serialization fails. When the controller has no
     * native fast path (desktop / fakes), this still works — it falls
     * back to [serialize] + writeFile so the caller's contract holds.
     */
    private suspend fun stageSaveToTempFile(): StagedSave? {
        val tempPath = "${fileStorage.getSavesDir()}/.tmp-save-${currentSessionId ?: "none"}"
        val cleanup: suspend () -> Unit = { runCatching { fileStorage.deleteFile(tempPath) } }
        val written = libretroController.serializeToFile(tempPath)
        if (written != null && written > 0) {
            return StagedSave(tempPath, written, cleanup)
        }
        // Native fast path unavailable — fall back to in-memory serialize
        // and write through. On JVM-only platforms (desktop) this is fine;
        // they have plenty of heap.
        val bytes = libretroController.serialize() ?: return null
        return runCatching {
            fileStorage.writeFile(tempPath, bytes)
            StagedSave(tempPath, bytes.size.toLong(), cleanup)
        }.getOrNull().also {
            if (it == null) cleanup()
        }
    }

    /**
     * Save state to a specific slot. Blocks in challenge/hardcore mode.
     * Uses the slot-specific API endpoint (PUT /api/sessions/:id/saves/slot/:slot).
     */
    fun saveToSlot(slot: Int) {
        if (_state.value.isChallengeMode) return
        if (_state.value.isHardcoreMode) {
            _state.update { it.copy(error = "Save states are disabled in hardcore mode") }
            return
        }
        if (rehearsalMode) {
            emitRehearsalBlocked(RehearsalSaveKind.Slot)
            return
        }
        scope.launch(dispatchers.io) {
            val staged = stageSaveToTempFile() ?: return@launch
            try {
                val sessionId = currentSessionId
                if (sessionId != null) {
                    val screenshot = screenshotCapture?.captureScreenshot()
                    sessionRepository.uploadSlotSaveFromFile(
                        sessionId, slot, staged.path, staged.size, screenshot, currentCoreName,
                    ).fold(
                        onSuccess = {
                            withContext(dispatchers.main) {
                                _state.update {
                                    it.copy(
                                        statusMessage = "Saved to slot $slot",
                                        secondaryToast = SecondaryToastData(
                                            message = "Saved to Slot $slot",
                                            type = SecondaryToastType.SAVE,
                                        ),
                                    )
                                }
                            }
                            refreshSaveSlots()
                        },
                        onFailure = { error ->
                            withContext(dispatchers.main) {
                                _state.update { it.copy(error = "Failed to save to slot $slot: ${error.message}") }
                            }
                        },
                    )
                } else {
                    withContext(dispatchers.main) {
                        _state.update { it.copy(statusMessage = "State saved") }
                    }
                }
            } finally {
                staged.cleanup()
            }
        }
    }

    /**
     * Load state from a specific slot. Blocks in challenge/hardcore mode.
     * Uses the slot-specific API endpoint (GET /api/sessions/:id/saves/slot/:slot).
     */
    fun loadFromSlot(slot: Int) {
        if (_state.value.isChallengeMode) return
        if (_state.value.isHardcoreMode) {
            _state.update { it.copy(error = "Save states are disabled in hardcore mode") }
            return
        }
        scope.launch(dispatchers.io) {
            val sessionId = currentSessionId
            if (sessionId != null) {
                sessionRepository.downloadSlotSave(sessionId, slot).fold(
                    onSuccess = { saveData ->
                        libretroController.unserialize(saveData)
                        withContext(dispatchers.main) {
                            _state.update {
                                it.copy(
                                    statusMessage = "Loaded from slot $slot",
                                    secondaryToast = SecondaryToastData(
                                        message = "Loaded Slot $slot",
                                        type = SecondaryToastType.LOAD,
                                    ),
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        withContext(dispatchers.main) {
                            _state.update { it.copy(error = "Failed to load from slot $slot: ${error.message}") }
                        }
                    },
                )
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
                val tempPath = "${fileStorage.getSavesDir()}/.tmp-load-manual-$sessionId"
                sessionRepository.downloadSessionAutoSaveToFile(sessionId, tempPath).fold(
                    onSuccess = {
                        libretroController.unserializeFromFile(tempPath)
                        runCatching { fileStorage.deleteFile(tempPath) }
                        withContext(dispatchers.main) {
                            _state.update {
                                it.copy(
                                    statusMessage = "State loaded",
                                    secondaryToast = SecondaryToastData(
                                        message = "Loaded Slot ${it.activeSlot}",
                                        type = SecondaryToastType.LOAD,
                                    ),
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        runCatching { fileStorage.deleteFile(tempPath) }
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

    /**
     * Update the session's core name on the server.
     * Called at emulation start so the session tracks which core is being used,
     * even if the game crashes before any save is uploaded.
     */
    suspend fun updateSessionCoreName() {
        val sessionId = currentSessionId ?: return
        if (currentCoreName.isEmpty()) return
        runCatching {
            sessionRepository.updateSession(sessionId, coreName = currentCoreName)
        }
    }

    /**
     * Fetch save states for the current session and update the save slot map.
     * Each save state with a non-null slot is mapped to its corresponding slot number.
     * Called on game start and after manual save operations to keep thumbnails fresh.
     */
    fun refreshSaveSlots() {
        val sessionId = currentSessionId ?: return
        scope.launch(dispatchers.io) {
            sessionRepository.getSessionSaves(sessionId).onSuccess { saves ->
                val slotMap = mutableMapOf<Int, SaveSlotInfo>()
                for (save in saves) {
                    val slot = save.slot ?: continue
                    // Only keep the most recent save per slot (saves are ordered by time from server)
                    if (slot in 1..10 && slot !in slotMap) {
                        slotMap[slot] = SaveSlotInfo(
                            screenshotUrl = save.screenshotUrl,
                            timestamp = save.createdAt?.let { instant ->
                                val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                                "%02d:%02d".format(local.hour, local.minute)
                            },
                            isFilled = true,
                        )
                    }
                }
                withContext(dispatchers.main) {
                    _state.update { it.copy(saveSlots = slotMap) }
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
