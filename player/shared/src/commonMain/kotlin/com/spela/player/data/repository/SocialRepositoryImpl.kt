package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.ShowcaseUpdateEntry
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.HeatmapEntry
import com.spela.player.domain.model.OnlineUser
import com.spela.player.domain.model.PublicProfile
import com.spela.player.domain.model.ShowcaseAchievement
import com.spela.player.domain.model.UnlockedAchievement
import com.spela.player.domain.repository.SocialRepository

class SocialRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : SocialRepository {

    override suspend fun getOnlineUsers(): Result<List<OnlineUser>> = runCatching {
        apiClient.getOnlineUsers().users.map { dto ->
            val user = dto.toDomain()
            user.copy(
                avatarUrl = apiClient.resolveUrl(user.avatarUrl),
                currentGame = user.currentGame?.copy(
                    coverUrl = apiClient.resolveUrl(user.currentGame.coverUrl),
                ),
            )
        }
    }

    override suspend fun getActivityFeed(page: Int, pageSize: Int): Result<List<ActivityEvent>> = runCatching {
        apiClient.getActivityFeed(page = page, pageSize = pageSize).data.map { dto ->
            val event = dto.toDomain()
            event.copy(
                userAvatarUrl = apiClient.resolveUrl(event.userAvatarUrl),
                gameCoverUrl = apiClient.resolveUrl(event.gameCoverUrl),
            )
        }
    }

    override suspend fun getPublicProfile(userId: String): Result<PublicProfile> = runCatching {
        val dto = apiClient.getPublicProfile(userId)
        val profile = dto.toDomain()
        profile.copy(
            avatarUrl = apiClient.resolveUrl(profile.avatarUrl),
            currentGame = profile.currentGame?.copy(
                coverUrl = apiClient.resolveUrl(profile.currentGame.coverUrl),
            ),
            favoriteGames = profile.favoriteGames.map { it.copy(coverUrl = apiClient.resolveUrl(it.coverUrl)) },
            recentGames = profile.recentGames.map { it.copy(coverUrl = apiClient.resolveUrl(it.coverUrl)) },
            topGames = profile.topGames.map { it.copy(coverUrl = apiClient.resolveUrl(it.coverUrl)) },
        )
    }

    override suspend fun getPlayHeatmap(userId: String): Result<List<HeatmapEntry>> = runCatching {
        apiClient.getPublicPlayHeatmap(userId).map { it.toDomain() }
    }

    override suspend fun getPublicShowcase(userId: String): Result<List<ShowcaseAchievement>> = runCatching {
        apiClient.getPublicShowcase(userId).map { dto ->
            val achievement = dto.toDomain()
            achievement.copy(badgeUrl = apiClient.resolveUrl(achievement.badgeUrl))
        }
    }

    override suspend fun getUnlockedAchievements(): Result<List<UnlockedAchievement>> = runCatching {
        apiClient.getUnlockedAchievements().achievements.map { dto ->
            val achievement = dto.toDomain()
            achievement.copy(badgeUrl = apiClient.resolveUrl(achievement.badgeUrl))
        }
    }

    override suspend fun updateShowcase(achievements: List<ShowcaseAchievement>): Result<List<ShowcaseAchievement>> = runCatching {
        val entries = achievements.map { ShowcaseUpdateEntry(it.achievementRaId, it.raGameId) }
        apiClient.updateShowcase(entries).map { dto ->
            val achievement = dto.toDomain()
            achievement.copy(badgeUrl = apiClient.resolveUrl(achievement.badgeUrl))
        }
    }
}
