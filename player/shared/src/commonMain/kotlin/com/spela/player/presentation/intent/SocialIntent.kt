package com.spela.player.presentation.intent

import com.spela.player.domain.model.UnlockedAchievement

sealed interface SocialIntent {
    data object LoadOnlineUsers : SocialIntent
    data object LoadActivityFeed : SocialIntent
    data object RefreshAll : SocialIntent
    data object DismissError : SocialIntent
    data class LoadPublicProfile(val userId: String) : SocialIntent
    data object LoadFullActivityFeed : SocialIntent
    data object LoadMoreActivity : SocialIntent
    // Showcase editing
    data object OpenShowcaseEditor : SocialIntent
    data object CloseShowcaseEditor : SocialIntent
    data class ToggleShowcaseAchievement(val achievement: UnlockedAchievement) : SocialIntent
    data class MoveShowcaseAchievement(val index: Int, val direction: Int) : SocialIntent
    data object SaveShowcase : SocialIntent
    data class SetShowcaseSearchQuery(val query: String) : SocialIntent
}
