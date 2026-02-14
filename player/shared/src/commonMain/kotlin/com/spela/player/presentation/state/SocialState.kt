package com.spela.player.presentation.state

import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.OnlineUser

data class SocialState(
    val onlineUsers: List<OnlineUser> = emptyList(),
    val activityEvents: List<ActivityEvent> = emptyList(),
    val isLoadingOnline: Boolean = false,
    val isLoadingActivity: Boolean = false,
    val error: String? = null,
)
