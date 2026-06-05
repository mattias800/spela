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

    /**
     * Resumes a paused or resumably-failed download from the bytes already on
     * disk. Requires a partial-download record (created when the download first
     * started, so it survives process death). Sends an HTTP Range request from
     * the current on-disk offset, guarded by the stored validator; if the
     * server reports the file changed it transparently restarts from scratch.
     * Returns the local path on success. On a resumable failure the partial is
     * kept and the state returns to PAUSED; on a terminal failure the partial
     * is discarded and the state is FAILED. (#1296)
     *
     * Default is a no-op failure for fakes that don't exercise resume; the
     * production [com.spela.player.data.repository.DownloadRepositoryImpl]
     * overrides it.
     */
    suspend fun resumeDownload(gameId: String): Result<String> =
        Result.failure(UnsupportedOperationException("resumeDownload not supported"))

    /**
     * Stops the in-flight transfer but KEEPS the partial so it can be resumed
     * later — an interrupted download is recoverable, not discarded. The state
     * becomes PAUSED. Use [deleteLocalGame] to remove a partial entirely. (#1296)
     */
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
