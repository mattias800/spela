package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.*
import com.spela.player.domain.repository.GameStatsRepository

class GameStatsRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : GameStatsRepository {

    override suspend fun getGameStats(gameId: String): Result<GameStats> = runCatching {
        val stats = apiClient.getGameStats(gameId).toDomain()
        stats.copy(
            topPlayers = stats.topPlayers.map { it.copy(avatarUrl = apiClient.resolveUrl(it.avatarUrl)) },
        )
    }

    override suspend fun getGameAchievements(gameId: String): Result<List<GameAchievement>> = runCatching {
        apiClient.getGameAchievements(gameId).achievements.map { dto ->
            val achievement = dto.toDomain()
            achievement.copy(badgeUrl = apiClient.resolveUrl(achievement.badgeUrl))
        }
    }

    override suspend fun getAchievementProgress(gameId: String): Result<List<AchievementProgress>> = runCatching {
        apiClient.getAchievementProgress(gameId).progress.map { it.toDomain() }
    }

    override suspend fun getAchievementTimeline(gameId: String): Result<AchievementTimelineData> = runCatching {
        val data = apiClient.getAchievementTimeline(gameId).toDomain()
        data.copy(
            timeline = data.timeline.map { it.copy(badgeUrl = apiClient.resolveUrl(it.badgeUrl)) },
        )
    }

    override suspend fun getAchievementLeaderboard(gameId: String): Result<List<AchievementPlayerRanking>> = runCatching {
        apiClient.getAchievementLeaderboard(gameId).leaderboard.orEmpty().map { dto ->
            val ranking = dto.toDomain()
            ranking.copy(avatarUrl = apiClient.resolveUrl(ranking.avatarUrl))
        }
    }

    override suspend fun getUserStats(): Result<UserStats> = runCatching {
        val stats = apiClient.getUserStats().toDomain()
        stats.copy(
            mostPlayedGame = stats.mostPlayedGame?.copy(
                coverUrl = apiClient.resolveUrl(stats.mostPlayedGame.coverUrl),
            ),
        )
    }

    override suspend fun getRecentAchievements(): Result<List<RecentAchievement>> = runCatching {
        apiClient.getRecentAchievements().achievements.orEmpty().map { dto ->
            val achievement = dto.toDomain()
            achievement.copy(
                badgeUrl = apiClient.resolveUrl(achievement.badgeUrl),
                coverUrl = apiClient.resolveUrl(achievement.coverUrl),
            )
        }
    }
}
