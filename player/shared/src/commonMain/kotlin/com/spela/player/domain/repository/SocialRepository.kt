package com.spela.player.domain.repository

import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.OnlineUser

interface SocialRepository {
    suspend fun getOnlineUsers(): Result<List<OnlineUser>>
    suspend fun getActivityFeed(page: Int = 1, pageSize: Int = 20): Result<List<ActivityEvent>>
}
