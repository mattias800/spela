package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.domain.controller.ScreenshotCapture
import com.spela.player.domain.model.PENDING_SAVE_UPLOAD_STUCK_RETRY_THRESHOLD
import com.spela.player.domain.model.PendingSaveUpload
import com.spela.player.domain.model.PendingUploadKind
import com.spela.player.domain.repository.PendingSaveUploadRepository
import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.domain.repository.SessionRepository
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.state.SaveSlotInfo
import com.spela.player.presentation.state.SecondaryToastData
import com.spela.player.presentation.state.SecondaryToastType
import com.spela.player.util.DispatcherProvider
import com.spela.player.util.FileStorage
import com.spela.player.util.spelaLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

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
    /**
     * Persistent FIFO queue used by the deferred-sync path (#804
     * phase 6). Manual save taps stage to disk, enqueue here, and
     * return immediately — the user isn't blocked on the network
     * round-trip. The drain runs opportunistically right after each
     * enqueue (slice 2); future slices add explicit triggers (game
     * exit, app pause, network reconnect).
     */
    private val pendingUploadRepository: PendingSaveUploadRepository,
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

        // Wait for any in-flight drain from the previous game to complete
        // before downloading the auto-save. Without this, when the new game
        // reuses the same session ID (ensureSession's "reuse most recent"
        // path), the drain's upload of the old game's final save can race
        // with this download — the user could resume from a stale state and
        // the drain's upload could then overwrite the server's auto-save
        // with an older snapshot, silently losing forward progress on the
        // next launch. See #997.
        drainMutex.withLock { /* await drain completion */ }

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
                if (ok) {
                    AutoLoadResult.Loaded
                } else {
                    AutoLoadResult.Error("Save state could not be loaded by the core")
                }
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
     * Pulls the session's save_dir bundle from the server and untars it
     * into [destDir]. Called by the emulation start flow before loadCore
     * so cores that scan their save_dir on init (ScummVM, DOSBox) see
     * their previously-saved files. Best-effort: any failure leaves the
     * dir in whatever state it's in (typically empty for a fresh session
     * or a session whose previous run wrote nothing). See #864.
     */
    /**
     * Returns the on-disk save_dir path for the given [sessionId] (creating
     * the directory if missing) or null when no session is active. Used by
     * the emulation start flow to compute the path that's passed to
     * [LibretroController.loadCore]. See #864.
     */
    suspend fun getSessionSaveDir(sessionId: String?): String? {
        if (sessionId == null) return null
        val path = "${fileStorage.getSavesDir()}/sessions/$sessionId"
        fileStorage.createDirectory(path)
        return path
    }

    suspend fun populateSessionSaveDir(sessionId: String?, destDir: String) {
        if (sessionId == null) return
        val tarPath = "${fileStorage.getSavesDir()}/.tmp-savedir-load-$sessionId.tar"
        try {
            val result = sessionRepository.downloadSessionSaveDirBundleToFile(
                sessionId, fileStorage, tarPath,
            )
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: ""
                if (msg.contains("404")) {
                    println("[SaveManager] populateSessionSaveDir: no bundle yet for session $sessionId")
                } else {
                    println("[SaveManager] populateSessionSaveDir: download failed: $msg")
                }
                return
            }
            val tarSize = runCatching { fileStorage.getFileSize(tarPath) }.getOrDefault(0L)
            if (tarSize <= 0L) {
                println("[SaveManager] populateSessionSaveDir: empty bundle for session $sessionId, leaving dir as-is")
                return
            }
            try {
                fileStorage.extractTarFile(tarPath, destDir)
                println("[SaveManager] populateSessionSaveDir: extracted ${tarSize}B bundle into $destDir")
            } catch (e: Exception) {
                println("[SaveManager] populateSessionSaveDir: extract failed: ${e.message}")
            }
        } finally {
            runCatching { fileStorage.deleteFile(tarPath) }
        }
    }

    /**
     * Tars the session's on-disk save_dir and uploads the bundle to the
     * server, atomically replacing any previous bundle. Called by the
     * emulation stop flow after the libretro auto-save has been written.
     * Best-effort: a failure leaves the local dir intact (it's the source
     * of truth on this device until the next successful upload). See #864.
     */
    private suspend fun saveDirOnStop(sessionId: String) {
        val srcDir = "${fileStorage.getSavesDir()}/sessions/$sessionId"
        val tarPath = "${fileStorage.getSavesDir()}/.tmp-savedir-upload-$sessionId.tar"
        try {
            val tarSize = try {
                fileStorage.tarDirectoryToFile(srcDir, tarPath)
            } catch (e: Exception) {
                println("[SaveManager] saveDirOnStop: tar failed: ${e.message}")
                return
            }
            if (tarSize <= 0L) {
                println("[SaveManager] saveDirOnStop: empty dir, nothing to upload for session $sessionId")
                return
            }
            val result = sessionRepository.uploadSessionSaveDirBundleFromFile(
                sessionId, tarPath, tarSize,
            )
            if (result.isSuccess) {
                println("[SaveManager] saveDirOnStop: uploaded ${tarSize}B bundle for session $sessionId")
            } else {
                val msg = result.exceptionOrNull()?.message ?: "unknown"
                println("[SaveManager] saveDirOnStop: upload failed: $msg")
            }
        } finally {
            runCatching { fileStorage.deleteFile(tarPath) }
        }
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
            // Some cores (ScummVM, DOSBox) declare savestate=false and
            // return null here. They still need their on-disk save_dir
            // bundle uploaded, so don't bail before saveDirOnStop. See
            // #864 — save_dir is the parallel persistent path that's
            // independent of the libretro memory snapshot.
            currentSessionId?.let { saveDirOnStop(it) }
            return
        }
        println("[SaveManager] autoSaveOnStop: staged ${staged.size} bytes at ${staged.path}, queueing...")
        val sessionId = currentSessionId
        if (sessionId != null) {
            try {
                enqueueStagedSave(
                    sessionId = sessionId,
                    kind = PendingUploadKind.Auto,
                    slot = null,
                    name = "Auto Save",
                    staged = staged,
                )
                withContext(dispatchers.main) {
                    _state.update { it.copy(hasPendingUploads = true) }
                }
            } catch (e: Exception) {
                staged.cleanup()
                println("[SaveManager] autoSaveOnStop: queue failed: ${e.message}")
                withContext(dispatchers.main) {
                    _state.update { it.copy(error = "Auto-save queue failed: ${e.message}") }
                }
            }
        } else {
            staged.cleanup()
        }
        // Game-exit drain (#804 phase 6 slice 3 spec point a). The
        // auto-save itself is queued above, and any manual/slot saves
        // queued during gameplay are still on disk waiting for an
        // opportunity. This is that opportunity.
        drainPendingUploads()

        // Per-session save_dir bundle (#864). Done after the libretro
        // auto-save so the in-memory snapshot wins if both succeed —
        // some cores write to BOTH (DOSBox writes config to save_dir
        // but its memory state lives in the libretro state).
        currentSessionId?.let { saveDirOnStop(it) }
    }

    /**
     * Manual save state. Blocks in challenge mode and hardcore mode.
     * When no session is active, delegates to the libretro controller directly.
     */
    private companion object {
        /** How long the "Saved" checkmark stays on the in-game overlay's
         *  Save button after a successful upload (#803). */
        const val SAVE_SUCCESS_FLASH_MS = 1_500L

        /**
         * Saves smaller than this don't get gzipped — for a few KB of
         * SRAM-sized state, the deflater frame and CPU spend cost more
         * than the bandwidth they save. 32 KiB happens to sit between
         * a fully-loaded NES state (~4-8 KB) and the smallest SNES
         * state. See #804 phase 2.
         */
        const val GZIP_MIN_BYTES: Long = 32 * 1024L

        const val SAVE_SYNC_LOG_TAG = "SaveSync"
    }


    private fun saveSyncLog(message: String) {
        spelaLog(SAVE_SYNC_LOG_TAG, message)
    }

    /**
     * Serialises drain calls so concurrent triggers (e.g. game-exit
     * + network-reconnect arriving in the same second) don't kick
     * off two drain loops that both pick up the same row and
     * upload it twice. tryLock semantics — the second call is a
     * no-op rather than a wait, because the in-flight drain already
     * sees any rows the second trigger would have picked up. See
     * #804 phase 6 slice 3.
     */
    private val drainMutex = Mutex()

    /**
     * Public entry point for the drain triggers (game exit, app
     * pause, network reconnect, post-enqueue immediate drain).
     * Idempotent: when the queue is empty the drain coroutine flips
     * [EmulationState.hasPendingUploads] to false and exits. When a
     * drain is already in flight, the call is a no-op (the in-flight
     * drain will pick up any newly-enqueued rows on its next
     * iteration). See #804 phase 6 slice 3.
     */
    fun drainPendingUploads(showCompletionFeedback: Boolean = true) {
        launchDrainPendingUploads(showCompletionFeedback)
    }

    /**
     * Subscribes to [ConnectivityMonitor.onReconnect] and triggers a
     * drain on every reconnect. Called once by the DI module after
     * construction; tests don't call this so the long-lived collector
     * doesn't keep their `runTest` scopes from completing. See #804
     * phase 6 slice 3 spec point (c).
     */
    fun startReconnectListener() {
        scope.launch(dispatchers.io) {
            connectivityMonitor.onReconnect.collect {
                saveSyncLog("reconnect_drain")
                drainPendingUploads()
            }
        }
    }

    /**
     * Stage a manual save to disk and enqueue it for deferred upload.
     *
     * [name] is the user-visible label that ends up on the
     * `SessionSaveState` row server-side. Defaults to "Manual Save"
     * for the historical small-tier flow. Medium-tier consoles can
     * pass a user-chosen name from the "Save with name…" dialog
     * (#830) so power users get their pre-boss-fight markers without
     * the slot grid going stale.
     */
    private suspend fun enqueueStagedSave(
        sessionId: String,
        kind: PendingUploadKind,
        slot: Int?,
        name: String,
        staged: StagedSave,
    ): Long {
        val screenshot = screenshotCapture?.captureScreenshot()
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        var screenshotPath: String? = null
        try {
            screenshotPath = if (screenshot != null) {
                runCatching {
                    val suffix = Random.nextLong(0, Long.MAX_VALUE)
                    val path = "${fileStorage.getSavesDir()}/.tmp-shot-$sessionId-${kind.apiId}-$now-$suffix.png"
                    fileStorage.writeFile(path, screenshot)
                    path
                }.onFailure { e ->
                    saveSyncLog(
                        "screenshot_stage_failure kind=${kind.apiId} session=$sessionId " +
                            "error=${e.message ?: e::class.simpleName}",
                    )
                }.getOrNull()
            } else {
                null
            }
            val rowId = pendingUploadRepository.enqueue(
                sessionId = sessionId,
                kind = kind,
                slot = slot,
                name = name,
                coreName = currentCoreName,
                compression = staged.compression,
                filePath = staged.path,
                fileSize = staged.size,
                screenshotPath = screenshotPath,
                createdAt = now,
            )
            saveSyncLog(
                "enqueue id=$rowId kind=${kind.apiId} session=$sessionId " +
                    "size=${staged.size} retry=0",
            )
            return rowId
        } catch (e: Exception) {
            screenshotPath?.let { path -> runCatching { fileStorage.deleteFile(path) } }
            saveSyncLog(
                "enqueue_failure kind=${kind.apiId} session=$sessionId " +
                    "size=${staged.size} error=${e.message ?: e::class.simpleName}",
            )
            throw e
        }
    }

    fun saveState(name: String = "Manual Save") {
        if (_state.value.isChallengeMode) return
        if (_state.value.isHardcoreMode) {
            _state.update { it.copy(error = "Save states are disabled in hardcore mode") }
            return
        }
        if (rehearsalMode) {
            emitRehearsalBlocked(RehearsalSaveKind.Manual)
            return
        }
        // Inline button feedback: tap → "Saving…" immediately so the
        // user knows the tap registered. The save flips to
        // "Saved locally · syncing" the moment the bytes are on disk
        // and queued (~milliseconds), and to idle once the upload
        // drain finishes. Clears any stale error from a prior failed
        // attempt. See #803 + #804 phase 6.
        _state.update {
            it.copy(
                isSaveInProgress = true,
                saveStateError = null,
                saveStateJustSucceeded = false,
            )
        }
        scope.launch(dispatchers.io) {
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
            val sessionId = currentSessionId
            if (sessionId == null) {
                // No session — save state was serialized by the
                // controller but we have nothing to upload. Treat as
                // immediately complete; the staged temp file is
                // unused.
                staged.cleanup()
                withContext(dispatchers.main) {
                    _state.update { it.copy(statusMessage = "State saved", isSaveInProgress = false) }
                }
                return@launch
            }
            try {
                enqueueStagedSave(
                    sessionId = sessionId,
                    kind = PendingUploadKind.Manual,
                    slot = null,
                    // Caller-supplied label for the save row (#830). Empty /
                    // blank falls back to the historical placeholder so the
                    // server never receives a row with no human-readable
                    // label.
                    name = name.ifBlank { "Manual Save" },
                    staged = staged,
                )
            } catch (e: Exception) {
                staged.cleanup()
                withContext(dispatchers.main) {
                    _state.update {
                        it.copy(
                            isSaveInProgress = false,
                            saveStateError = "Failed to queue save: ${e.message}",
                        )
                    }
                }
                return@launch
            }
            withContext(dispatchers.main) {
                _state.update {
                    it.copy(
                        isSaveInProgress = false,
                        hasPendingUploads = true,
                        secondaryToast = SecondaryToastData(
                            message = "Saved locally — syncing",
                            type = SecondaryToastType.SAVE,
                        ),
                    )
                }
            }
            // Fire-and-forget drain. Slice 3 will hook this same call
            // to game-exit / app-pause / network-reconnect triggers
            // for the cases where the immediate-after-enqueue drain
            // didn't manage to run (e.g. lost connectivity at save
            // time).
            launchDrainPendingUploads()
        }
    }

    /**
     * Walks the pending-upload queue oldest-first, uploading each row
     * via the appropriate session endpoint. Stops on first failure to
     * avoid an infinite retry storm; retry-counter + last_error are
     * persisted on the row so the next drain can pick up where this
     * one left off. Clears [EmulationState.hasPendingUploads] when the
     * queue empties. See #804 phase 6.
     */
    private fun launchDrainPendingUploads(showCompletionFeedback: Boolean = true) {
        scope.launch(dispatchers.io) {
            // tryLock so a redundant trigger (e.g. reconnect arrives
            // while the post-enqueue drain is still running) is a
            // cheap no-op instead of a parallel drain that would
            // race on the same rows.
            if (!drainMutex.tryLock()) {
                saveSyncLog("drain_skip reason=already_running")
                return@launch
            }
            saveSyncLog("drain_start")
            pendingUploadRepository.setDraining(true)
            try {
                while (true) {
                    val pending = pendingUploadRepository.getAll()
                    publishStuckCount(pending)
                    if (pending.isEmpty()) {
                        saveSyncLog("empty_queue")
                        withContext(dispatchers.main) {
                            _state.update {
                                it.copy(
                                    hasPendingUploads = false,
                                    saveStateJustSucceeded = if (showCompletionFeedback) {
                                        true
                                    } else {
                                        it.saveStateJustSucceeded
                                    },
                                    stuckUploadCount = 0,
                                )
                            }
                        }
                        refreshSaveSlots()
                        if (showCompletionFeedback) {
                            scope.launch(dispatchers.io) {
                                kotlinx.coroutines.delay(SAVE_SUCCESS_FLASH_MS)
                                withContext(dispatchers.main) {
                                    _state.update { it.copy(saveStateJustSucceeded = false) }
                                }
                            }
                        }
                        return@launch
                    }
                    val row = pending.first()
                    val screenshot: ByteArray? = row.screenshotPath?.let { path ->
                        runCatching { fileStorage.readFile(path) }.getOrNull()
                    }
                    saveSyncLog(
                        "row_upload_start id=${row.id} kind=${row.kind.apiId} " +
                            "session=${row.sessionId} size=${row.fileSize} retry=${row.retryCount}",
                    )
                    val result = uploadPendingRow(row, screenshot)
                    if (result.isSuccess) {
                        saveSyncLog("row_upload_success id=${row.id} kind=${row.kind.apiId}")
                        pendingUploadRepository.delete(row.id)
                        saveSyncLog("delete id=${row.id}")
                        runCatching { fileStorage.deleteFile(row.filePath) }
                        row.screenshotPath?.let {
                            runCatching { fileStorage.deleteFile(it) }
                        }
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "upload failed"
                        saveSyncLog(
                            "row_upload_failure id=${row.id} kind=${row.kind.apiId} " +
                                "error=$msg",
                        )
                        pendingUploadRepository.markRetry(row.id, msg)
                        saveSyncLog(
                            "retry_marked id=${row.id} retry=${row.retryCount + 1} " +
                                "error=$msg",
                        )
                        // Re-read the queue so the stuck count
                        // reflects the bumped retry counter on the
                        // failed row before we exit the drain loop.
                        publishStuckCount(pendingUploadRepository.getAll())
                        return@launch
                    }
                }
            } finally {
                pendingUploadRepository.setDraining(false)
                drainMutex.unlock()
            }
        }
    }

    /**
     * Pushes the current "stuck" count to [EmulationState] so the
     * in-game overlay can surface a "Sync paused — N saves waiting"
     * line. A row is stuck once its retry counter reaches
     * [PENDING_SAVE_UPLOAD_STUCK_RETRY_THRESHOLD] — three transient failures is enough
     * to suspect a server-side issue (quota, 5xx) rather than just
     * packet loss. See #804 phase 6 slice 4.
     */
    private suspend fun publishStuckCount(
        pending: List<PendingSaveUpload>,
    ) {
        val stuck = pending.count { it.retryCount >= PENDING_SAVE_UPLOAD_STUCK_RETRY_THRESHOLD }
        withContext(dispatchers.main) {
            _state.update { it.copy(stuckUploadCount = stuck) }
        }
    }

    /**
     * Routes a queued row to the correct session endpoint based on
     * its [PendingUploadKind]. Kept
     * here rather than on the repository so the SQL layer stays
     * upload-agnostic.
     */
    private suspend fun uploadPendingRow(
        row: PendingSaveUpload,
        screenshot: ByteArray?,
    ): Result<Unit> = when (row.kind) {
        PendingUploadKind.Manual ->
            sessionRepository.uploadSessionSaveFromFile(
                sessionId = row.sessionId,
                name = row.name,
                savePath = row.filePath,
                saveSize = row.fileSize,
                screenshot = screenshot,
                coreName = row.coreName,
                compression = row.compression,
            ).map { Unit }
        PendingUploadKind.Auto ->
            sessionRepository.uploadSessionAutoSaveFromFile(
                sessionId = row.sessionId,
                savePath = row.filePath,
                saveSize = row.fileSize,
                screenshot = screenshot,
                coreName = row.coreName,
                compression = row.compression,
            )
        PendingUploadKind.Slot ->
            sessionRepository.uploadSlotSaveFromFile(
                sessionId = row.sessionId,
                slot = row.slot ?: 1,
                savePath = row.filePath,
                saveSize = row.fileSize,
                screenshot = screenshot,
                coreName = row.coreName,
                compression = row.compression,
            ).map { Unit }
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
     * outcome to avoid leaking temp files. [compression] is the codec
     * applied to the bytes at [path] (`""` raw or `"gzip"`); see #804.
     */
    private data class StagedSave(
        val path: String,
        val size: Long,
        val compression: String,
        val cleanup: suspend () -> Unit,
    )

    /**
     * Serialize the current core state to a temp file using the native
     * fast path when available. Returns the path + byte count for a
     * caller to stream-upload directly from disk; the JVM never holds
     * the full save in a ByteArray. See #798 (Dolphin GameCube ~90 MB).
     *
     * Returns null when serialization fails. When the controller has no
     * native fast path (desktop / fakes), this still works — it falls
     * back to [serialize] + writeFile so the caller's contract holds.
     *
     * After the raw bytes are on disk we gzip them in a sibling file and
     * upload the compressed copy, tagging it with `compression="gzip"`
     * (#804 phase 2). Tiny saves below [GZIP_MIN_BYTES] skip gzip — the
     * deflater's frame overhead can outweigh the saving on a few KB SRAM-
     * sized states. If gzip itself fails for any reason we fall back to
     * uploading the uncompressed staging file so a transient I/O error
     * never blocks a save.
     */
    private suspend fun stageSaveToTempFile(): StagedSave? {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val suffix = Random.nextLong(0, Long.MAX_VALUE)
        val tempPath = "${fileStorage.getSavesDir()}/.tmp-save-${currentSessionId ?: "none"}-$now-$suffix"
        val cleanupRaw: suspend () -> Unit = { runCatching { fileStorage.deleteFile(tempPath) } }
        val rawSize: Long? = run {
            val written = libretroController.serializeToFile(tempPath)
            if (written != null && written > 0) {
                written
            } else {
                // Native fast path unavailable — fall back to in-memory
                // serialize and write through. On JVM-only platforms
                // (desktop) this is fine; they have plenty of heap.
                val bytes = libretroController.serialize() ?: return@run null
                runCatching {
                    fileStorage.writeFile(tempPath, bytes)
                    bytes.size.toLong()
                }.getOrNull()
            }
        }
        if (rawSize == null) {
            cleanupRaw()
            return null
        }
        return maybeGzipStaged(tempPath, rawSize, cleanupRaw)
    }

    /**
     * Attempts to gzip the staged save at [rawPath]. Returns a [StagedSave]
     * pointed at the gzipped sibling on success, or one pointed at the
     * raw file on any failure / when the input is too small to be worth
     * compressing. The cleanup closure deletes whichever temp files the
     * pipeline ended up creating.
     */
    private suspend fun maybeGzipStaged(
        rawPath: String,
        rawSize: Long,
        cleanupRaw: suspend () -> Unit,
    ): StagedSave {
        if (rawSize < GZIP_MIN_BYTES) {
            return StagedSave(rawPath, rawSize, "", cleanupRaw)
        }
        val gzPath = "$rawPath.gz"
        val cleanupGz: suspend () -> Unit = { runCatching { fileStorage.deleteFile(gzPath) } }
        val cleanupBoth: suspend () -> Unit = { cleanupRaw(); cleanupGz() }
        val gzSize = runCatching {
            com.spela.player.util.gzipFile(rawPath, gzPath)
        }.getOrElse { e ->
            println("[SaveManager] gzipFile failed (${e.message}); uploading raw")
            cleanupGz()
            return StagedSave(rawPath, rawSize, "", cleanupRaw)
        }
        if (gzSize <= 0) {
            cleanupGz()
            return StagedSave(rawPath, rawSize, "", cleanupRaw)
        }
        // Gzip succeeded — drop the raw bytes immediately. The
        // returned StagedSave references one file (the .gz), so the
        // deferred-upload queue can hand off a single path/cleanup
        // pair without leaking the raw sibling. See #804 phase 6.
        cleanupRaw()
        return StagedSave(gzPath, gzSize, "gzip", cleanupGz)
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
            val sessionId = currentSessionId
            if (sessionId != null) {
                try {
                    enqueueStagedSave(
                        sessionId = sessionId,
                        kind = PendingUploadKind.Slot,
                        slot = slot,
                        name = "Slot $slot",
                        staged = staged,
                    )
                } catch (e: Exception) {
                    staged.cleanup()
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = "Failed to queue slot $slot: ${e.message}") }
                    }
                    return@launch
                }
                withContext(dispatchers.main) {
                    _state.update {
                        it.copy(
                            statusMessage = "Saved to slot $slot",
                            hasPendingUploads = true,
                            secondaryToast = SecondaryToastData(
                                message = "Saved to Slot $slot - syncing",
                                type = SecondaryToastType.SAVE,
                            ),
                        )
                    }
                }
                launchDrainPendingUploads()
            } else {
                staged.cleanup()
                withContext(dispatchers.main) {
                    _state.update { it.copy(statusMessage = "State saved") }
                }
            }
        }
    }

    /**
     * Load state from a specific slot. Blocks in challenge/hardcore mode.
     * Uses the slot-specific API endpoint (GET /api/sessions/:id/saves/slot/:slot).
     *
     * Streams the body to a temp file and hands it to
     * [LibretroController.unserializeFromFile]. The streaming path is
     * what reads the server's `X-Compression: gzip` header and inflates
     * the body inline (#804 phase 2) — calling the in-memory
     * `downloadSlotSave` ByteArray API here would feed gzipped bytes
     * straight to the core for any save uploaded by a #804-aware
     * client.
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
                val tempPath = "${fileStorage.getSavesDir()}/.tmp-load-slot-$slot-$sessionId"
                sessionRepository.downloadSlotSaveToFile(sessionId, slot, tempPath).fold(
                    onSuccess = {
                        libretroController.unserializeFromFile(tempPath)
                        runCatching { fileStorage.deleteFile(tempPath) }
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
                        runCatching { fileStorage.deleteFile(tempPath) }
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
                                // The "Load" button (no slot selected) loads
                                // the auto-save endpoint, not a slot — so the
                                // toast must say so. Slot-based loads use
                                // loadFromSlot() instead. See #996.
                                it.copy(
                                    statusMessage = "State loaded",
                                    secondaryToast = SecondaryToastData(
                                        message = "Auto-save loaded",
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
    /**
     * Rename a single slot save. Hits PUT /api/sessions/{id}/saves/{saveId}
     * with the new name and updates the local saveSlots map optimistically.
     * Used by the in-game slot manage UI (#831). Failures roll back the
     * optimistic update — the slot picker will show the old name again so
     * the user knows the change didn't stick.
     */
    fun renameSlot(slot: Int, name: String) {
        val sessionId = currentSessionId ?: return
        val current = _state.value.saveSlots
        val info = current[slot] ?: return
        val saveId = info.saveId ?: return
        val previous = info
        // Optimistic update — flip the local name immediately so the user
        // sees the rename without waiting for the round-trip.
        _state.update {
            it.copy(saveSlots = it.saveSlots + (slot to info.copy(name = name.takeIf { it.isNotBlank() })))
        }
        scope.launch(dispatchers.io) {
            sessionRepository.updateSessionSave(sessionId, saveId, name).onFailure {
                // Roll back to the previous name.
                _state.update { state ->
                    state.copy(saveSlots = state.saveSlots + (slot to previous))
                }
            }
        }
    }

    /**
     * Delete a single slot save. Hits DELETE /api/sessions/{id}/saves/{saveId}
     * and clears the slot from the local map on success. Used by the
     * in-game slot manage UI (#831). Failures restore the slot.
     */
    fun deleteSlot(slot: Int) {
        val sessionId = currentSessionId ?: return
        val info = _state.value.saveSlots[slot] ?: return
        val saveId = info.saveId ?: return
        val previous = info
        // Optimistic clear — picker re-renders the cell as empty
        // immediately. Restored on failure.
        _state.update {
            it.copy(saveSlots = it.saveSlots - slot)
        }
        scope.launch(dispatchers.io) {
            sessionRepository.deleteSessionSave(sessionId, saveId).onFailure {
                _state.update { state ->
                    state.copy(saveSlots = state.saveSlots + (slot to previous))
                }
            }
        }
    }

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
                            saveId = save.id,
                            name = save.name.takeIf { it.isNotBlank() },
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
