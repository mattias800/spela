package com.spela.player.presentation.intent

sealed interface GameDetailIntent {
    data class LoadGame(val gameId: String) : GameDetailIntent
    data object DownloadGame : GameDetailIntent
    data object DownloadToFolder : GameDetailIntent
    /**
     * Silent download-then-launch path for sub-threshold games. The
     * ViewModel kicks off the download, suppresses progress UI for
     * the first 750 ms, and on success raises [pendingAutoLaunch] so
     * the screen invokes its onPlay handler. See #932.
     */
    data object DownloadGameAndPlay : GameDetailIntent

    /** Resume a paused/failed download from its on-disk offset (#1296). */
    data object ResumeDownload : GameDetailIntent

    /** Discard the partial and download the game over from scratch (#1296). */
    data object RestartDownload : GameDetailIntent
    /**
     * Screen-side acknowledgement that the auto-launch signal was
     * consumed. Clears [GameDetailState.pendingAutoLaunch] so the
     * effect doesn't re-fire on recomposition.
     */
    data object ConsumeAutoLaunch : GameDetailIntent
    data object PlayGame : GameDetailIntent
    data object DeleteLocalGame : GameDetailIntent
    data object OpenDownloadFolder : GameDetailIntent
    data object ShowDeleteDownloadDialog : GameDetailIntent
    data object DismissDeleteDownloadDialog : GameDetailIntent
    data object ToggleFavorite : GameDetailIntent
    data object TogglePlayLater : GameDetailIntent
    data class SetPreferredPlatform(val gameId: String) : GameDetailIntent
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

    /** Persist the per-game Wii controller scheme (#1559). */
    data class SelectWiiControlScheme(
        val scheme: com.spela.player.domain.model.WiiControlScheme,
    ) : GameDetailIntent

    /** Persist the per-game Wii IR pointer source (#1560). */
    data class SelectWiiIrSource(
        val source: com.spela.player.domain.model.WiiIrSource,
    ) : GameDetailIntent
    data object LoadSharedSaves : GameDetailIntent
    /**
     * Upload a session save to the public shared-saves library.
     * Pre-#979 this intent only carried [saveId] and the VM uploaded
     * an empty ByteArray placeholder; [sessionId] is required to
     * resolve the actual bytes via SessionRepository.downloadSessionSave.
     */
    data class ShareSave(
        val sessionId: String,
        val saveId: String,
        val name: String,
        val description: String,
    ) : GameDetailIntent
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

    // Share session — start a new shared session seeded from this
    // local session's most-recent save state. The dialog opens
    // capability-gated on PlaySemantics.ResumesFromSaveState (i.e.
    // there's a save to share). See #885.
    /** Open the "Share session" dialog for the given source session. */
    data class ShowShareSessionDialog(val sourceSessionId: String) : GameDetailIntent
    data object DismissShareSessionDialog : GameDetailIntent
    /**
     * Submit the dialog. The handler creates the shared session via
     * the server's sourceSessionId path, then sets
     * [com.spela.player.presentation.state.GameDetailState.shareSessionCreatedId]
     * so the screen can navigate to the new shared session's detail
     * with the invite sheet auto-opened.
     */
    data class CreateSharedSessionFromSession(
        val sourceSessionId: String,
        val name: String,
        val description: String = "",
    ) : GameDetailIntent
    data object ConsumeShareSessionCreatedNavigation : GameDetailIntent
    /**
     * Marks playFromSharedSaveSessionId as consumed after the screen has
     * dispatched the navigation. Without this, recomposition can re-fire
     * the LaunchedEffect and push duplicate emulation screens onto the
     * back-stack.
     */
    data object ConsumePlayFromSharedSaveNavigation : GameDetailIntent
}
