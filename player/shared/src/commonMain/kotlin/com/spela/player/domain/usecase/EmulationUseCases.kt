package com.spela.player.domain.usecase

import com.spela.player.domain.repository.CorePrunedException
import com.spela.player.domain.repository.CoreRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.util.currentPlatform

/**
 * Result of preparing a game for emulation. Carries both the resolved
 * file paths and an optional user-facing warning when we had to fall
 * back from a pinned core version.
 */
data class PrepareGameResult(
    val gamePath: String,
    val corePath: String,
    /**
     * Non-null when the session had a pinned core sha256 but that
     * historical binary was no longer available (pruned) and we
     * silently fell back to the latest core. UI surfaces this as a
     * non-blocking warning. Null when no pin, or the pin was satisfied.
     */
    val coreVersionWarning: String? = null,
)

/**
 * Some HW-accelerated cores don't work on macOS due to Metal-backed GL
 * texture incompatibilities. Map them to their software equivalents.
 */
private val MACOS_CORE_SUBSTITUTIONS = mapOf(
    "beetle_psx_hw" to "mednafen_psx",
)

/**
 * The libretro buildbot hosts Android N64 cores under variant-specific names
 * (e.g. mupen64plus_next_gles3) rather than the base name. Map accordingly.
 */
private val ANDROID_CORE_SUBSTITUTIONS = mapOf(
    "mupen64plus_next" to "mupen64plus_next_gles3",
    "beetle_psx_hw" to "mednafen_psx_hw",
    "beetle_psx" to "mednafen_psx",
    "beetle_pce" to "mednafen_pce_fast",
    "beetle_saturn" to "mednafen_saturn",
    "beetle_vb" to "mednafen_vb",
    "beetle_ngp" to "mednafen_ngp",
    "beetle_wswan" to "mednafen_wswan",
    "beetle_pcfx" to "mednafen_pcfx",
)

class PrepareGameUseCase(
    private val downloadRepository: DownloadRepository,
    private val coreRepository: CoreRepository,
) {
    /** Warning copy shown to the user when the session's pinned core is no longer on the server. */
    private val prunedWarning =
        "Original core version no longer available. The latest core may not load this save correctly."

    /**
     * Ensures both the game ROM and the required libretro core are available locally.
     *
     * When [pinnedCoreSha256] is non-null the use case first attempts a
     * hash-versioned core download (from the server-side history snapshot).
     * If the historical binary has been pruned, we fall back to the
     * unversioned download path and surface [prunedWarning] via the
     * returned [PrepareGameResult.coreVersionWarning]. Save-state failures
     * downstream still go through the existing error path.
     *
     * Returns a [PrepareGameResult] carrying the resolved paths and
     * (optionally) the fallback warning.
     */
    suspend operator fun invoke(
        gameId: String,
        pinnedCoreSha256: String? = null,
    ): Result<PrepareGameResult> {
        val gamePath = downloadRepository.getLocalGamePath(gameId)
            ?: return Result.failure(IllegalStateException("Game not downloaded"))

        val core = coreRepository.getRecommendedCore(gameId).getOrElse { networkError ->
            // Fallback: if server is unreachable, try locally cached cores
            println("[PrepareGame] getRecommendedCore failed: ${networkError.message}, trying local fallback")
            val knownCores = listOf("ppsspp", "desmume", "mednafen_psx_hw", "mednafen_psx", "beetle_psx_hw", "mupen64plus_next", "snes9x", "nestopia", "mgba", "gambatte", "genesis_plus_gx")
            for (coreName in knownCores) {
                val localPath = coreRepository.getLocalCorePath(coreName)
                if (localPath != null) {
                    println("[PrepareGame] Using local fallback core: $localPath")
                    return Result.success(PrepareGameResult(gamePath, localPath))
                }
            }
            return Result.failure(networkError)
        }

        val coreName = platformCoreSubstitution(core.name)
        // When the core name was substituted, the server's download URL is for the
        // original name. Clear it so CoreRepository falls back to the buildbot URL
        // constructed from the substituted name.
        val downloadUrl = if (coreName != core.name) null else core.downloadUrl

        // Pinned-core path — #555 Phase 3 session-to-core binding.
        // We attempt the versioned download only when the session has a
        // pin; otherwise we use the regular local-cache-or-download flow.
        // Platform core substitutions (macOS Metal, Android variants)
        // intentionally skip the hash path because the pinned sha refers
        // to the ORIGINAL core name's binary, not the substitute's.
        if (pinnedCoreSha256 != null && coreName == core.name) {
            val versioned = coreRepository.downloadCoreByHash(coreName, pinnedCoreSha256)
            val pinnedPath = versioned.getOrNull()
            if (pinnedPath != null) {
                return Result.success(PrepareGameResult(gamePath, pinnedPath))
            }
            val error = versioned.exceptionOrNull()
            if (error is CorePrunedException) {
                println("[PrepareGame] Pinned core $pinnedCoreSha256 pruned — falling back to latest")
                val fallbackPath = coreRepository.getLocalCorePath(coreName)
                    ?: coreRepository.downloadCore(coreName, downloadUrl).getOrElse {
                        return Result.failure(it)
                    }
                return Result.success(
                    PrepareGameResult(gamePath, fallbackPath, coreVersionWarning = prunedWarning),
                )
            }
            // Any other failure: treat as a transient issue — fall back
            // to unversioned so gameplay still works. No warning surface
            // in this case: the latest core is also the pinned version
            // on every-other-update, and the user doesn't need to know
            // about a transient network glitch.
            println("[PrepareGame] Versioned core download failed (${error?.message}) — falling back to latest")
        }

        // Unpinned path: if a local copy exists but the server's current
        // sha256 differs (core was updated upstream since we last
        // downloaded), silently refresh it so the user is on the latest
        // build. `isCachedCoreCurrent` returns `null` for the
        // "can't decide" case (no local file, server hasn't fingerprinted,
        // network hiccup, hash failed) — we intentionally fall through to
        // the existing path-existence check in that case so transient
        // server issues don't block an otherwise-usable cached core.
        // See #555 Phase 2.
        val cached = coreRepository.getLocalCorePath(coreName)
        val corePath = if (cached != null && coreRepository.isCachedCoreCurrent(coreName) == false) {
            println("[PrepareGame] Cached $coreName is stale vs server — redownloading")
            coreRepository.downloadCore(coreName, downloadUrl).getOrElse {
                // Re-download failed: fall back to the stale cache rather
                // than stranding the user. The game still runs; saves
                // made here might not load on a fresh install, but that's
                // strictly better than refusing to launch.
                println("[PrepareGame] Redownload failed, keeping stale cache: ${it.message}")
                cached
            }
        } else {
            cached ?: coreRepository.downloadCore(coreName, downloadUrl).getOrElse {
                return Result.failure(it)
            }
        }

        return Result.success(PrepareGameResult(gamePath, corePath))
    }

    /**
     * Substitutes HW-accelerated cores with software equivalents on platforms
     * where the HW cores don't work (e.g. macOS Metal-backed GL).
     */
    private fun platformCoreSubstitution(coreName: String): String {
        val substitutions = when (currentPlatform()) {
            "macos" -> MACOS_CORE_SUBSTITUTIONS
            "android" -> ANDROID_CORE_SUBSTITUTIONS
            else -> return coreName
        }
        val substitute = substitutions[coreName] ?: return coreName
        println("[PrepareGame] ${currentPlatform()}: substituting $coreName -> $substitute")
        return substitute
    }
}

