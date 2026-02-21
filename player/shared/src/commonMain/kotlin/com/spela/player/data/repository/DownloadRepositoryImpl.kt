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

        if (gameDetail.discCount >= 2) {
            downloadMultiDiscGame(gameId, gameTitle, gameDetail)
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

    private suspend fun downloadMultiDiscGame(gameId: String, gameTitle: String, game: GameDto): String {
        val gameDir = fileStorage.getGamesDir() + "/$gameId"
        fileStorage.createDirectory(gameDir)

        // Download .m3u file
        val m3uData = apiClient.downloadM3U(gameId)
        val m3uPath = "$gameDir/game.m3u"
        fileStorage.writeFile(m3uPath, m3uData)

        val totalDiscs = game.discs.size

        // Download each disc
        for ((index, disc) in game.discs.sortedBy { it.discNumber }.withIndex()) {
            downloads.update {
                it + (gameId to DownloadProgress(
                    gameId, gameTitle, DownloadState.DOWNLOADING,
                    bytesDownloaded = 0, totalBytes = disc.fileSize,
                    currentDisc = disc.discNumber, totalDiscs = totalDiscs,
                ))
            }

            if (disc.fileName.endsWith(".cue", ignoreCase = true)) {
                // Tar archive (cue+bin) - use ByteArray (typically <800MB)
                val discData = apiClient.downloadDisc(gameId, disc.discNumber) { downloaded, total ->
                    downloads.update {
                        it + (gameId to DownloadProgress(
                            gameId, gameTitle, DownloadState.DOWNLOADING,
                            bytesDownloaded = downloaded, totalBytes = total ?: disc.fileSize,
                            currentDisc = disc.discNumber, totalDiscs = totalDiscs,
                        ))
                    }
                }
                extractTar(discData, gameDir)
            } else {
                // Single file (ISO/CHD/CSO) - stream to disk
                val discPath = "$gameDir/${disc.fileName}"
                apiClient.downloadDiscToFile(gameId, disc.discNumber, fileStorage, discPath) { downloaded, total ->
                    downloads.update {
                        it + (gameId to DownloadProgress(
                            gameId, gameTitle, DownloadState.DOWNLOADING,
                            bytesDownloaded = downloaded, totalBytes = total ?: disc.fileSize,
                            currentDisc = disc.discNumber, totalDiscs = totalDiscs,
                        ))
                    }
                }
            }
        }

        // Rewrite .m3u to use local filenames
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

    private suspend fun extractTar(tarData: ByteArray, outputDir: String) {
        var offset = 0
        while (offset + 512 <= tarData.size) {
            // Read header (512 bytes)
            val header = tarData.copyOfRange(offset, offset + 512)

            // Check for end-of-archive (two zero blocks)
            if (header.all { it == 0.toByte() }) break

            // Extract filename (bytes 0-99, null-terminated)
            val nameEnd = header.indexOf(0.toByte()).let { if (it < 0 || it > 100) 100 else it }
            val name = header.copyOfRange(0, nameEnd).decodeToString().trim()
            if (name.isEmpty()) break

            // Extract file size (bytes 124-135, octal ASCII)
            val sizeStr = header.copyOfRange(124, 136).decodeToString().trim().trimEnd(0.toChar())
            val size = sizeStr.toLongOrNull(8) ?: 0L

            offset += 512 // Move past header

            if (size > 0) {
                val fileData = tarData.copyOfRange(offset, (offset + size.toInt()).coerceAtMost(tarData.size))
                fileStorage.writeFile("$outputDir/$name", fileData)
                // Tar pads to 512-byte blocks
                offset += ((size + 511) / 512 * 512).toInt()
            }
        }
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
        // Check multi-disc first
        val multiPath = "$gameDir/game.m3u"
        if (fileStorage.fileExists(multiPath)) return multiPath
        // Check single-disc in subdirectory (new format: /$gameId/$fileName)
        if (fileStorage.isDirectory(gameDir)) {
            val files = fileStorage.listFiles(gameDir)
            println("[Download] getLocalGamePath: gameDir=$gameDir isDir=true files=$files")
            val gameFile = files.firstOrNull { it != "game.m3u" }
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
