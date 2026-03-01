package com.spela.player.util

/**
 * Platform-specific file storage abstraction.
 * Implemented separately on Android and Desktop.
 */
interface FileStorage {
    fun getGamesDir(): String
    fun getCoresDir(): String
    fun getSavesDir(): String
    fun getBiosDir(): String
    suspend fun createDirectory(path: String)
    suspend fun writeFile(path: String, data: ByteArray)
    suspend fun readFile(path: String): ByteArray
    suspend fun fileExists(path: String): Boolean
    suspend fun deleteFile(path: String)
    suspend fun deleteDirectory(path: String)
    suspend fun getDirectorySize(path: String): Long
    suspend fun writeFileStreaming(
        path: String,
        writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit,
    )
    suspend fun getFileSize(path: String): Long
    suspend fun listFiles(path: String): List<String>
    suspend fun isDirectory(path: String): Boolean
    suspend fun zipDirectoryToBytes(dirPath: String): ByteArray?
    suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String)
}
