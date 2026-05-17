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

    /**
     * Downloads the latest binary for [coreName].
     *
     * [onProgress] receives raw byte counters as they're observed from
     * the underlying HTTP stream. `totalBytes` may be `null` for hosts
     * that don't send a Content-Length header; the UI should render an
     * indeterminate state in that case. Fires zero or more times during
     * the download and is not guaranteed to fire at all (e.g. when the
     * payload is fully buffered before any download progress reaches the
     * client). See #1192.
     */
    suspend fun downloadCore(
        coreName: String,
        customDownloadUrl: String? = null,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): Result<String>

    /**
     * Downloads a specific historical build of [coreName] identified by
     * [sha256]. On success returns the local path of the resolved binary
     * (may be platform-specific). When the server has pruned that build,
     * returns `Result.failure(CorePrunedException)` — callers decide
     * whether to fall back to the latest core.
     *
     * Part of #555 Phase 3 core history retention.
     */
    suspend fun downloadCoreByHash(
        coreName: String,
        sha256: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): Result<String>

    suspend fun getLocalCorePath(coreName: String): String?
    suspend fun isCoreCached(coreName: String): Boolean

    /**
     * Reports whether the locally cached binary for [coreName] matches the
     * server's current sha256. Used by the emulation path to decide whether
     * to silently re-download a stale core when a session has no pinned
     * version. See #555 Phase 2.
     *
     * Tri-state semantics:
     *   - `true`  — local cache matches the server's current sha256 and is
     *               safe to reuse.
     *   - `false` — local cache differs from the server; caller should
     *               discard it and re-download.
     *   - `null`  — can't decide (no local file, server hasn't fingerprinted
     *               yet, network failed, or local hash failed). Caller
     *               should fall back to the existing path-existence check
     *               so network/server hiccups don't lock users out of an
     *               otherwise-usable cached core.
     */
    suspend fun isCachedCoreCurrent(coreName: String): Boolean?

    /**
     * Returns the server's current sha256 for [coreName] via
     * `GET /api/cores/{id}/manifest`, or `null` when the server
     * hasn't fingerprinted the core yet, the core name is unknown,
     * or the network request fails. Used by
     * [com.spela.player.domain.usecase.PrepareGameUseCase] to decide
     * whether a session's pinned sha is still current without
     * re-downloading the binary. See #672.
     */
    suspend fun getServerCoreSha(coreName: String): String?
}
