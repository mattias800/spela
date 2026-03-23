package com.spela.player.domain.usecase

import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.HeatmapEntry
import com.spela.player.domain.model.OnlineUser
import com.spela.player.domain.model.PublicProfile
import com.spela.player.domain.model.ShowcaseAchievement
import com.spela.player.domain.model.UnlockedAchievement
import com.spela.player.domain.repository.SocialRepository

class GetOnlineUsersUseCase(private val socialRepository: SocialRepository) {
    suspend operator fun invoke(): Result<List<OnlineUser>> = socialRepository.getOnlineUsers()
}

class GetActivityFeedUseCase(private val socialRepository: SocialRepository) {
    suspend operator fun invoke(page: Int = 1, pageSize: Int = 20): Result<List<ActivityEvent>> =
        socialRepository.getActivityFeed(page = page, pageSize = pageSize)
}

class GetPublicProfileUseCase(private val repository: SocialRepository) {
    suspend operator fun invoke(userId: String): Result<PublicProfile> = repository.getPublicProfile(userId)
}

class GetPlayHeatmapUseCase(private val repository: SocialRepository) {
    suspend operator fun invoke(userId: String): Result<List<HeatmapEntry>> = repository.getPlayHeatmap(userId)
}

class GetPublicShowcaseUseCase(private val repository: SocialRepository) {
    suspend operator fun invoke(userId: String): Result<List<ShowcaseAchievement>> = repository.getPublicShowcase(userId)
}

class GetUnlockedAchievementsUseCase(private val repository: SocialRepository) {
    suspend operator fun invoke(): Result<List<UnlockedAchievement>> = repository.getUnlockedAchievements()
}

class UpdateShowcaseUseCase(private val repository: SocialRepository) {
    suspend operator fun invoke(achievements: List<ShowcaseAchievement>): Result<List<ShowcaseAchievement>> =
        repository.updateShowcase(achievements)
}
