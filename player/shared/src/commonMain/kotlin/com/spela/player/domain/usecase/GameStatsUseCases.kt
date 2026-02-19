package com.spela.player.domain.usecase

import com.spela.player.domain.model.*
import com.spela.player.domain.repository.GameStatsRepository

class GetGameStatsUseCase(private val gameStatsRepository: GameStatsRepository) {
    suspend operator fun invoke(gameId: String): Result<GameStats> =
        gameStatsRepository.getGameStats(gameId)
}

class GetGameAchievementsUseCase(private val gameStatsRepository: GameStatsRepository) {
    suspend operator fun invoke(gameId: String): Result<List<GameAchievement>> =
        gameStatsRepository.getGameAchievements(gameId)
}

class GetAchievementProgressUseCase(private val gameStatsRepository: GameStatsRepository) {
    suspend operator fun invoke(gameId: String): Result<List<AchievementProgress>> =
        gameStatsRepository.getAchievementProgress(gameId)
}

class GetAchievementTimelineUseCase(private val gameStatsRepository: GameStatsRepository) {
    suspend operator fun invoke(gameId: String): Result<AchievementTimelineData> =
        gameStatsRepository.getAchievementTimeline(gameId)
}

class GetAchievementLeaderboardUseCase(private val gameStatsRepository: GameStatsRepository) {
    suspend operator fun invoke(gameId: String): Result<List<AchievementPlayerRanking>> =
        gameStatsRepository.getAchievementLeaderboard(gameId)
}

class GetUserStatsUseCase(private val gameStatsRepository: GameStatsRepository) {
    suspend operator fun invoke(): Result<UserStats> =
        gameStatsRepository.getUserStats()
}

class GetRecentAchievementsUseCase(private val gameStatsRepository: GameStatsRepository) {
    suspend operator fun invoke(): Result<List<RecentAchievement>> =
        gameStatsRepository.getRecentAchievements()
}
