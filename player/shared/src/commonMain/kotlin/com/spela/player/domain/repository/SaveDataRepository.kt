package com.spela.player.domain.repository

import com.spela.player.domain.model.SaveData

interface SaveDataRepository {
    suspend fun getSaveDataList(gameId: String): Result<List<SaveData>>
    suspend fun uploadActiveSaveData(gameId: String, data: ByteArray): Result<SaveData>
    suspend fun downloadActiveSaveData(gameId: String): Result<ByteArray>
    suspend fun downloadSaveData(gameId: String, saveDataId: String): Result<ByteArray>
    suspend fun activateSaveData(gameId: String, saveDataId: String): Result<Unit>
    suspend fun renameSaveData(gameId: String, saveDataId: String, name: String): Result<Unit>
    suspend fun deleteSaveData(gameId: String, saveDataId: String): Result<Unit>
    suspend fun saveLocalSRAM(gameId: String, data: ByteArray)
    suspend fun loadLocalSRAM(gameId: String): ByteArray?
    suspend fun getPendingSyncCount(): Int
}
