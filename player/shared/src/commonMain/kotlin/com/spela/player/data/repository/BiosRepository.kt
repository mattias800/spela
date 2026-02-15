package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.util.FileStorage

class BiosRepository(
    private val apiClient: SpelaApiClient,
    private val fileStorage: FileStorage,
) {
    suspend fun syncBiosFiles() {
        val serverFiles = try {
            apiClient.listBiosFiles()
        } catch (e: Exception) {
            return // Server may not support BIOS yet
        }
        val biosDir = fileStorage.getBiosDir()
        for (file in serverFiles) {
            val localPath = "$biosDir/${file.name}"
            if (!fileStorage.fileExists(localPath)) {
                val data = apiClient.downloadBiosFile(file.name)
                fileStorage.writeFile(localPath, data)
            }
        }
    }

    suspend fun getLocalBiosFiles(): List<String> {
        val biosDir = fileStorage.getBiosDir()
        return java.io.File(biosDir).listFiles()?.map { it.name } ?: emptyList()
    }
}
