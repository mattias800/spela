package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
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
            map[gameId] ?: DownloadProgress(gameId, DownloadState.IDLE)
        }

    override suspend fun downloadGame(gameId: String): Result<String> = runCatching {
        downloads.update { it + (gameId to DownloadProgress(gameId, DownloadState.QUEUED)) }

        downloads.update {
            it + (gameId to DownloadProgress(gameId, DownloadState.DOWNLOADING))
        }

        val data = apiClient.downloadGame(gameId) { downloaded, total ->
            downloads.update {
                it + (gameId to DownloadProgress(gameId, DownloadState.DOWNLOADING, downloaded, total))
            }
        }

        val path = fileStorage.getGamesDir() + "/$gameId"
        fileStorage.writeFile(path, data)

        downloads.update {
            it + (gameId to DownloadProgress(gameId, DownloadState.COMPLETED, data.size.toLong(), data.size.toLong()))
        }

        path
    }.onFailure {
        downloads.update {
            it + (gameId to DownloadProgress(gameId, DownloadState.FAILED))
        }
    }

    override suspend fun cancelDownload(gameId: String) {
        downloads.update { it - gameId }
    }

    override suspend fun getLocalGamePath(gameId: String): String? {
        val path = fileStorage.getGamesDir() + "/$gameId"
        return if (fileStorage.fileExists(path)) path else null
    }

    override suspend fun isGameCached(gameId: String): Boolean {
        return getLocalGamePath(gameId) != null
    }

    override suspend fun deleteLocalGame(gameId: String) {
        val path = fileStorage.getGamesDir() + "/$gameId"
        fileStorage.deleteFile(path)
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
