package com.spela.player.domain.repository

import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.HeatmapEntry
import com.spela.player.domain.model.OnlineUser
import com.spela.player.domain.model.PublicProfile

interface SocialRepository {
    suspend fun getOnlineUsers(): Result<List<OnlineUser>>
    suspend fun getActivityFeed(page: Int = 1, pageSize: Int = 20): Result<List<ActivityEvent>>
    suspend fun getPublicProfile(userId: String): Result<PublicProfile>
    suspend fun getPlayHeatmap(userId: String): Result<List<HeatmapEntry>>
}
