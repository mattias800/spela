package com.spela.player.domain.repository

import com.spela.player.domain.model.SharedSaveState

interface SharedSaveRepository {
    suspend fun getSharedSaves(gameId: String, page: Int = 1, pageSize: Int = 20): Result<List<SharedSaveState>>
    suspend fun shareSave(gameId: String, name: String, description: String, saveData: ByteArray): Result<SharedSaveState>
    suspend fun downloadSharedSave(gameId: String, saveId: String): Result<ByteArray>
    suspend fun deleteSharedSave(gameId: String, saveId: String): Result<Unit>
}
