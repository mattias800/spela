package com.spela.player.domain.repository

import com.spela.player.domain.model.LibretroCore

interface CoreRepository {
    suspend fun getAvailableCores(): Result<List<LibretroCore>>
    suspend fun getRecommendedCore(gameId: String): Result<LibretroCore>
    suspend fun downloadCore(coreId: String, onProgress: (Float) -> Unit = {}): Result<String>
    suspend fun getLocalCorePath(coreId: String): String?
    suspend fun isCoreCached(coreId: String): Boolean
}
