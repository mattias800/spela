package com.spela.player.presentation.intent

sealed interface GameDetailIntent {
    data class LoadGame(val gameId: String) : GameDetailIntent
    data object DownloadGame : GameDetailIntent
    data object PlayGame : GameDetailIntent
    data object DeleteLocalGame : GameDetailIntent
    data object ShowDeleteDownloadDialog : GameDetailIntent
    data object DismissDeleteDownloadDialog : GameDetailIntent
    data object ToggleFavorite : GameDetailIntent
    data object TogglePlayLater : GameDetailIntent
    /**
     * Set the per-game save-state opt-out (#804 phase 4b spec point c).
     * `choice == null` clears the override so the game inherits from
     * the per-console policy. The handler does an optimistic update +
     * rollback on API failure, mirroring the per-console toggle in
     * Settings.
     */
    data class SetGameSaveStatePolicy(
        val choice: com.spela.player.domain.model.SaveStateChoice?,
    ) : GameDetailIntent
    data class RateGame(val rating: Int, val review: String = "") : GameDetailIntent
    data object DeleteRating : GameDetailIntent
    data object LoadSharedSaves : GameDetailIntent
    data class ShareSave(val saveId: String, val name: String, val description: String) : GameDetailIntent
    data class DownloadSharedSave(val saveId: String) : GameDetailIntent
    data class DeleteSharedSave(val saveId: String) : GameDetailIntent
    data class PlayFromSharedSave(val saveId: String) : GameDetailIntent
    // Add to Collection
    data object ShowAddToCollectionDialog : GameDetailIntent
    data object DismissAddToCollectionDialog : GameDetailIntent
    data class AddToCollection(val collectionId: String) : GameDetailIntent
    data class CreateCollectionAndAddGame(val name: String) : GameDetailIntent

    // Community Stats, Reviews, Shared Sessions
    data class LoadGameStats(val gameId: String) : GameDetailIntent
    data class LoadReviews(val gameId: String) : GameDetailIntent
    data class LoadMoreReviews(val gameId: String) : GameDetailIntent
    data class LoadGameSharedSessions(val gameId: String) : GameDetailIntent

    // Create Challenge
    data object ShowCreateChallengeDialog : GameDetailIntent
    data object DismissCreateChallengeDialog : GameDetailIntent
    data class CreateChallenge(
        val saveStateId: String,
        val name: String,
        val description: String,
        val type: String,
        val difficulty: String,
    ) : GameDetailIntent

    // Achievements
    data class LoadAchievements(val gameId: String) : GameDetailIntent
    data class LoadAchievementTimeline(val gameId: String) : GameDetailIntent
    data class LoadAchievementLeaderboard(val gameId: String) : GameDetailIntent
    data class ToggleAchievementsView(val mode: com.spela.player.presentation.state.AchievementsViewMode) : GameDetailIntent

    data object DismissError : GameDetailIntent
    data object DismissSuccess : GameDetailIntent

    // Admin actions
    data object AdminScrapeGame : GameDetailIntent
    data object AdminRefreshAchievements : GameDetailIntent

    // Sessions
    data class LoadSessions(val gameId: String) : GameDetailIntent
    data class CreateSession(val gameId: String, val name: String) : GameDetailIntent
    data class RenameSession(val sessionId: String, val name: String) : GameDetailIntent
    data class DeleteSession(val sessionId: String) : GameDetailIntent
    /**
     * Clone an existing session into a new session owned by the caller.
     * [name] defaults to `"{source.name} (Copy)"` server-side when null.
     * [saveId] selects which save to seed the clone from — omit (null)
     * for the most-recent save, or pass a specific save id for US-3.
     */
    data class CloneSession(val sessionId: String, val name: String? = null, val saveId: Long? = null) : GameDetailIntent
}
