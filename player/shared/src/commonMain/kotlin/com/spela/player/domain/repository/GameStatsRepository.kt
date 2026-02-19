package com.spela.player.domain.repository

import com.spela.player.domain.model.*

interface GameStatsRepository {
    suspend fun getGameStats(gameId: String): Result<GameStats>
    suspend fun getGameAchievements(gameId: String): Result<List<GameAchievement>>
    suspend fun getAchievementProgress(gameId: String): Result<List<AchievementProgress>>
    suspend fun getAchievementTimeline(gameId: String): Result<AchievementTimelineData>
    suspend fun getAchievementLeaderboard(gameId: String): Result<List<AchievementPlayerRanking>>
    suspend fun getUserStats(): Result<UserStats>
    suspend fun getRecentAchievements(): Result<List<RecentAchievement>>
}
