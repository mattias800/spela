package com.spela.player.domain.usecase

import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.OnlineUser
import com.spela.player.domain.repository.SocialRepository

class GetOnlineUsersUseCase(private val socialRepository: SocialRepository) {
    suspend operator fun invoke(): Result<List<OnlineUser>> = socialRepository.getOnlineUsers()
}

class GetActivityFeedUseCase(private val socialRepository: SocialRepository) {
    suspend operator fun invoke(page: Int = 1, pageSize: Int = 20): Result<List<ActivityEvent>> =
        socialRepository.getActivityFeed(page = page, pageSize = pageSize)
}
