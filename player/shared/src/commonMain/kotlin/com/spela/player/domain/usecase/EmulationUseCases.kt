package com.spela.player.domain.usecase

import com.spela.player.domain.repository.CoreRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.SaveRepository

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

        val core = coreRepository.getRecommendedCore(gameId).getOrElse {
            return Result.failure(it)
        }

        val corePath = coreRepository.getLocalCorePath(core.id)
            ?: run {
                coreRepository.downloadCore(core.id).getOrElse {
                    return Result.failure(it)
                }
            }

        return Result.success(gamePath to corePath)
    }
}

class SaveGameStateUseCase(private val saveRepository: SaveRepository) {
    suspend operator fun invoke(gameId: String, data: ByteArray): Result<Unit> {
        return saveRepository.uploadAutoSave(gameId, data).map { }
    }
}

class LoadGameStateUseCase(private val saveRepository: SaveRepository) {
    suspend operator fun invoke(gameId: String): Result<ByteArray> {
        return saveRepository.downloadAutoSave(gameId)
    }
}
