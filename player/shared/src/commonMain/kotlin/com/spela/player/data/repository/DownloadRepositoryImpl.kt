package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.GameDto
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.util.FileStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

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

    private fun recordSpeed(gameId: String, bytesDownloaded: Long): Long =
        speedTrackers.getOrPut(gameId) { SpeedTracker() }.record(bytesDownloaded)

    private fun resetSpeed(gameId: String) {
        speedTrackers.remove(gameId)
    }

    init {
        refreshDownloadedGames()
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

    override suspend fun downloadGame(gameId: String, gameTitle: String): Result<String> = runCatching {
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
    }.onFailure {
        cleanupPartialDownload(gameId)
        resetSpeed(gameId)
        downloads.update {
            it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.FAILED))
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
            // Prefer the DB file size over Content-Length. OkHttp transparently
            // decompresses gzip responses, so Content-Length may report the
            // compressed size while downloaded bytes count decompressed data,
            // causing the progress bar to jump past 100%.
            val reportedTotal = if (expectedSize > 0) expectedSize else (total ?: -1)
            val speed = recordSpeed(gameId, downloaded)
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, downloaded, reportedTotal, bytesPerSecond = speed))
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
            // Tar archive (cue+bin or gdi+tracks) — stream-extract directly to disk
            apiClient.downloadDiscAndExtract(gameId, disc.discNumber.toInt(), fileStorage, gameDir) { downloaded, total ->
                val reportedTotal = if (disc.fileSize > 0) disc.fileSize else (total ?: -1)
                val speed = recordSpeed(gameId, downloaded)
                downloads.update {
                    it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, downloaded, reportedTotal, bytesPerSecond = speed))
                }
            }
        } else {
            // Single file — stream to disk
            val discPath = "$gameDir/${disc.fileName}"
            apiClient.downloadDiscToFile(gameId, disc.discNumber.toInt(), fileStorage, discPath) { downloaded, total ->
                val reportedTotal = if (disc.fileSize > 0) disc.fileSize else (total ?: -1)
                val speed = recordSpeed(gameId, downloaded)
                downloads.update {
                    it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, downloaded, reportedTotal, bytesPerSecond = speed))
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
     * file-by-file as the response arrives), then reports COMPLETED. There is
     * no `actualSize == expectedSize` check because the wire bytes are the
     * uncompressed tar (with 512-byte headers and per-file padding), which
     * never matches `game.fileSize` (the size of the entry file alone).
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

        apiClient.downloadGameAndExtract(gameId, fileStorage, gameDir) { downloaded, total ->
            val reportedTotal = if (expectedSize > 0) expectedSize else (total ?: -1)
            val speed = recordSpeed(gameId, downloaded)
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, downloaded, reportedTotal, bytesPerSecond = speed))
            }
        }

        resetSpeed(gameId)
        downloads.update {
            it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.COMPLETED, expectedSize, expectedSize))
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
                // Tar archive (cue+bin or gdi+tracks) - stream-extract directly to disk
                apiClient.downloadDiscAndExtract(gameId, disc.discNumber.toInt(), fileStorage, gameDir) { downloaded, total ->
                    val totalDownloaded = discOffset + downloaded
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
                    val totalDownloaded = discOffset + downloaded
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
        try {
            val gameDir = fileStorage.getGamesDir() + "/$gameId"
            if (fileStorage.isDirectory(gameDir)) {
                fileStorage.deleteDirectory(gameDir)
            } else if (fileStorage.fileExists(gameDir)) {
                fileStorage.deleteFile(gameDir)
            }
        } catch (_: Exception) {
            // Best effort cleanup
        }
    }

    override suspend fun cancelDownload(gameId: String) {
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
