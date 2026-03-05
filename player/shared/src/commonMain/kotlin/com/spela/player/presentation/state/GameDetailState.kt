package com.spela.player.presentation.state

import com.spela.player.domain.model.AchievementPlayerRanking
import com.spela.player.domain.model.AchievementProgress
import com.spela.player.domain.model.AchievementTimelineData
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.GameAchievement
import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.GameRating
import com.spela.player.domain.model.GameStats
import com.spela.player.domain.model.QuickSaveSlot
import com.spela.player.domain.model.RatingSummary
import com.spela.player.domain.model.Relay
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.domain.model.SimilarGame
import com.spela.player.domain.model.Cheat
import com.spela.player.domain.model.GameSession
import com.spela.player.domain.model.StorageUsage

enum class AchievementsViewMode { GRID, TIMELINE, LEADERBOARD }

data class GameDetailState(
    val gameDetail: GameDetail? = null,
    val saveStates: List<SaveState> = emptyList(),
    val sharedSaves: List<SharedSaveState> = emptyList(),
    val downloadProgress: DownloadProgress? = null,
    val isGameCached: Boolean = false,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val isScraping: Boolean = false,
    val isSharing: Boolean = false,
    val myRating: Int? = null,
    val ratingSummary: RatingSummary? = null,
    val isRating: Boolean = false,
    val showAddToCollectionDialog: Boolean = false,
    val userCollections: List<GameCollection> = emptyList(),
    val isLoadingCollections: Boolean = false,
    val isCreatingCollection: Boolean = false,
    val collectionCreationError: String? = null,
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
    // Save Data (SRAM)
    val saveDataCount: Int = 0,
    // Delete Download
    val showDeleteDownloadDialog: Boolean = false,
    // Create Challenge
    val showCreateChallengeDialog: Boolean = false,
    val isCreatingChallenge: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
    // Similar Games & Developer Games
    val similarGames: List<SimilarGame> = emptyList(),
    val developerGames: List<DeveloperGame> = emptyList(),
    val developerName: String? = null,
    val isLoadingSimilar: Boolean = false,
    val isLoadingDeveloperGames: Boolean = false,
    // BIOS
    val missingBiosFiles: List<BiosMissingFile> = emptyList(),

    // Feature 3: Sync status
    val unsyncedSaveCount: Int = 0,

    // Feature 5: Quick-save slots
    val quickSaveSlots: List<QuickSaveSlot> = emptyList(),

    // Feature 6: Auto-save history
    val autoSaveHistory: List<SaveState> = emptyList(),

    // Feature 7: Bulk delete
    val isSelectionMode: Boolean = false,
    val selectedSaveIds: Set<Long> = emptySet(),

    // Feature 8: Storage usage
    val storageUsage: StorageUsage? = null,

    // Cheats
    val cheats: List<Cheat> = emptyList(),
    val isLoadingCheats: Boolean = false,
    val showCheatDialog: Boolean = false,

    // Sessions
    val sessions: List<GameSession> = emptyList(),
    val isLoadingSessions: Boolean = false,
)
