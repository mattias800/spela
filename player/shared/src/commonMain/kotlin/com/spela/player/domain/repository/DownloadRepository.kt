package com.spela.player.domain.repository

import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadedGame
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadProgress>>
    fun observeDownload(gameId: String): Flow<DownloadProgress>
    fun observeDownloadedGames(): Flow<List<DownloadedGame>>
    suspend fun downloadGame(gameId: String, gameTitle: String = ""): Result<String>
    suspend fun cancelDownload(gameId: String)
    suspend fun getLocalGamePath(gameId: String): String?
    suspend fun isGameCached(gameId: String): Boolean
    suspend fun deleteLocalGame(gameId: String)
    suspend fun getCacheSize(): Long
    suspend fun clearCache()
}
