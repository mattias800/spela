package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.repository.SaveRepository

class SaveRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : SaveRepository {

    override suspend fun getSaveStates(gameId: String): Result<List<SaveState>> = runCatching {
        apiClient.getSaveStates(gameId).map { it.toDomain() }
    }

    override suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray): Result<SaveState> =
        runCatching {
            apiClient.uploadSaveState(gameId, name, data).toDomain()
        }

    override suspend fun downloadSaveState(gameId: String, saveId: String): Result<ByteArray> = runCatching {
        apiClient.downloadSaveState(gameId, saveId)
    }

    override suspend fun deleteSaveState(gameId: String, saveId: String): Result<Unit> = runCatching {
        apiClient.deleteSaveState(gameId, saveId)
    }

    override suspend fun uploadAutoSave(gameId: String, data: ByteArray): Result<SaveState> = runCatching {
        apiClient.uploadAutoSave(gameId, data).toDomain()
    }

    override suspend fun downloadAutoSave(gameId: String): Result<ByteArray> = runCatching {
        apiClient.downloadAutoSave(gameId)
    }
}
