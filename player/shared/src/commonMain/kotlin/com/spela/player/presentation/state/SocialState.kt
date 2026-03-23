package com.spela.player.presentation.state

import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.HeatmapEntry
import com.spela.player.domain.model.OnlineUser
import com.spela.player.domain.model.PublicProfile
import com.spela.player.domain.model.ShowcaseAchievement

data class SocialState(
    val onlineUsers: List<OnlineUser> = emptyList(),
    val activityEvents: List<ActivityEvent> = emptyList(),
    val isLoadingOnline: Boolean = false,
    val isLoadingActivity: Boolean = false,
    val error: String? = null,
    val publicProfile: PublicProfile? = null,
    val isLoadingProfile: Boolean = false,
    val heatmapData: List<HeatmapEntry> = emptyList(),
    val showcaseAchievements: List<ShowcaseAchievement> = emptyList(),
    // Full activity feed (paginated, for the Activity screen)
    val fullActivityEvents: List<ActivityEvent> = emptyList(),
    val isLoadingFullActivity: Boolean = false,
    val fullActivityPage: Int = 1,
    val hasMoreActivity: Boolean = true,
)
