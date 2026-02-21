package com.spela.player.domain.repository

import com.spela.player.domain.model.SaveState

interface SaveRepository {
    suspend fun getSaveStates(gameId: String): Result<List<SaveState>>
    suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray): Result<SaveState>
    suspend fun downloadSaveState(gameId: String, saveId: String): Result<ByteArray>
    suspend fun deleteSaveState(gameId: String, saveId: String): Result<Unit>
    suspend fun uploadAutoSave(gameId: String, data: ByteArray): Result<SaveState>
    suspend fun downloadAutoSave(gameId: String): Result<ByteArray>
    suspend fun saveLocally(gameId: String, name: String, data: ByteArray, isAuto: Boolean): Result<SaveState>
    suspend fun loadLocalAutoSave(gameId: String): Result<ByteArray>
    suspend fun getPendingSyncCount(): Int
}
