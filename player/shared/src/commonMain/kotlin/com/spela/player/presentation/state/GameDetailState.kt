package com.spela.player.presentation.state

import com.spela.player.domain.model.AchievementPlayerRanking
import com.spela.player.domain.model.AchievementProgress
import com.spela.player.domain.model.AchievementTimelineData
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.GameAchievement
import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.GameFranchiseLink
import com.spela.player.domain.model.GameRating
import com.spela.player.domain.model.GameSeriesLink
import com.spela.player.domain.model.GameStats
import com.spela.player.domain.model.RatingSummary
import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.domain.model.PlaySemantics
import com.spela.player.domain.model.SaveStateChoice
import com.spela.player.domain.model.SimilarGame
import com.spela.player.domain.model.Cheat
import com.spela.player.domain.model.GameSession
import com.spela.player.domain.model.effectiveSaveStateChoice
import com.spela.player.domain.model.resolvePlaySemantics

enum class AchievementsViewMode { GRID, TIMELINE, LEADERBOARD }

data class GameDetailState(
    val gameDetail: GameDetail? = null,
    val console: Console? = null,
    val sharedSaves: List<SharedSaveState> = emptyList(),
    val downloadProgress: DownloadProgress? = null,
    val isGameCached: Boolean = false,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val isScraping: Boolean = false,
    val isScrapeQueued: Boolean = false,
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
    // Active Shared Sessions
    val gameSharedSessions: List<SharedSession> = emptyList(),
    val isLoadingSharedSessions: Boolean = false,
    // Delete Download
    val showDeleteDownloadDialog: Boolean = false,
    // Create Challenge
    val showCreateChallengeDialog: Boolean = false,
    val isCreatingChallenge: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
    val isAdminActionLoading: Boolean = false,
    val isAdmin: Boolean = false,
    // Similar Games & Developer Games
    val similarGames: List<SimilarGame> = emptyList(),
    val developerGames: List<DeveloperGame> = emptyList(),
    val developerName: String? = null,
    val isLoadingSimilar: Boolean = false,
    val isLoadingDeveloperGames: Boolean = false,
    // BIOS
    val missingBiosFiles: List<BiosMissingFile> = emptyList(),

    /**
     * The user's per-game save-state opt-out for this game, or null
     * when no override exists ("inherit from per-console policy").
     * Drives the tri-state radio on the game-detail options menu.
     * See #804 phase 4b spec point (c).
     */
    val gameSaveStatePolicy: com.spela.player.domain.model.SaveStateChoice? = null,

    /**
     * The user's per-console save-state opt-outs, mirrored from
     * [com.spela.player.domain.model.UserPreferences.consoleSaveStatePolicies].
     * Snapshotted into state so [playSemantics] can resolve the hero
     * label without round-tripping through the preferences repository
     * on every recomposition. See #884.
     */
    val userConsoleSaveStatePolicies: Map<String, SaveStateChoice> = emptyMap(),

    // Cheats (used by InGameOverlay, not displayed on game detail)
    val cheats: List<Cheat> = emptyList(),
    val isLoadingCheats: Boolean = false,

    // Sessions
    val sessions: List<GameSession> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val isPlayingFromSharedSave: Boolean = false,
    val playFromSharedSaveSessionId: String? = null,

    // Series & Franchise links
    val gameSeries: List<GameSeriesLink> = emptyList(),
    val gameFranchises: List<GameFranchiseLink> = emptyList(),
) {
    /**
     * Hero label semantics derived from the loaded console + sessions
     * + user save-state preferences. Recomputed on every read; the
     * inputs are tiny so allocation pressure is negligible. See #884.
     *
     * NoSession when no detail / no console is loaded yet, so callers
     * get a stable default-safe value during the initial loading spin.
     */
    val playSemantics: PlaySemantics
        get() {
            val detail = gameDetail ?: return PlaySemantics.NoSession
            val console = console ?: return PlaySemantics.NoSession
            val effective = effectiveSaveStateChoice(
                consoleAbbr = detail.game.consoleId,
                tier = detail.game.consoleSaveStatePolicy,
                overrides = userConsoleSaveStatePolicies,
                gameOverride = gameSaveStatePolicy,
            )
            return resolvePlaySemantics(
                hasSession = sessions.isNotEmpty(),
                consoleSaveStateSupport = console.saveStateSupport,
                effectiveChoice = effective,
            )
        }
}
