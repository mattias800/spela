package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.LibretroCore
import com.spela.player.domain.repository.CoreRepository
import com.spela.player.util.FileStorage

class CoreRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val fileStorage: FileStorage,
) : CoreRepository {

    override suspend fun getAvailableCores(): Result<List<LibretroCore>> = runCatching {
        apiClient.getAvailableCores().map { it.toDomain() }
    }

    override suspend fun getRecommendedCore(gameId: String): Result<LibretroCore> = runCatching {
        apiClient.getRecommendedCore(gameId).toDomain()
    }

    override suspend fun downloadCore(coreId: String, onProgress: (Float) -> Unit): Result<String> = runCatching {
        val data = apiClient.downloadCore(coreId) { downloaded, total ->
            if (total > 0) onProgress(downloaded.toFloat() / total)
        }
        val path = fileStorage.getCoresDir() + "/$coreId"
        fileStorage.writeFile(path, data)
        path
    }

    override suspend fun getLocalCorePath(coreId: String): String? {
        val path = fileStorage.getCoresDir() + "/$coreId"
        return if (fileStorage.fileExists(path)) path else null
    }

    override suspend fun isCoreCached(coreId: String): Boolean {
        return getLocalCorePath(coreId) != null
    }
}
