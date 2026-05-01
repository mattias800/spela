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

    /**
     * Streams the first entry of the zip at [zipPath] to [destPath]
     * without buffering the entire payload in memory. Used for the
     * libretro core download path: buildbot serves each core as a
     * `.so.zip` (one entry, no compression on the .so itself), and
     * the largest cores (e.g. scummvm at ~134 MB) overflow the
     * default Android heap when buffered as a `ByteArray`. See #849.
     */
    suspend fun extractFirstZipEntryFromFile(zipPath: String, destPath: String)

    /**
     * Tars the contents of [dirPath] (recursively) into [destPath]. The
     * tar contains entries named relative to [dirPath] — `dirPath/foo/bar`
     * lands in the tar as `foo/bar`. Streams to disk; the tar isn't
     * materialized in memory. Used to bundle a libretro save_dir for
     * upload (#864). Returns the size in bytes of the written tar.
     * Returns 0 when [dirPath] doesn't exist or is empty.
     */
    suspend fun tarDirectoryToFile(dirPath: String, destPath: String): Long

    /**
     * Extracts a tar file at [tarPath] into [destDir] (recursively).
     * Companion to [tarDirectoryToFile] for the save_dir bundle download
     * path (#864). Streams from disk — tar is never fully buffered in
     * memory. The destination dir is created if missing; existing files
     * with the same path are overwritten.
     */
    suspend fun extractTarFile(tarPath: String, destDir: String)

    /**
     * Streaming SHA-256 of the file at [path], returned as a lowercase hex
     * string. Returns `null` when the path doesn't exist or can't be read —
     * callers treat that as "cannot decide" and should fall back to the
     * pessimistic choice (e.g. redownload). Used by the core-cache
     * invalidation path (#555 Phase 2).
     */
    suspend fun sha256File(path: String): String?
}
