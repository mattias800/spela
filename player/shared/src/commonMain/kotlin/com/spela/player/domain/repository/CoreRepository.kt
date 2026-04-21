package com.spela.player.domain.repository

import com.spela.player.domain.model.LibretroCore

/**
 * Thrown when a version-pinned core binary (identified by sha256) is
 * requested but the server has already pruned it from its history
 * retention window. Caller is expected to fall back to the latest
 * unversioned core and surface a non-blocking warning to the user.
 */
class CorePrunedException(sha256: String) : RuntimeException(
    "Historical core binary with sha256=$sha256 is no longer available on the server.",
)

interface CoreRepository {
    suspend fun getAvailableCores(): Result<List<LibretroCore>>
    suspend fun getRecommendedCore(gameId: String): Result<LibretroCore>
    suspend fun downloadCore(coreName: String, downloadUrl: String? = null, onProgress: (Float) -> Unit = {}): Result<String>

    /**
     * Downloads a specific historical build of [coreName] identified by
     * [sha256]. On success returns the local path of the resolved binary
     * (may be platform-specific). When the server has pruned that build,
     * returns `Result.failure(CorePrunedException)` — callers decide
     * whether to fall back to the latest core.
     *
     * Part of #555 Phase 3 core history retention.
     */
    suspend fun downloadCoreByHash(coreName: String, sha256: String, onProgress: (Float) -> Unit = {}): Result<String>

    suspend fun getLocalCorePath(coreName: String): String?
    suspend fun isCoreCached(coreName: String): Boolean
}
