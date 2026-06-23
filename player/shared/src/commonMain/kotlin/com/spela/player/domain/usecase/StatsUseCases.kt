package com.spela.player.domain.usecase

import com.spela.player.domain.model.ActivePlayer
import com.spela.player.domain.model.MeshAchiever
import com.spela.player.domain.model.MeshStat
import com.spela.player.domain.model.MeshStatMetric
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.domain.repository.FederationRepository
import com.spela.player.domain.repository.StatsRepository

class GetMostPlayedGamesUseCase(private val statsRepository: StatsRepository) {
    suspend operator fun invoke(): Result<List<MostPlayedGame>> =
        statsRepository.getMostPlayedGames()
}

class GetMostActivePlayersUseCase(private val statsRepository: StatsRepository) {
    suspend operator fun invoke(): Result<List<ActivePlayer>> =
        statsRepository.getMostActivePlayers()
}

// Federated (mesh) leaderboard for the given metric, across connected servers.
class GetMeshStatsUseCase(private val federationRepository: FederationRepository) {
    suspend operator fun invoke(metric: MeshStatMetric): Result<List<MeshStat>> =
        federationRepository.getAggregatedStats(metric)
}

// Federated "top achievers" leaderboard across connected servers.
class GetMeshAchieversUseCase(private val federationRepository: FederationRepository) {
    suspend operator fun invoke(): Result<List<MeshAchiever>> =
        federationRepository.getAggregatedAchievers()
}
