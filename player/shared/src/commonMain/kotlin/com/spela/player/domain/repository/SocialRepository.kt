package com.spela.player.domain.repository

import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.HeatmapEntry
import com.spela.player.domain.model.OnlineUser
import com.spela.player.domain.model.PublicProfile
import com.spela.player.domain.model.ShowcaseAchievement
import com.spela.player.domain.model.UnlockedAchievement

interface SocialRepository {
    suspend fun getOnlineUsers(): Result<List<OnlineUser>>
    suspend fun getActivityFeed(page: Int = 1, pageSize: Int = 20): Result<List<ActivityEvent>>
    suspend fun getPublicProfile(userId: String): Result<PublicProfile>
    suspend fun getPlayHeatmap(userId: String): Result<List<HeatmapEntry>>
    suspend fun getPublicShowcase(userId: String): Result<List<ShowcaseAchievement>>
    suspend fun getUnlockedAchievements(): Result<List<UnlockedAchievement>>
    suspend fun updateShowcase(achievements: List<ShowcaseAchievement>): Result<List<ShowcaseAchievement>>
}
