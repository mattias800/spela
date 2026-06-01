package com.spela.player.domain.repository

import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadedGame
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadProgress>>
    fun observeDownload(gameId: String): Flow<DownloadProgress>
    fun observeDownloadedGames(): Flow<List<DownloadedGame>>
    suspend fun downloadGame(gameId: String, gameTitle: String = ""): Result<String>

    /**
     * Downloads a game's file directly into [destDir] (an absolute path the
     * user chose), bypassing the managed cache and DB tracking. For games
     * Spela can't emulate, where the file is only useful outside the app
     * (#1257). Progress is emitted via [observeDownload] keyed by gameId.
     * Default is a no-op failure for fakes that don't support it.
     */
    suspend fun downloadGameToDirectory(
        gameId: String,
        gameTitle: String,
        destDir: String,
    ): Result<String> = Result.failure(UnsupportedOperationException("downloadGameToDirectory not supported"))

    suspend fun cancelDownload(gameId: String)
    suspend fun getLocalGamePath(gameId: String): String?
    suspend fun isGameCached(gameId: String): Boolean
    suspend fun deleteLocalGame(gameId: String)
    suspend fun getCacheSize(): Long
    suspend fun clearCache()

    /**
     * Walks the games directory and removes any per-game subdirectory
     * that has no row in the local downloads table. Cleans up partial
     * files left behind by app/process death mid-download. Idempotent.
     * Should be called once at app launch. See #845.
     */
    suspend fun scanForOrphanedDownloads()
}
