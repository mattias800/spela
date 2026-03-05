package com.spela.player.domain.repository

import com.spela.player.domain.model.LibretroCore

interface CoreRepository {
    suspend fun getAvailableCores(): Result<List<LibretroCore>>
    suspend fun getRecommendedCore(gameId: String): Result<LibretroCore>
    suspend fun downloadCore(coreName: String, downloadUrl: String? = null, onProgress: (Float) -> Unit = {}): Result<String>
    suspend fun getLocalCorePath(coreName: String): String?
    suspend fun isCoreCached(coreName: String): Boolean
}
