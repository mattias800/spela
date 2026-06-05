package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.DownloadResult
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.GameDto
import com.spela.player.domain.model.DownloadFailureReason
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.util.FileStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class DownloadRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val fileStorage: FileStorage,
    private val database: SpelaDatabase,
) : DownloadRepository {

    private val downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    private val _downloadedGames = MutableStateFlow<List<DownloadedGame>>(emptyList())

    /** Per-game rolling speed calculator. Created on first in-flight
     *  emission for a game id, removed when the download enters a
     *  terminal state (COMPLETED / FAILED) so a re-download starts
     *  fresh. See [SpeedTracker] and #801. */
    private val speedTrackers = mutableMapOf<String, SpeedTracker>()

    /** Tracks the in-flight coroutine [Job] for each active download so
     *  [cancelDownload] can actually interrupt it (the call originates
     *  from a different scope than the one that started [downloadGame],
     *  so we can't reach the job via [coroutineContext]). The map only
     *  holds entries while a download is between QUEUED and a terminal
     *  state — every code path that exits [downloadGame] removes its
     *  entry in a finally block. See #845. */
    private val activeJobs = mutableMapOf<String, Job>()

    // The speedTrackers / activeJobs / lastEmittedBytes maps are
    // accessed concurrently from progress callbacks invoked on the
    // IO dispatcher's thread pool — multiple simultaneous downloads
    // race on every map mutation. Pre-#957 these were unsynchronized
    // mutableMapOf and could throw ConcurrentModificationException
    // or silently lose writes (the lost-write on lastEmittedBytes
    // directly broke the monotonic guard from #931).
    //
    // @Synchronized provides a per-instance monitor — every helper
    // below acquires it before touching any map, and that's enough
    // because no helper holds the lock across a suspension point.
    @Synchronized
    private fun recordSpeed(gameId: String, bytesDownloaded: Long): Long =
        speedTrackers.getOrPut(gameId) { SpeedTracker() }.record(bytesDownloaded)

    @Synchronized
    private fun resetSpeed(gameId: String) {
        speedTrackers.remove(gameId)
        // Reset the monotonic high-water mark together with the speed
        // tracker — both have the same lifecycle (cleared whenever a
        // download terminates: completed, failed, or cancelled).
        lastEmittedBytes.remove(gameId)
    }

    /*
     * High-water mark per active download — defends against ANY future
     * unit mismatch making the bar visibly retreat. The unit fix in
     * #931 (logical-bytes tar parser + X-Logical-Size header) removes
     * the known sources of backward jumps, but a monotonic clamp here
     * is cheap insurance against regressions.
     *
     * Reset in cleanupPartialDownload() and on COMPLETED so the next
     * download for the same game starts at 0.
     */
    private val lastEmittedBytes = mutableMapOf<String, Long>()

    @Synchronized
    private fun monotonicBytes(gameId: String, candidate: Long): Long {
        val prev = lastEmittedBytes[gameId] ?: 0L
        val next = maxOf(prev, candidate)
        lastEmittedBytes[gameId] = next
        return next
    }

    @Synchronized
    private fun setActiveJob(gameId: String, job: Job) {
        activeJobs[gameId] = job
    }

    @Synchronized
    private fun takeActiveJob(gameId: String): Job? {
        return activeJobs.remove(gameId)
    }

    init {
        refreshDownloadedGames()
    }

    /**
     * Launch-time download reconciliation (call exactly once from the DI graph):
     *
     * 1. **Restores resumable partials** ([PartialDownloadEntity]) into the
     *    in-memory state as PAUSED so the UI offers Resume after an app restart,
     *    with progress reflecting the bytes already on disk. (#1296)
     * 2. **Deletes orphan directories** under `getGamesDir()` that have neither a
     *    completed-download row nor a resumable-partial row — leftovers from
     *    process death/force-stop that aborted [downloadGame] before either row
     *    was written. Without this they'd sit on disk forever (no UI lists them).
     *
     * Idempotent: running twice produces the same result. See #845, #1296.
     */
    override suspend fun scanForOrphanedDownloads() {
        // (1) Restore resumable partials into the in-memory state. Their dirs are
        // also protected from the orphan sweep below.
        val partials = database.spelaDatabaseQueries.getAllPartialDownloads().executeAsList()
        for (p in partials) {
            val onDisk = if (fileStorage.fileExists(p.local_path)) fileStorage.getFileSize(p.local_path) else 0L
            val reason = p.failure_reason?.let { runCatching { DownloadFailureReason.valueOf(it) }.getOrNull() }
            // Seed the monotonic high-water so a subsequent resume doesn't snap
            // the bar back to 0 before its first chunk lands.
            monotonicBytes(p.game_id, onDisk)
            downloads.update {
                it + (p.game_id to DownloadProgress(
                    gameId = p.game_id,
                    gameTitle = p.game_title,
                    state = DownloadState.PAUSED,
                    bytesDownloaded = onDisk,
                    totalBytes = if (p.expected_size > 0) p.expected_size else -1L,
                    failureReason = reason,
                ))
            }
        }

        // (2) Sweep orphan dirs. A dir is tracked if it's a completed download
        // OR has a resumable partial — neither should be deleted.
        val gamesDir = fileStorage.getGamesDir()
        if (!fileStorage.fileExists(gamesDir)) return
        val tracked = (
            database.spelaDatabaseQueries.getAllDownloads().executeAsList().map { it.game_id } +
                partials.map { it.game_id }
            ).toSet()
        val onDisk = try {
            fileStorage.listFiles(gamesDir)
        } catch (e: Exception) {
            println("[Download] scanForOrphanedDownloads: listFiles failed: ${e.message}")
            return
        }
        for (gameId in onDisk) {
            if (gameId !in tracked) {
                val orphan = "$gamesDir/$gameId"
                println("[Download] scanForOrphanedDownloads: removing orphan $orphan")
                try {
                    if (fileStorage.isDirectory(orphan)) {
                        fileStorage.deleteDirectory(orphan)
                    } else {
                        fileStorage.deleteFile(orphan)
                    }
                } catch (e: Exception) {
                    println("[Download] scanForOrphanedDownloads: failed to delete $orphan: ${e.message}")
                }
            }
        }
    }

    private fun refreshDownloadedGames() {
        val allDownloads = database.spelaDatabaseQueries.getAllDownloads().executeAsList()
        _downloadedGames.value = allDownloads.map { dl ->
            val cachedGame = try {
                database.spelaDatabaseQueries.getCachedGame(dl.game_id).executeAsOneOrNull()
            } catch (_: Exception) { null }
            DownloadedGame(
                gameId = dl.game_id,
                title = cachedGame?.title ?: "Game ${dl.game_id}",
                consoleName = cachedGame?.console_name ?: "",
                coverUrl = cachedGame?.cover_url,
                fileSizeBytes = dl.file_size,
                downloadedAt = dl.downloaded_at,
            )
        }
    }

    override fun observeDownloads(): Flow<List<DownloadProgress>> =
        downloads.map { it.values.toList() }

    override fun observeDownload(gameId: String): Flow<DownloadProgress> =
        downloads.map { map ->
            map[gameId] ?: DownloadProgress(gameId = gameId, state = DownloadState.IDLE)
        }

    override fun observeDownloadedGames(): Flow<List<DownloadedGame>> = _downloadedGames

    override suspend fun downloadGame(gameId: String, gameTitle: String): Result<String> {
        // Capture the caller's Job so cancelDownload can interrupt it.
        // The download work below runs on the SAME coroutine, so cancelling
        // this Job is what actually aborts the in-flight HTTP fetch.
        setActiveJob(gameId, coroutineContext[Job]!!)
        return runCatching {
            downloads.update { it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.QUEUED)) }
            downloads.update { it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING)) }

            // Check if this is a multi-disc game by fetching game detail
            val gameDetail = apiClient.getGameDetail(gameId)
            println("[Download] Game detail: fileName=${gameDetail.fileName} fileSize=${gameDetail.fileSize} discCount=${gameDetail.discCount}")

            val discs = gameDetail.discs
            if (gameDetail.discCount >= 2) {
                downloadMultiDiscGame(gameId, gameTitle, gameDetail)
            } else if (discs.isNotEmpty() && discs.size == 1) {
                // Single disc with disc record — use disc endpoint (handles .cue tar bundles)
                downloadSingleDiscWithDiscRecord(gameId, gameTitle, gameDetail)
            } else if (isTarBundledByServer(gameDetail.fileName)) {
                // Server packages these as a tar of the directory:
                //   - .cue / .gdi without disc records (old DB entries)
                //   - .scummvm (entire directory bundled)
                // The game endpoint returns application/x-tar with no Content-Length,
                // so we stream-extract instead of comparing bytes against game.fileSize
                // (which is the on-disk size of the .scummvm marker file or .cue file
                // alone — much smaller than the tar).
                downloadTarBundledGameFromGameEndpoint(gameId, gameTitle, gameDetail.fileSize)
            } else {
                downloadSingleDiscGame(gameId, gameTitle, gameDetail.fileName, gameDetail.fileSize)
            }
        }.onSuccess { path ->
            val fileSize = try { fileStorage.getDirectorySize(fileStorage.getGamesDir() + "/$gameId") } catch (_: Exception) { 0L }
            database.spelaDatabaseQueries.insertDownload(gameId, path, fileSize, nowMillis())
            // The download finished — drop any resumable-partial record so the
            // game reads as fully cached, not paused. (#1296)
            database.spelaDatabaseQueries.deletePartialDownload(gameId)
            refreshDownloadedGames()
            takeActiveJob(gameId)
        }.onFailure { error ->
            handleDownloadFailure(gameId, gameTitle, error)
        }
    }

    override suspend fun resumeDownload(gameId: String): Result<String> {
        val partial = database.spelaDatabaseQueries.getPartialDownload(gameId).executeAsOneOrNull()
            ?: return Result.failure(IllegalStateException("No resumable partial for game $gameId"))
        val gameTitle = partial.game_title
        val path = partial.local_path
        // Capture the caller's Job so cancelDownload can interrupt the resume.
        setActiveJob(gameId, coroutineContext[Job]!!)
        return runCatching {
            // The partial file's current size IS the resume offset. If the file
            // vanished (manual cleanup), start over from zero.
            val resumeFrom = if (fileStorage.fileExists(path)) fileStorage.getFileSize(path) else 0L
            // Seed the monotonic high-water so the bar continues from the offset
            // rather than snapping back to 0 before the first 206 chunk lands.
            monotonicBytes(gameId, resumeFrom)
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, resumeFrom, partial.expected_size))
            }
            transferSingleFile(gameId, gameTitle, path, partial.expected_size, resumeFrom, partial.validator)
        }.onSuccess { resolvedPath ->
            val fileSize = try { fileStorage.getDirectorySize(fileStorage.getGamesDir() + "/$gameId") } catch (_: Exception) { 0L }
            database.spelaDatabaseQueries.insertDownload(gameId, resolvedPath, fileSize, nowMillis())
            database.spelaDatabaseQueries.deletePartialDownload(gameId)
            refreshDownloadedGames()
            takeActiveJob(gameId)
        }.onFailure { error ->
            handleDownloadFailure(gameId, gameTitle, error)
        }
    }

    /**
     * Resolves the in-memory + persisted state when a download stops without
     * completing. Shared by [downloadGame] and [resumeDownload].
     *
     * - **Cancellation** (user pause, app backgrounded/killed): keep the
     *   partial and surface PAUSED. The partial row was persisted at download
     *   start, so nothing else is needed — and the coroutine is cancelled, so
     *   suspend/DB work here would throw. Re-throws so structured concurrency
     *   still unwinds.
     * - **Resumable failure** (network drop, server cut, unknown error): keep
     *   the partial, stamp the reason, surface PAUSED with a [DownloadFailureReason].
     * - **Terminal failure** (corrupt bytes, disk full): discard the partial so
     *   a restart is clean, surface FAILED. (#1296)
     */
    private suspend fun handleDownloadFailure(gameId: String, gameTitle: String, error: Throwable) {
        takeActiveJob(gameId)
        // Read display bytes/total from the last in-memory progress (non-suspend,
        // safe even when the coroutine is cancelled). The exact resume offset is
        // re-derived by statting the file at resume time.
        val last = downloads.value[gameId]
        val onDisk = last?.bytesDownloaded ?: 0L
        val total = last?.totalBytes ?: -1L
        resetSpeed(gameId)
        // Resume needs a persisted partial record. Paths that never write one
        // (multi-disc, tar bundles) can't be resumed, so they must NOT surface a
        // PAUSED/Resume affordance that would dead-end on resumeDownload. This is
        // a synchronous query — safe to read even on a cancelled coroutine. (#1296)
        val hasPartial = database.spelaDatabaseQueries.getPartialDownload(gameId).executeAsOneOrNull() != null

        if (error is CancellationException) {
            downloads.update {
                if (hasPartial) {
                    it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.PAUSED, onDisk, total))
                } else {
                    it - gameId // nothing to resume — a plain cancel, drop the entry
                }
            }
            throw error
        }

        val reason = classifyFailure(error)
        if (reason.resumable && hasPartial) {
            // Keep the partial; record why so the UI shows the right copy. Guard
            // the DB write with NonCancellable so a racing cancel can't abort the
            // bookkeeping half-done.
            withContext(NonCancellable) {
                database.spelaDatabaseQueries.updatePartialDownloadFailure(reason.name, nowMillis(), gameId)
            }
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.PAUSED, onDisk, total, failureReason = reason))
            }
        } else {
            // Terminal, OR resumable but with no partial to resume from → discard
            // and surface FAILED so the UI offers Start over, not a broken Resume.
            withContext(NonCancellable) {
                cleanupPartialDownload(gameId)
                database.spelaDatabaseQueries.deletePartialDownload(gameId)
            }
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.FAILED, failureReason = reason))
            }
        }
    }

    /**
     * Maps a download error to a [DownloadFailureReason]. Defaults to NETWORK
     * (resumable) for unrecognised errors — better to offer Resume than
     * dead-end the user on an opaque failure. Terminal causes are matched
     * explicitly. (#1296)
     */
    private fun classifyFailure(error: Throwable): DownloadFailureReason {
        val msg = (error.message ?: "").lowercase()
        return when {
            "size mismatch" in msg || "empty file" in msg -> DownloadFailureReason.CORRUPT
            "enospc" in msg || "no space" in msg || "not enough space" in msg -> DownloadFailureReason.DISK_FULL
            // 416: our partial is past the server file's end (e.g. it shrank
            // under a same validator). Resuming again just re-416s — restart.
            "http 416" in msg -> DownloadFailureReason.CORRUPT
            Regex("http 5\\d\\d").containsMatchIn(msg) -> DownloadFailureReason.SERVER
            else -> DownloadFailureReason.NETWORK
        }
    }

    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun persistPartialRow(
        gameId: String,
        gameTitle: String,
        fileName: String,
        path: String,
        expectedSize: Long,
        validator: String?,
    ) {
        database.spelaDatabaseQueries.insertPartialDownload(
            gameId, gameTitle, fileName, path, expectedSize, validator, null, nowMillis(),
        )
    }

    private fun updatePartialHeaders(gameId: String, validator: String?, expectedSize: Long) {
        database.spelaDatabaseQueries.updatePartialDownloadHeaders(validator, expectedSize, nowMillis(), gameId)
    }

    override suspend fun downloadGameToDirectory(
        gameId: String,
        gameTitle: String,
        destDir: String,
    ): Result<String> {
        setActiveJob(gameId, coroutineContext[Job]!!)
        return runCatching {
            downloads.update { it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.QUEUED)) }
            downloads.update { it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING)) }

            val gameDetail = apiClient.getGameDetail(gameId)
            val fileName = gameDetail.fileName.ifEmpty { gameId }
            val expectedSize = gameDetail.fileSize
            // destDir is a user-chosen absolute path; write the file directly
            // into it. Unlike downloadGame this records no DB row, so the game
            // is never treated as "cached" — the file lives outside the games
            // dir purely for the user. (#1257)
            val destPath = "$destDir/$fileName"

            apiClient.downloadGameToFile(gameId, fileStorage, destPath) { downloaded, total ->
                val reportedTotal = total ?: if (expectedSize > 0) expectedSize else -1L
                val monotonic = monotonicBytes(gameId, downloaded)
                val speed = recordSpeed(gameId, monotonic)
                downloads.update {
                    it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, monotonic, reportedTotal, bytesPerSecond = speed))
                }
            }

            resetSpeed(gameId)
            val actualSize = fileStorage.getFileSize(destPath)
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.COMPLETED, actualSize, actualSize))
            }
            destPath
        }.also {
            takeActiveJob(gameId)
        }.onFailure { error ->
            takeActiveJob(gameId)
            resetSpeed(gameId)
            if (error is CancellationException) {
                downloads.update { it - gameId }
                throw error
            }
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.FAILED))
            }
        }
    }

    /**
     * Mirrors the server's packaging decision in
     * `huma_downloads.go::HumaDownloadGame`: extensions that the server
     * always bundles as an uncompressed tar on `/api/games/{id}/download`.
     * Keep this list in sync with the server's `if HasSuffix` checks.
     */
    private fun isTarBundledByServer(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".scummvm") ||
            lower.endsWith(".cue") ||
            lower.endsWith(".gdi")
    }

    private suspend fun downloadSingleDiscGame(
        gameId: String,
        gameTitle: String,
        fileName: String,
        expectedSize: Long,
    ): String {
        // Store in a subdirectory with the original filename so the core can identify the format
        val gameDir = fileStorage.getGamesDir() + "/$gameId"
        // Clean up any old flat file at this path (from previous download format)
        if (fileStorage.fileExists(gameDir) && !fileStorage.isDirectory(gameDir)) {
            fileStorage.deleteFile(gameDir)
        }
        fileStorage.createDirectory(gameDir)
        val actualFileName = fileName.ifEmpty { gameId }
        val path = "$gameDir/$actualFileName"

        // Record a resumable partial BEFORE streaming so an interrupted transfer
        // — including a hard process kill that never reaches a failure handler —
        // leaves a row to resume from (and shields the dir from the orphan
        // sweep). The validator is filled in once response headers arrive. (#1296)
        persistPartialRow(gameId, gameTitle, actualFileName, path, expectedSize, validator = null)

        return transferSingleFile(gameId, gameTitle, path, expectedSize, resumeFrom = 0, validator = null)
    }

    /**
     * Streams a single-file game to [path] — fresh ([resumeFrom] = 0) or
     * resumed (the on-disk offset, with a [validator] for If-Range) — updating
     * progress and the partial record, then validates the result against the
     * size the server reported for THIS transfer. Shared by the fresh and
     * resume paths. (#1296)
     */
    private suspend fun transferSingleFile(
        gameId: String,
        gameTitle: String,
        path: String,
        expectedSize: Long,
        resumeFrom: Long,
        validator: String?,
    ): String {
        // Authoritative full size of the file the server is serving, learned
        // from response headers; the DB size is only a fallback until then.
        var serverTotal: Long = if (expectedSize > 0) expectedSize else -1L

        val result = apiClient.downloadGameToFile(
            gameId,
            fileStorage,
            path,
            resumeFrom = resumeFrom,
            validator = validator,
            onResponseInfo = { serverValidator, fullSize, resumed ->
                if (fullSize != null && fullSize > 0) serverTotal = fullSize
                // If we asked to resume but the server restarted from scratch
                // (200 — the file changed), the byte counter is going back to 0;
                // clear the high-water seeded at the old offset so the bar tracks
                // the fresh transfer instead of sticking at the stale offset. (#1296)
                if (resumeFrom > 0 && !resumed) resetSpeed(gameId)
                // Persist validator + true size immediately so a later resume is
                // safe and shows correct progress even if this attempt dies
                // mid-stream — the very state resume needs. (#1296)
                updatePartialHeaders(gameId, serverValidator, fullSize ?: expectedSize)
            },
        ) { downloaded, total ->
            // Prefer the server's Content-Length (`total`) — it's the exact
            // number of bytes we'll receive, so `downloaded` reaches it and the
            // bar hits 100%. The DB file size can disagree with the transfer:
            // for compressed single-file formats (.rvz, .chd) it's the
            // logical/uncompressed size, which left the bar stuck well under
            // 100%. Game downloads aren't Content-Encoding: gzip'd, so
            // Content-Length is the true received-byte count on every engine.
            // Fall back to the DB size only if the server omits it. (#1235)
            val reportedTotal = total ?: if (expectedSize > 0) expectedSize else -1L
            val monotonic = monotonicBytes(gameId, downloaded)
            val speed = recordSpeed(gameId, monotonic)
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, monotonic, reportedTotal, bytesPerSecond = speed))
            }
        }

        val actualSize = fileStorage.getFileSize(path)
        println("[Download] File written: path=$path actualSize=$actualSize serverTotal=$serverTotal resumed=${result.resumed}")
        if (actualSize == 0L) {
            throw RuntimeException("Download produced empty file: $path")
        }
        // Validate against the size the server reported for THIS transfer, not
        // the possibly-stale DB size — otherwise a legitimately changed file
        // (served fresh via a 200 on resume) looks like a size mismatch. (#1296)
        if (serverTotal > 0 && actualSize != serverTotal) {
            throw RuntimeException(
                "Download size mismatch: expected $serverTotal bytes, got $actualSize bytes"
            )
        }
        resetSpeed(gameId)
        downloads.update {
            it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.COMPLETED, actualSize, actualSize))
        }

        return path
    }

    /**
     * Downloads a single-disc game that has a GameDisc record on the server.
     * For .cue files, uses the disc endpoint which returns a tar bundle.
     * For other formats, uses the disc endpoint which returns the file directly.
     */
    private suspend fun downloadSingleDiscWithDiscRecord(
        gameId: String,
        gameTitle: String,
        game: GameDto,
    ): String {
        val disc = game.discs.first()
        val gameDir = fileStorage.getGamesDir() + "/$gameId"
        if (fileStorage.fileExists(gameDir) && !fileStorage.isDirectory(gameDir)) {
            fileStorage.deleteFile(gameDir)
        }
        fileStorage.createDirectory(gameDir)

        if (disc.fileName.endsWith(".cue", ignoreCase = true) ||
            disc.fileName.endsWith(".gdi", ignoreCase = true)) {
            // Tar archive (cue+bin or gdi+tracks) — stream-extract directly to disk.
            // Prefer the server-supplied X-Logical-Size total over the DB
            // disc.fileSize: same units (logical bytes) but the server
            // measures the actual files in the archive. See #931.
            apiClient.downloadDiscAndExtract(gameId, disc.discNumber.toInt(), fileStorage, gameDir) { downloaded, total ->
                val reportedTotal = total ?: if (disc.fileSize > 0) disc.fileSize else -1L
                val monotonic = monotonicBytes(gameId, downloaded)
                val speed = recordSpeed(gameId, monotonic)
                downloads.update {
                    it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, monotonic, reportedTotal, bytesPerSecond = speed))
                }
            }
        } else {
            // Single file — stream to disk
            val discPath = "$gameDir/${disc.fileName}"
            apiClient.downloadDiscToFile(gameId, disc.discNumber.toInt(), fileStorage, discPath) { downloaded, total ->
                // Prefer the server's Content-Length over the DB disc.fileSize:
                // it's the exact transfer size, so the bar reaches 100%. For
                // compressed single-file discs (.rvz, .chd) the DB size is the
                // logical/uncompressed size and left the bar stuck ~50%. This
                // matches the .cue/.gdi branch above. (#1235)
                val reportedTotal = total ?: if (disc.fileSize > 0) disc.fileSize else -1L
                val monotonic = monotonicBytes(gameId, downloaded)
                val speed = recordSpeed(gameId, monotonic)
                downloads.update {
                    it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, monotonic, reportedTotal, bytesPerSecond = speed))
                }
            }
        }

        resetSpeed(gameId)
        downloads.update {
            it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.COMPLETED, game.fileSize, game.fileSize))
        }
        return gameDir
    }

    /**
     * Downloads a game whose payload from `/api/games/{id}/download` is a tar
     * archive rather than the raw ROM bytes. Two cases hit this path:
     *   - `.cue` / `.gdi` games that have no disc records (old DB entry).
     *   - `.scummvm` games — the server tars the whole game directory so the
     *     player ends up with all the engine data files, not just the empty
     *     `*.scummvm` marker.
     *
     * Streams the tar straight to disk via `downloadGameAndExtract` (extracts
     * file-by-file as the response arrives), then reports COMPLETED.
     *
     * Total source: prefer the server-supplied total (X-Logical-Size header,
     * sum of post-extract file sizes — see #931 / streamArchiveTar). For
     * ScummVM games game.fileSize is the size of the marker file alone
     * (often a few hundred bytes), so falling back to it would peg the bar
     * at 100% on the first emission. Falls back to game.fileSize only if
     * the server didn't supply X-Logical-Size — keeps an old-server client
     * functional.
     */
    private suspend fun downloadTarBundledGameFromGameEndpoint(
        gameId: String,
        gameTitle: String,
        expectedSize: Long,
    ): String {
        val gameDir = fileStorage.getGamesDir() + "/$gameId"
        if (fileStorage.fileExists(gameDir) && !fileStorage.isDirectory(gameDir)) {
            fileStorage.deleteFile(gameDir)
        }
        fileStorage.createDirectory(gameDir)

        var lastTotal = -1L
        apiClient.downloadGameAndExtract(gameId, fileStorage, gameDir) { downloaded, total ->
            val reportedTotal = total ?: if (expectedSize > 0) expectedSize else -1L
            lastTotal = reportedTotal
            val monotonic = monotonicBytes(gameId, downloaded)
            val speed = recordSpeed(gameId, monotonic)
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, monotonic, reportedTotal, bytesPerSecond = speed))
            }
        }

        // Use whichever total the bar saw last — that's what the user
        // watched the progress fill toward, so completing to the same
        // number is monotonic.
        val finalTotal = if (lastTotal > 0) lastTotal else expectedSize
        resetSpeed(gameId)
        downloads.update {
            it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.COMPLETED, finalTotal, finalTotal))
        }
        return gameDir
    }

    private suspend fun downloadMultiDiscGame(gameId: String, gameTitle: String, game: GameDto): String {
        val gameDir = fileStorage.getGamesDir() + "/$gameId"
        fileStorage.createDirectory(gameDir)

        val m3uPath = "$gameDir/game.m3u"
        val totalDiscs = game.discs.size
        val totalSize = game.fileSize
        var completedBytes = 0L

        // Download each disc FIRST — write M3U only after all discs succeed.
        // Writing M3U early would make getLocalGamePath treat the game as cached
        // even if disc downloads fail or are still in progress.
        for ((index, disc) in game.discs.sortedBy { it.discNumber }.withIndex()) {
            val discOffset = completedBytes

            downloads.update {
                it + (gameId to DownloadProgress(
                    gameId, gameTitle, DownloadState.DOWNLOADING,
                    bytesDownloaded = discOffset, totalBytes = totalSize,
                    currentDisc = disc.discNumber.toInt(), totalDiscs = totalDiscs,
                ))
            }

            if (disc.fileName.endsWith(".cue", ignoreCase = true) ||
                disc.fileName.endsWith(".gdi", ignoreCase = true)) {
                // Tar archive (cue+bin or gdi+tracks) - stream-extract directly to disk.
                // Both discOffset (sum of disc.fileSize) and downloaded (from
                // the tar parser, post-#931) are in logical bytes — same unit
                // as totalSize, so the running sum no longer jumps backward
                // at disc boundaries.
                apiClient.downloadDiscAndExtract(gameId, disc.discNumber.toInt(), fileStorage, gameDir) { downloaded, total ->
                    val totalDownloaded = monotonicBytes(gameId, discOffset + downloaded)
                    val speed = recordSpeed(gameId, totalDownloaded)
                    downloads.update {
                        it + (gameId to DownloadProgress(
                            gameId, gameTitle, DownloadState.DOWNLOADING,
                            bytesDownloaded = totalDownloaded, totalBytes = totalSize,
                            currentDisc = disc.discNumber.toInt(), totalDiscs = totalDiscs,
                            bytesPerSecond = speed,
                        ))
                    }
                }
            } else {
                // Single file (ISO/CHD/CSO) - stream to disk
                val discPath = "$gameDir/${disc.fileName}"
                apiClient.downloadDiscToFile(gameId, disc.discNumber.toInt(), fileStorage, discPath) { downloaded, total ->
                    val totalDownloaded = monotonicBytes(gameId, discOffset + downloaded)
                    val speed = recordSpeed(gameId, totalDownloaded)
                    downloads.update {
                        it + (gameId to DownloadProgress(
                            gameId, gameTitle, DownloadState.DOWNLOADING,
                            bytesDownloaded = totalDownloaded, totalBytes = totalSize,
                            currentDisc = disc.discNumber.toInt(), totalDiscs = totalDiscs,
                            bytesPerSecond = speed,
                        ))
                    }
                }
            }

            completedBytes += disc.fileSize
        }

        // Write .m3u with local filenames — only after all discs downloaded
        val m3uContent = game.discs.sortedBy { it.discNumber }
            .joinToString("\n") { it.fileName } + "\n"
        fileStorage.writeFile(m3uPath, m3uContent.encodeToByteArray())

        resetSpeed(gameId)
        downloads.update {
            it + (gameId to DownloadProgress(
                gameId, gameTitle, DownloadState.COMPLETED,
                bytesDownloaded = game.fileSize, totalBytes = game.fileSize,
                currentDisc = totalDiscs, totalDiscs = totalDiscs,
            ))
        }

        return m3uPath
    }

    private suspend fun cleanupPartialDownload(gameId: String) {
        val gameDir = fileStorage.getGamesDir() + "/$gameId"
        try {
            if (fileStorage.isDirectory(gameDir)) {
                fileStorage.deleteDirectory(gameDir)
            } else if (fileStorage.fileExists(gameDir)) {
                fileStorage.deleteFile(gameDir)
            }
        } catch (e: Exception) {
            // Surface the failure so silent disk-space leaks are visible in
            // logcat. Cleanup is best-effort because this runs from
            // failure / cancel paths where we can't propagate further;
            // the next download for the same gameId will overwrite via
            // createDirectory, so the leak is bounded to that game's id.
            println("[Download] cleanupPartialDownload($gameId): failed to delete $gameDir: ${e.message}")
        }
    }

    override suspend fun cancelDownload(gameId: String) {
        // Stop the in-flight transfer but KEEP the partial — an interrupted
        // download is resumable now (#1296). Cancelling the job propagates
        // CancellationException into the runCatching block of
        // downloadGame/resumeDownload, whose failure handler records PAUSED and
        // leaves the partial file + its row in place. If there's no active job
        // (already paused/done), this is a no-op. Use deleteLocalGame to remove
        // a partial entirely.
        takeActiveJob(gameId)?.cancel(CancellationException("download paused by user"))
    }

    override suspend fun getLocalGamePath(gameId: String): String? {
        // An in-progress / paused / failed partial is on disk under the final
        // name but isn't a complete, playable file — never resolve it as cached
        // until the download finishes and its partial row is cleared. (#1296)
        if (database.spelaDatabaseQueries.getPartialDownload(gameId).executeAsOneOrNull() != null) {
            return null
        }
        val gameDir = fileStorage.getGamesDir() + "/$gameId"
        // Check multi-disc first: M3U must exist AND disc images must be present.
        // Without this check, a partial download (M3U written but discs not yet
        // downloaded) would be treated as a valid cached game.
        val multiPath = "$gameDir/game.m3u"
        if (fileStorage.fileExists(multiPath)) {
            val files = fileStorage.listFiles(gameDir)
            val hasDiscImages = files.any { it != "game.m3u" }
            if (hasDiscImages) return multiPath
            // M3U exists but no disc images — partial/failed download
            println("[Download] getLocalGamePath: $multiPath exists but no disc images, treating as not cached")
        }
        // Check single-disc in subdirectory (new format: /$gameId/$fileName)
        if (fileStorage.isDirectory(gameDir)) {
            val files = fileStorage.listFiles(gameDir)
            println("[Download] getLocalGamePath: gameDir=$gameDir isDir=true files=$files")
            // Pick the entry file the libretro core expects. Multi-file games
            // tar-bundle their companions next to a small "entry" file:
            //   - ScummVM: `*.scummvm` marker — core reads engine/data ID from it
            //   - PS1 / Saturn: `.cue` / `.gdi` — core reads track list from it
            // Score files so the entry rank wins regardless of tar order:
            //   3 = .scummvm, 2 = .cue/.gdi, 1 = anything else.
            val gameFile = files.filter { it != "game.m3u" }
                .maxByOrNull { name ->
                    when {
                        name.endsWith(".scummvm", ignoreCase = true) -> 3
                        name.endsWith(".cue", ignoreCase = true) ||
                            name.endsWith(".gdi", ignoreCase = true) -> 2
                        else -> 1
                    }
                }
            if (gameFile != null) return "$gameDir/$gameFile"
        } else {
            val exists = fileStorage.fileExists(gameDir)
            println("[Download] getLocalGamePath: gameDir=$gameDir isDir=false exists=$exists")
        }
        return null
    }

    override suspend fun isGameCached(gameId: String): Boolean {
        return getLocalGamePath(gameId) != null
    }

    override suspend fun deleteLocalGame(gameId: String) {
        // Stop any in-flight transfer first so it can't keep writing to a dir
        // we're about to delete (e.g. "Start over" while a job is somehow live).
        takeActiveJob(gameId)?.cancel(CancellationException("download removed"))
        val gameDir = fileStorage.getGamesDir() + "/$gameId"
        if (fileStorage.isDirectory(gameDir)) {
            fileStorage.deleteDirectory(gameDir)
        } else if (fileStorage.fileExists(gameDir)) {
            fileStorage.deleteFile(gameDir)
        }
        downloads.update { it - gameId }
        database.spelaDatabaseQueries.deleteDownload(gameId)
        // Also clear any resumable partial — this is the removal path for both
        // a completed game and a paused/failed partial ("Start over"/"Cancel"). (#1296)
        database.spelaDatabaseQueries.deletePartialDownload(gameId)
        resetSpeed(gameId)
        refreshDownloadedGames()
    }

    override suspend fun getCacheSize(): Long {
        return fileStorage.getDirectorySize(fileStorage.getGamesDir())
    }

    override suspend fun clearCache() {
        fileStorage.deleteDirectory(fileStorage.getGamesDir())
        downloads.value = emptyMap()
        database.spelaDatabaseQueries.deleteAllDownloads()
        refreshDownloadedGames()
    }
}
