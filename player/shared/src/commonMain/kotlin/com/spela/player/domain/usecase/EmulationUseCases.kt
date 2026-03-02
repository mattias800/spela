package com.spela.player.domain.usecase

import com.spela.player.domain.repository.CoreRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.SaveRepository
import com.spela.player.util.currentPlatform

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
)

class PrepareGameUseCase(
    private val downloadRepository: DownloadRepository,
    private val coreRepository: CoreRepository,
) {
    /**
     * Ensures both the game ROM and the required libretro core are available locally.
     * Returns a pair of (gamePath, corePath).
     */
    suspend operator fun invoke(gameId: String): Result<Pair<String, String>> {
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
                    return Result.success(gamePath to localPath)
                }
            }
            return Result.failure(networkError)
        }

        val coreName = platformCoreSubstitution(core.name)

        val corePath = coreRepository.getLocalCorePath(coreName)
            ?: run {
                coreRepository.downloadCore(coreName).getOrElse {
                    return Result.failure(it)
                }
            }

        return Result.success(gamePath to corePath)
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

class SaveGameStateUseCase(private val saveRepository: SaveRepository) {
    suspend operator fun invoke(gameId: String, data: ByteArray, screenshot: ByteArray? = null, coreName: String? = null): Result<Unit> {
        return saveRepository.uploadAutoSaveWithScreenshot(gameId, data, screenshot, coreName).map { }
    }
}

class LoadGameStateUseCase(private val saveRepository: SaveRepository) {
    suspend operator fun invoke(gameId: String): Result<ByteArray> {
        return saveRepository.downloadAutoSave(gameId)
    }
}
