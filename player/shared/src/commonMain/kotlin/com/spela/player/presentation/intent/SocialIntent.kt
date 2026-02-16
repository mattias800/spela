package com.spela.player.presentation.intent

sealed interface SocialIntent {
    data object LoadOnlineUsers : SocialIntent
    data object LoadActivityFeed : SocialIntent
    data object RefreshAll : SocialIntent
    data object DismissError : SocialIntent
    data class LoadPublicProfile(val userId: String) : SocialIntent
    data object LoadFullActivityFeed : SocialIntent
    data object LoadMoreActivity : SocialIntent
}
