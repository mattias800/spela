package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.domain.repository.SharedSaveRepository

class SharedSaveRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : SharedSaveRepository {

    override suspend fun getSharedSaves(gameId: String, page: Int, pageSize: Int): Result<List<SharedSaveState>> = runCatching {
        apiClient.getSharedSaves(gameId, page = page, pageSize = pageSize).data.orEmpty().map { dto ->
            val save = dto.toDomain()
            save.copy(userAvatarUrl = apiClient.resolveUrl(save.userAvatarUrl))
        }
    }

    override suspend fun shareSave(gameId: String, name: String, description: String, saveData: ByteArray): Result<SharedSaveState> = runCatching {
        val dto = apiClient.shareSave(gameId, name, description, saveData)
        dto.toDomain().copy(userAvatarUrl = apiClient.resolveUrl(dto.avatarUrl))
    }

    override suspend fun downloadSharedSave(gameId: String, saveId: String): Result<ByteArray> = runCatching {
        apiClient.downloadSharedSave(gameId, saveId)
    }

    override suspend fun deleteSharedSave(gameId: String, saveId: String): Result<Unit> = runCatching {
        apiClient.deleteSharedSave(gameId, saveId)
    }
}
