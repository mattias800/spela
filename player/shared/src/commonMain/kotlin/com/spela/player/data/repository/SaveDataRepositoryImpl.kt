package com.spela.player.data.repository

import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.util.FileStorage

class SaveDataRepositoryImpl(
    private val fileStorage: FileStorage,
) : SaveDataRepository {

    override suspend fun saveLocalSRAM(gameId: String, data: ByteArray) {
        val dir = "${fileStorage.getSavesDir()}/sram/$gameId"
        val path = "$dir/active.srm"
        fileStorage.createDirectory(dir)
        // Atomic write: temp-file + rename. A process kill mid-write
        // (Android OOM, rotation crash) no longer leaves a truncated
        // active.srm that loadLocalSRAM would feed to the core as
        // corrupt save data. See #994.
        fileStorage.atomicWriteFile(path, data)
    }

    override suspend fun loadLocalSRAM(gameId: String): ByteArray? {
        val path = "${fileStorage.getSavesDir()}/sram/$gameId/active.srm"
        return if (fileStorage.fileExists(path)) {
            runCatching { fileStorage.readFile(path) }.getOrNull()
        } else null
    }

    override suspend fun getPendingSyncCount(): Int = 0

    override suspend fun zipSaveDirectory(gameId: String): ByteArray? {
        val userDir = "${fileStorage.getSavesDir()}/User"
        return fileStorage.zipDirectoryToBytes(userDir)
    }

    override suspend fun unzipToSaveDirectory(data: ByteArray) {
        val userDir = "${fileStorage.getSavesDir()}/User"
        fileStorage.unzipBytesToDirectory(data, userDir)
    }
}
