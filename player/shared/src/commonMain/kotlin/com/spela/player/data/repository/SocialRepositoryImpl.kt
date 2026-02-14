package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.OnlineUser
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
}
