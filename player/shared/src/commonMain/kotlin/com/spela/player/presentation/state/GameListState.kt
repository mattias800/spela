package com.spela.player.presentation.state

import com.spela.player.domain.model.Challenge
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.RecentAchievement
import com.spela.player.domain.model.UserStats

enum class ViewMode {
    GRID,
    LIST,
}

data class GameListState(
    val consoles: List<Console> = emptyList(),
    val games: List<Game> = emptyList(),
    val recentGames: List<Game> = emptyList(),
    val favoriteGames: List<Game> = emptyList(),
    val playLaterGames: List<Game> = emptyList(),
    val selectedConsoleId: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedConsoleFilter: String? = null,
    val sortBy: String = "title",
    val sortOrder: String = "asc",
    val viewMode: ViewMode = ViewMode.GRID,
    val personalStats: UserStats? = null,
    val isLoadingPersonalStats: Boolean = false,
    val recentAchievements: List<RecentAchievement> = emptyList(),
    val isLoadingAchievements: Boolean = false,
    val trendingChallenges: List<Challenge> = emptyList(),
    val isLoadingTrendingChallenges: Boolean = false,
    val consolesWithMissingBios: Set<String> = emptySet(),
)
