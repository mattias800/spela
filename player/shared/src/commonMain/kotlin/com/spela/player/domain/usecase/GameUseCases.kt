package com.spela.player.domain.usecase

import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.repository.GameRepository

class GetConsolesUseCase(private val gameRepository: GameRepository) {
    suspend operator fun invoke(): Result<List<Console>> = gameRepository.getConsoles()
}

class GetGamesForConsoleUseCase(private val gameRepository: GameRepository) {
    suspend operator fun invoke(consoleId: String): Result<List<Game>> =
        gameRepository.getGamesForConsole(consoleId)
}

class GetAllGamesUseCase(private val gameRepository: GameRepository) {
    suspend operator fun invoke(): Result<List<Game>> = gameRepository.getAllGames()
}

class SearchGamesUseCase(private val gameRepository: GameRepository) {
    suspend operator fun invoke(query: String): Result<List<Game>> =
        gameRepository.searchGames(query)
}

class GetGameDetailUseCase(private val gameRepository: GameRepository) {
    suspend operator fun invoke(gameId: String): Result<GameDetail> =
        gameRepository.getGameDetail(gameId)
}

class GetRecentGamesUseCase(private val gameRepository: GameRepository) {
    suspend operator fun invoke(): Result<List<Game>> = gameRepository.getRecentGames()
}

class GetFavoriteGamesUseCase(private val gameRepository: GameRepository) {
    suspend operator fun invoke(): Result<List<Game>> = gameRepository.getFavoriteGames()
}

class ToggleFavoriteUseCase(private val gameRepository: GameRepository) {
    suspend operator fun invoke(gameId: String, isFavorite: Boolean): Result<Unit> {
        return if (isFavorite) {
            gameRepository.removeFavorite(gameId)
        } else {
            gameRepository.addFavorite(gameId)
        }
    }
}
