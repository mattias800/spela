package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.GameDto
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.util.FileStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class DownloadRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val fileStorage: FileStorage,
) : DownloadRepository {

    private val downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())

    override fun observeDownloads(): Flow<List<DownloadProgress>> =
        downloads.map { it.values.toList() }

    override fun observeDownload(gameId: String): Flow<DownloadProgress> =
        downloads.map { map ->
            map[gameId] ?: DownloadProgress(gameId = gameId, state = DownloadState.IDLE)
        }

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
        } else if (gameDetail.fileName.endsWith(".cue", ignoreCase = true)) {
            // .cue without disc records (old DB entry) — game endpoint returns tar
            downloadCueGameFromGameEndpoint(gameId, gameTitle, gameDetail.fileName, gameDetail.fileSize)
        } else {
            downloadSingleDiscGame(gameId, gameTitle, gameDetail.fileName, gameDetail.fileSize)
        }
    }.onFailure {
        cleanupPartialDownload(gameId)
        downloads.update {
            it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.FAILED))
        }
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
            // Use server-reported fileSize as fallback when Content-Length is missing
            val reportedTotal = total ?: if (expectedSize > 0) expectedSize else null
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, downloaded, reportedTotal ?: -1))
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

        if (disc.fileName.endsWith(".cue", ignoreCase = true)) {
            // Tar archive (cue+bin) — stream-extract directly to disk
            apiClient.downloadDiscAndExtract(gameId, disc.discNumber, fileStorage, gameDir) { downloaded, total ->
                downloads.update {
                    it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, downloaded, total ?: disc.fileSize))
                }
            }
        } else {
            // Single file — stream to disk
            val discPath = "$gameDir/${disc.fileName}"
            apiClient.downloadDiscToFile(gameId, disc.discNumber, fileStorage, discPath) { downloaded, total ->
                downloads.update {
                    it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, downloaded, total ?: disc.fileSize))
                }
            }
        }

        downloads.update {
            it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.COMPLETED, game.fileSize, game.fileSize))
        }
        return gameDir
    }

    /**
     * Downloads a .cue game that has no disc records (old DB entry).
     * The game download endpoint now returns a tar bundle for .cue files.
     */
    private suspend fun downloadCueGameFromGameEndpoint(
        gameId: String,
        gameTitle: String,
        fileName: String,
        expectedSize: Long,
    ): String {
        val gameDir = fileStorage.getGamesDir() + "/$gameId"
        if (fileStorage.fileExists(gameDir) && !fileStorage.isDirectory(gameDir)) {
            fileStorage.deleteFile(gameDir)
        }
        fileStorage.createDirectory(gameDir)

        apiClient.downloadGameAndExtract(gameId, fileStorage, gameDir) { downloaded, total ->
            val reportedTotal = total ?: if (expectedSize > 0) expectedSize else null
            downloads.update {
                it + (gameId to DownloadProgress(gameId, gameTitle, DownloadState.DOWNLOADING, downloaded, reportedTotal ?: -1))
            }
        }

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
                    currentDisc = disc.discNumber, totalDiscs = totalDiscs,
                ))
            }

            if (disc.fileName.endsWith(".cue", ignoreCase = true)) {
                // Tar archive (cue+bin) - stream-extract directly to disk
                apiClient.downloadDiscAndExtract(gameId, disc.discNumber, fileStorage, gameDir) { downloaded, total ->
                    downloads.update {
                        it + (gameId to DownloadProgress(
                            gameId, gameTitle, DownloadState.DOWNLOADING,
                            bytesDownloaded = discOffset + downloaded, totalBytes = totalSize,
                            currentDisc = disc.discNumber, totalDiscs = totalDiscs,
                        ))
                    }
                }
            } else {
                // Single file (ISO/CHD/CSO) - stream to disk
                val discPath = "$gameDir/${disc.fileName}"
                apiClient.downloadDiscToFile(gameId, disc.discNumber, fileStorage, discPath) { downloaded, total ->
                    downloads.update {
                        it + (gameId to DownloadProgress(
                            gameId, gameTitle, DownloadState.DOWNLOADING,
                            bytesDownloaded = discOffset + downloaded, totalBytes = totalSize,
                            currentDisc = disc.discNumber, totalDiscs = totalDiscs,
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
            // Prefer .cue files — the emulator expects the .cue path to find companions
            val gameFile = files.filter { it != "game.m3u" }
                .sortedByDescending { it.endsWith(".cue", ignoreCase = true) }
                .firstOrNull()
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
    }

    override suspend fun getCacheSize(): Long {
        return fileStorage.getDirectorySize(fileStorage.getGamesDir())
    }

    override suspend fun clearCache() {
        fileStorage.deleteDirectory(fileStorage.getGamesDir())
        downloads.value = emptyMap()
    }
}
