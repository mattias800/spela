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
    data class RateGame(val rating: Int, val review: String = "") : GameDetailIntent
    data object DeleteRating : GameDetailIntent
    data object LoadSharedSaves : GameDetailIntent
    data class ShareSave(val saveId: String, val name: String, val description: String) : GameDetailIntent
    data class DownloadSharedSave(val saveId: String) : GameDetailIntent
    data class DeleteSharedSave(val saveId: String) : GameDetailIntent
    data class DeleteSave(val saveId: Long) : GameDetailIntent

    // Feature 2: Rename save states
    data class RenameSave(val saveId: Long, val name: String) : GameDetailIntent

    // Feature 4: Save state notes
    data class UpdateSaveNotes(val saveId: Long, val notes: String) : GameDetailIntent

    // Feature 7: Bulk delete saves
    data object ToggleSelectionMode : GameDetailIntent
    data class ToggleSaveSelection(val saveId: Long) : GameDetailIntent
    data object SelectAllSaves : GameDetailIntent
    data object DeleteSelectedSaves : GameDetailIntent

    // Feature 9: Export/Import
    data class ExportSave(val saveId: Long) : GameDetailIntent
    data class ImportSave(val name: String, val fileData: ByteArray) : GameDetailIntent

    // Add to Collection
    data object ShowAddToCollectionDialog : GameDetailIntent
    data object DismissAddToCollectionDialog : GameDetailIntent
    data class AddToCollection(val collectionId: String) : GameDetailIntent
    data class CreateCollectionAndAddGame(val name: String) : GameDetailIntent

    // Community Stats, Reviews, Relays
    data class LoadGameStats(val gameId: String) : GameDetailIntent
    data class LoadReviews(val gameId: String) : GameDetailIntent
    data class LoadMoreReviews(val gameId: String) : GameDetailIntent
    data class LoadGameRelays(val gameId: String) : GameDetailIntent

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

    data object RefreshSaves : GameDetailIntent
    data object LoadSaveDataCount : GameDetailIntent

    data object DismissError : GameDetailIntent
    data object DismissSuccess : GameDetailIntent
}
