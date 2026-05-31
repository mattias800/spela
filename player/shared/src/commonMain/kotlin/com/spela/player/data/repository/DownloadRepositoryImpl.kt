package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.GameDto
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.util.FileStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
     * Walks `getGamesDir()` and deletes any per-game directory that has
     * no row in the `downloads` table. These are orphans from process
     * death, force-stop, or crashes that aborted [downloadGame] before
     * the [DownloadEntity] insert ran — without this scan they'd sit on
     * disk forever (no UI surface lists them, and the user has no
     * "phantom partial" to discover).
     *
     * Idempotent: running this twice in a row produces the same result.
     * Should be called exactly once at app launch from the DI graph.
     * See #845.
     */
    override suspend fun scanForOrphanedDownloads() {
        val gamesDir = fileStorage.getGamesDir()
        if (!fileStorage.fileExists(gamesDir)) return
        val tracked = database.spelaDatabaseQueries.getAllDownloads()
            .executeAsList().map { it.game_id }.toSet()
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
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            database.spelaDatabaseQueries.insertDownload(gameId, path, fileSize, now)
            refreshDownloadedGames()
            takeActiveJob(gameId)
        }.onFailure { error ->
            takeActiveJob(gameId)
            cleanupPartialDownload(gameId)
            resetSpeed(gameId)
            // User-initiated cancel surfaces as CancellationException via runCatching.
            // Treat it as IDLE rather than FAILED so the UI doesn't show an error
            // toast for an action the user just took intentionally.
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

        apiClient.downloadGameToFile(gameId, fileStorage, path) { downloaded, total ->
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
        println("[Download] File written: path=$path actualSize=$actualSize expectedSize=$expectedSize")
        if (actualSize == 0L) {
            throw RuntimeException("Download produced empty file: $path")
        }
        if (expectedSize > 0 && actualSize != expectedSize) {
            throw RuntimeException(
                "Download size mismatch: expected $expectedSize bytes, got $actualSize bytes"
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
        // Cancel the in-flight job (if any). This propagates CancellationException
        // through the runCatching block in downloadGame, which then runs
        // cleanupPartialDownload via the onFailure branch — so disk space is
        // reclaimed automatically. cancelDownload itself doesn't need to call
        // cleanup directly.
        takeActiveJob(gameId)?.cancel(CancellationException("download cancelled by user"))
        // Defensive: if the job already completed (or never started), drop the
        // in-memory progress entry and tracker manually.
        downloads.update { it - gameId }
        // Drop the tracker too so a cancel-then-restart starts with a fresh
        // window. Without this the old samples briefly inflate the reported
        // speed of the new download until they age out.
        resetSpeed(gameId)
    }

    override suspend fun getLocalGamePath(gameId: String): String? {
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
        val gameDir = fileStorage.getGamesDir() + "/$gameId"
        if (fileStorage.isDirectory(gameDir)) {
            fileStorage.deleteDirectory(gameDir)
        } else if (fileStorage.fileExists(gameDir)) {
            fileStorage.deleteFile(gameDir)
        }
        downloads.update { it - gameId }
        database.spelaDatabaseQueries.deleteDownload(gameId)
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
