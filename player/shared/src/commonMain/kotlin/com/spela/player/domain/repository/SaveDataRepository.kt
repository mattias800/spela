package com.spela.player.domain.repository

interface SaveDataRepository {
    suspend fun saveLocalSRAM(gameId: String, data: ByteArray)
    suspend fun loadLocalSRAM(gameId: String): ByteArray?
    suspend fun getPendingSyncCount(): Int
    suspend fun zipSaveDirectory(gameId: String): ByteArray?
    suspend fun unzipToSaveDirectory(data: ByteArray)
}
