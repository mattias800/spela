package com.spela.player.domain.usecase

import com.spela.player.domain.model.ActivePlayer
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.domain.repository.StatsRepository

class GetMostPlayedGamesUseCase(private val statsRepository: StatsRepository) {
    suspend operator fun invoke(): Result<List<MostPlayedGame>> =
        statsRepository.getMostPlayedGames()
}

class GetMostActivePlayersUseCase(private val statsRepository: StatsRepository) {
    suspend operator fun invoke(): Result<List<ActivePlayer>> =
        statsRepository.getMostActivePlayers()
}
