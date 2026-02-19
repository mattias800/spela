package com.spela.player.presentation.state

import com.spela.player.domain.model.AchievementPlayerRanking
import com.spela.player.domain.model.AchievementProgress
import com.spela.player.domain.model.AchievementTimelineData
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.GameAchievement
import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.GameRating
import com.spela.player.domain.model.GameStats
import com.spela.player.domain.model.RatingSummary
import com.spela.player.domain.model.Relay
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SharedSaveState

enum class AchievementsViewMode { GRID, TIMELINE, LEADERBOARD }

data class GameDetailState(
    val gameDetail: GameDetail? = null,
    val saveStates: List<SaveState> = emptyList(),
    val sharedSaves: List<SharedSaveState> = emptyList(),
    val downloadProgress: DownloadProgress? = null,
    val isGameCached: Boolean = false,
    val isLoading: Boolean = false,
    val isScraping: Boolean = false,
    val isSharing: Boolean = false,
    val myRating: Int? = null,
    val ratingSummary: RatingSummary? = null,
    val isRating: Boolean = false,
    val showAddToCollectionDialog: Boolean = false,
    val userCollections: List<GameCollection> = emptyList(),
    val isLoadingCollections: Boolean = false,
    // Community Stats
    val gameStats: GameStats? = null,
    val isLoadingStats: Boolean = false,
    // Achievements
    val achievements: List<GameAchievement> = emptyList(),
    val achievementProgress: List<AchievementProgress> = emptyList(),
    val achievementTimeline: AchievementTimelineData? = null,
    val achievementLeaderboard: List<AchievementPlayerRanking> = emptyList(),
    val achievementsView: AchievementsViewMode = AchievementsViewMode.GRID,
    val isLoadingAchievements: Boolean = false,
    // Reviews
    val reviews: List<GameRating> = emptyList(),
    val reviewsTotal: Long = 0,
    val reviewsPage: Int = 1,
    val isLoadingReviews: Boolean = false,
    // Active Relays
    val gameRelays: List<Relay> = emptyList(),
    val isLoadingRelays: Boolean = false,
    // Create Challenge
    val showCreateChallengeDialog: Boolean = false,
    val isCreatingChallenge: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
)
