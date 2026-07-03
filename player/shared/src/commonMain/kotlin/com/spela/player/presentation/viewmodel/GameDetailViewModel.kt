package com.spela.player.presentation.viewmodel

import com.spela.player.data.cache.GameDetailCache
import com.spela.player.data.remote.ScrapeService
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.repository.BiosRepository
import com.spela.player.domain.usecase.AddGameToCollectionUseCase
import com.spela.player.domain.usecase.CreateCollectionUseCase
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.GetGameStatsUseCase
import com.spela.player.domain.usecase.GetMyCollectionsUseCase
import com.spela.player.domain.usecase.ToggleFavoriteUseCase
import com.spela.player.domain.usecase.TogglePlayLaterUseCase
import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.CheatRepository
import com.spela.player.domain.repository.ExploreRepository
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.RatingRepository
import com.spela.player.domain.repository.SharedSessionRepository
import com.spela.player.domain.repository.SessionRepository
import com.spela.player.domain.repository.SharedSaveRepository
import com.spela.player.domain.repository.GameStatsRepository
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.state.GameDetailState
import com.spela.player.util.DispatcherProvider
import com.spela.player.util.pickDirectory
import com.spela.player.util.revealInFileManager
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.INSTANT_DOWNLOAD_FALLBACK_DELAY_MS
import com.spela.player.domain.model.INSTANT_DOWNLOAD_THRESHOLD_BYTES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameDetailViewModel(
    private val getGameDetailUseCase: GetGameDetailUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val togglePlayLaterUseCase: TogglePlayLaterUseCase,
    private val downloadRepository: DownloadRepository,
    private val ratingRepository: RatingRepository,
    private val sharedSaveRepository: SharedSaveRepository,
    private val getMyCollectionsUseCase: GetMyCollectionsUseCase,
    private val addGameToCollectionUseCase: AddGameToCollectionUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val getGameStatsUseCase: GetGameStatsUseCase,
    private val gameStatsRepository: GameStatsRepository,
    private val challengeRepository: ChallengeRepository,
    private val sharedSessionRepository: SharedSessionRepository,
    private val gameRepository: GameRepository,
    private val preferencesRepository: com.spela.player.domain.repository.PreferencesRepository,
    private val apiClient: SpelaApiClient,
    private val scrapeService: ScrapeService,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val biosRepository: BiosRepository? = null,
    private val cheatRepository: CheatRepository? = null,
    private val sessionRepository: SessionRepository? = null,
    private val exploreRepository: ExploreRepository? = null,
) {
    private val _state = MutableStateFlow(GameDetailState())
    val state: StateFlow<GameDetailState> = _state.asStateFlow()

    private var currentGameId: String? = null

    // Lazy-fetch latch for `/api/games/{id}/similar` — null until the
    // UI signals the Similar Games carousel has approached the
    // viewport via requestSimilarGames(). Reset by loadGame() so a
    // new game starts un-requested.
    private var similarGamesRequestedForGameId: String? = null

    // Per-game collector jobs. Cancelled and re-launched on every
    // loadGame so navigating away and back (or to a different game)
    // doesn't leak collectors. Pre-#956 these were unbounded `scope.
    // launch { collect { ... } }` calls — each loadGame multiplied
    // the active collectors, so after two visits two writers raced
    // on state.downloadProgress and two reloads fired on every
    // scrape-done event.
    private var downloadObserveJob: Job? = null
    private var scrapeObserveJobs: List<Job> = emptyList()
    /**
     * Tracks the in-flight game-detail fetch so a fast back-then-
     * forward navigation cancels the previous load before starting
     * the new one. Without this, a slow response for game A could
     * win over the fresh response for game B (last-write-wins), and
     * the user would see game A's data appear on game B's screen.
     * See the `currentGameId` re-check in the load coroutine for the
     * belt-and-suspenders guard against the same race.
     */
    private var loadGameJob: Job? = null

    // Session + achievement CRUD extracted into their own managers
    // (#691). Each shares this VM's `_state` so updates land
    // directly. EmulationViewModel uses the same delegation pattern
    // for SaveManager / ChallengeManager / NetplayManager.
    private val sessionManager = GameDetailSessionManager(
        sessionRepository = sessionRepository,
        _state = _state,
        dispatchers = dispatchers,
        scope = scope,
    )
    private val achievementManager = GameDetailAchievementManager(
        gameStatsRepository = gameStatsRepository,
        _state = _state,
        dispatchers = dispatchers,
        scope = scope,
    )

    fun onIntent(intent: GameDetailIntent) {
        when (intent) {
            is GameDetailIntent.LoadGame -> loadGame(intent.gameId)
            GameDetailIntent.DownloadGame -> downloadGame()
            GameDetailIntent.DownloadToFolder -> downloadToFolder()
            GameDetailIntent.DownloadGameAndPlay -> downloadGameAndPlay()
            GameDetailIntent.ResumeDownload -> resumeDownload()
            GameDetailIntent.RestartDownload -> restartDownload()
            GameDetailIntent.ConsumeAutoLaunch -> _state.update { it.copy(pendingAutoLaunch = false) }
            GameDetailIntent.PlayGame -> { /* Handled by UI navigation to emulation screen */ }
            GameDetailIntent.DeleteLocalGame -> deleteLocalGame()
            GameDetailIntent.OpenDownloadFolder -> openDownloadFolder()
            GameDetailIntent.ShowDeleteDownloadDialog -> _state.update {
                it.copy(showDeleteDownloadDialog = true)
            }
            GameDetailIntent.DismissDeleteDownloadDialog -> _state.update {
                it.copy(showDeleteDownloadDialog = false)
            }
            GameDetailIntent.ToggleFavorite -> toggleFavorite()
            GameDetailIntent.TogglePlayLater -> togglePlayLater()
            is GameDetailIntent.SetGameSaveStatePolicy ->
                setGameSaveStatePolicy(intent.choice)
            is GameDetailIntent.RateGame -> rateGame(intent.rating, intent.review)
            is GameDetailIntent.SelectWiiControlScheme -> selectWiiControlScheme(intent.scheme)
            is GameDetailIntent.SelectWiiIrSource -> selectWiiIrSource(intent.source)
            GameDetailIntent.DeleteRating -> deleteRating()
            GameDetailIntent.LoadSharedSaves -> loadSharedSaves()
            is GameDetailIntent.ShareSave -> shareSave(intent.sessionId, intent.saveId, intent.name, intent.description)
            is GameDetailIntent.DownloadSharedSave -> downloadSharedSave(intent.saveId)
            is GameDetailIntent.DeleteSharedSave -> deleteSharedSave(intent.saveId)
            is GameDetailIntent.PlayFromSharedSave -> playFromSharedSave(intent.saveId)
            GameDetailIntent.ShowAddToCollectionDialog -> showAddToCollectionDialog()
            GameDetailIntent.DismissAddToCollectionDialog -> _state.update {
                it.copy(showAddToCollectionDialog = false)
            }
            is GameDetailIntent.AddToCollection -> addToCollection(intent.collectionId)
            is GameDetailIntent.CreateCollectionAndAddGame -> createCollectionAndAddGame(intent.name)
            GameDetailIntent.ShowCreateChallengeDialog -> _state.update {
                it.copy(showCreateChallengeDialog = true)
            }
            GameDetailIntent.DismissCreateChallengeDialog -> _state.update {
                it.copy(showCreateChallengeDialog = false)
            }
            is GameDetailIntent.CreateChallenge -> createChallenge(
                intent.saveStateId, intent.name, intent.description, intent.type, intent.difficulty,
            )
            is GameDetailIntent.LoadGameStats -> loadGameStats(intent.gameId)
            is GameDetailIntent.LoadReviews -> loadReviews(intent.gameId)
            is GameDetailIntent.LoadMoreReviews -> loadMoreReviews(intent.gameId)
            is GameDetailIntent.LoadGameSharedSessions -> loadGameSharedSessions(intent.gameId)
            is GameDetailIntent.LoadAchievements -> achievementManager.loadAchievements(intent.gameId)
            is GameDetailIntent.LoadAchievementTimeline -> achievementManager.loadAchievementTimeline(intent.gameId)
            is GameDetailIntent.LoadAchievementLeaderboard -> achievementManager.loadAchievementLeaderboard(intent.gameId)
            is GameDetailIntent.ToggleAchievementsView -> achievementManager.toggleAchievementsView(intent.mode, currentGameId)
            GameDetailIntent.DismissError -> _state.update { it.copy(error = null) }
            GameDetailIntent.DismissSuccess -> _state.update { it.copy(successMessage = null) }

            // Sessions
            is GameDetailIntent.LoadSessions -> sessionManager.loadSessions(intent.gameId)
            is GameDetailIntent.CreateSession -> sessionManager.createSession(intent.gameId, intent.name)
            is GameDetailIntent.RenameSession -> sessionManager.renameSession(intent.sessionId, intent.name)
            is GameDetailIntent.DeleteSession -> sessionManager.deleteSession(intent.sessionId)
            is GameDetailIntent.CloneSession -> sessionManager.cloneSession(intent.sessionId, intent.name, intent.saveId)

            // Share session — #885
            is GameDetailIntent.ShowShareSessionDialog ->
                _state.update { it.copy(shareSessionDialogSourceId = intent.sourceSessionId) }
            GameDetailIntent.DismissShareSessionDialog ->
                _state.update { it.copy(shareSessionDialogSourceId = null, isCreatingSharedSession = false) }
            is GameDetailIntent.CreateSharedSessionFromSession ->
                createSharedSessionFromSession(intent.sourceSessionId, intent.name, intent.description)
            GameDetailIntent.ConsumeShareSessionCreatedNavigation ->
                _state.update { it.copy(shareSessionCreatedId = null) }
            GameDetailIntent.ConsumePlayFromSharedSaveNavigation ->
                _state.update { it.copy(playFromSharedSaveSessionId = null) }

            // Admin actions
            GameDetailIntent.AdminScrapeGame -> adminScrapeGame()
            GameDetailIntent.AdminRefreshAchievements -> adminRefreshAchievements()
        }
    }

    /**
     * Create a shared session seeded from [sourceSessionIdString]'s
     * latest save (#885). Drives state.isCreatingSharedSession during
     * the in-flight POST so the dialog can disable inputs, and on
     * success populates state.shareSessionCreatedId so the screen can
     * navigate to the new shared session's detail with the invite
     * sheet auto-opened.
     */
    private fun createSharedSessionFromSession(
        sourceSessionIdString: String,
        name: String,
        description: String,
    ) {
        val gameId = currentGameId ?: return
        val sourceId = sourceSessionIdString.toLongOrNull() ?: run {
            _state.update { it.copy(error = "Invalid session id") }
            return
        }
        _state.update { it.copy(isCreatingSharedSession = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.createSharedSession(
                name = name,
                gameId = gameId,
                description = description,
                sourceSessionId = sourceId,
            ).fold(
                onSuccess = { detail ->
                    _state.update {
                        it.copy(
                            isCreatingSharedSession = false,
                            shareSessionDialogSourceId = null,
                            shareSessionCreatedId = detail.id,
                            successMessage = "Shared session created",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isCreatingSharedSession = false,
                            error = error.message ?: "Failed to create shared session",
                        )
                    }
                },
            )
        }
    }

    private fun adminScrapeGame() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            runCatching { apiClient.adminScrapeGame(gameId) }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message) }
                }
            // No need to reload — the WebSocket game_scrape_status event will
            // set isScraping=true, and on idle the observer reloads the game.
        }
    }

    private fun adminRefreshAchievements() {
        val gameId = currentGameId ?: return
        _state.update { it.copy(isAdminActionLoading = true) }
        scope.launch(dispatchers.io) {
            runCatching { apiClient.adminRefreshAchievements(gameId) }
                .onSuccess {
                    // Reload achievements
                    achievementManager.loadAchievements(gameId)
                    _state.update { it.copy(isAdminActionLoading = false, successMessage = "Achievements refreshed") }
                }
                .onFailure { error ->
                    _state.update { it.copy(isAdminActionLoading = false, error = error.message) }
                }
        }
    }

    /**
     * Apply [transform] to the current `_state.gameDetail`, write the
     * result to both screen state AND the [GameDetailCache]. Use this
     * for every mutation that touches `gameDetail` (favourite toggle,
     * play-later toggle, scrape-refresh re-fetch, etc.) — the cache
     * then stays consistent with on-screen truth without needing
     * separate invalidation calls. If `_state.gameDetail` is null
     * (no detail loaded yet), the transform is skipped and nothing
     * changes.
     */
    private inline fun mutateGameDetail(transform: (GameDetail) -> GameDetail) {
        _state.update { state ->
            val current = state.gameDetail ?: return@update state
            val updated = transform(current)
            GameDetailCache.put(updated.game.id, updated)
            state.copy(gameDetail = updated)
        }
    }

    private fun loadGame(gameId: String) {
        currentGameId = gameId
        // Per-game Wii controller scheme (#1559) + IR source (#1560),
        // device-local; baked into the fresh state constructions below so
        // they survive the reset.
        val wiiScheme = preferencesRepository.resolveWiiControlScheme(gameId)
        val wiiIrSource = preferencesRepository.resolveWiiIrSource(gameId)

        // Stale-while-revalidate: if we've fetched this game before
        // in this app session, the cache still has the last detail
        // we observed. Paint the screen with it immediately so the
        // user gets instant feedback while the fresh network fetch
        // runs in the background. By the time the fetch returns the
        // user has already been looking at correct (or near-correct)
        // data; the new value just refreshes anything that drifted.
        val cached = GameDetailCache.get(gameId)
        if (cached != null) {
            _state.update {
                GameDetailState(
                    gameDetail = cached,
                    isLoading = false,
                    wiiControlScheme = wiiScheme,
                    wiiIrSource = wiiIrSource,
                )
            }
        } else {
            _state.update {
                GameDetailState(isLoading = true, wiiControlScheme = wiiScheme, wiiIrSource = wiiIrSource)
            }
        }


        // Cancel any observers from a previous loadGame call (a
        // back-and-forth nav, or a switch to a different game) so
        // we don't accumulate concurrent collectors. See #956.
        downloadObserveJob?.cancel()
        scrapeObserveJobs.forEach { it.cancel() }
        scrapeObserveJobs = emptyList()
        // Cancel any in-flight detail fetch for the previous game.
        // Without this, a slow response for game A could land after
        // the user has already navigated to game B and overwrite
        // B's state with A's data — visible as "wrong game briefly
        // appears" during back-then-forward navigation.
        loadGameJob?.cancel()

        loadGameJob = scope.launch(dispatchers.io) {
            getGameDetailUseCase(gameId).fold(
                onSuccess = { detail ->
                    val isCached = downloadRepository.isGameCached(gameId)
                    val isPlayable = detail.game.playable
                    val myRating = detail.game.userRating
                    val summary = ratingRepository.getRatingSummary(gameId).getOrNull()
                    val console = gameRepository.getConsoles().getOrNull()
                        ?.find { it.id == detail.game.consoleId }
                    val sharedSaves = if (isPlayable) {
                        sharedSaveRepository.getSharedSaves(gameId).getOrDefault(emptyList())
                    } else emptyList()
                    // Resolve the per-game save-state opt-out by
                    // looking up the user's preferences. null means
                    // "no per-game choice — inherit from per-console
                    // policy". See #804 phase 4b spec point (c).
                    val prefs = preferencesRepository.getPreferences().getOrNull()
                    val perGameChoice = prefs?.gameSaveStatePolicies?.get(gameId)
                    val perConsolePolicies = prefs?.consoleSaveStatePolicies ?: emptyMap()
                    val autoLoadSaveEnabled = prefs?.autoLoadSaveEnabled ?: true
                    // Write to cache even if this response is for a
                    // game the user has already navigated away from —
                    // we don't want to throw away a finished fetch
                    // we paid for. The next visit to that game will
                    // read it from the cache and render instantly.
                    GameDetailCache.put(gameId, detail)
                    // Belt-and-suspenders: even though we cancel the
                    // previous job above, if a CancellationException
                    // slipped through (e.g. the use case caught it),
                    // this guard drops the late write so the user's
                    // current game's screen isn't clobbered.
                    if (currentGameId != gameId) return@fold
                    _state.update {
                        it.copy(
                            gameDetail = detail,
                            console = console,
                            sharedSaves = sharedSaves,
                            isGameCached = isCached,
                            myRating = myRating,
                            ratingSummary = summary,
                            gameSaveStatePolicy = perGameChoice,
                            // Snapshot per-console policies so the hero
                            // label resolver in GameDetailState.playSemantics
                            // doesn't need access to UserPreferences. See #884.
                            userConsoleSaveStatePolicies = perConsolePolicies,
                            autoLoadSaveEnabled = autoLoadSaveEnabled,
                            isLoading = false,
                        )
                    }
                    // Check admin status asynchronously (non-blocking)
                    scope.launch(dispatchers.io) {
                        val isAdmin = runCatching { apiClient.getCurrentUser() }
                            .map { it.role == "admin" || it.role == "owner" }
                            .getOrDefault(false)
                        if (currentGameId != gameId) return@launch
                        _state.update { it.copy(isAdmin = isAdmin) }
                    }
                    if (detail.game.scrapeAttempts == 0) {
                        scrapeAndRefresh(gameId)
                    }

                    // Load playable-only community data after we know the game is playable
                    if (isPlayable) {
                        loadGameSharedSessions(gameId)
                        achievementManager.loadAchievements(gameId)
                        loadCheats(gameId)
                        loadBiosStatus()
                        sessionManager.loadSessions(gameId)
                    }
                },
                onFailure = { error ->
                    if (currentGameId != gameId) return@fold
                    _state.update { state ->
                        if (state.gameDetail?.game?.id == gameId) {
                            state.copy(isLoading = false)
                        } else {
                            state.copy(error = error.message, isLoading = false)
                        }
                    }
                },
            )
        }

        downloadObserveJob = scope.launch(dispatchers.io) {
            downloadRepository.observeDownload(gameId).collect { progress ->
                // Reconcile the isDownloading latch from the observed state. A
                // download paused/failed from another surface (e.g. the Downloads
                // screen) cancels this VM's downloadGame coroutine, so its
                // .fold(onFailure) — the usual place isDownloading clears — never
                // runs; without this the CTA stays stuck on "Downloading…" and
                // never flips to Resume / Start over. (#1296)
                val settled = progress.state == DownloadState.PAUSED ||
                    progress.state == DownloadState.FAILED ||
                    progress.state == DownloadState.COMPLETED
                _state.update {
                    it.copy(
                        downloadProgress = progress,
                        isDownloading = if (settled) false else it.isDownloading,
                    )
                }
            }
        }

        // Observe WebSocket scrape status for this game
        observeScrapeStatus(gameId)

        // Reset the per-game lazy-request flag so requestSimilarGames
        // re-fires for the new game when its section approaches the
        // viewport. The previous game's "already fetched" state must
        // not leak across.
        similarGamesRequestedForGameId = null

        // Load community data that applies to all games (playable or not).
        // Note: loadSimilarGames is NOT auto-fired here — it's gated on
        // viewport visibility (see requestSimilarGames) because the
        // similar section sits far down the page and the upstream IGDB
        // call is the most expensive part of the detail screen.
        loadGameStats(gameId)
        loadReviews(gameId)
        loadDeveloperGames(gameId)
        loadGameSeriesLinks(gameId)
        loadGameFranchiseLinks(gameId)
    }

    /**
     * Trigger the similar-games fetch for the currently-loaded game.
     *
     * Called by the UI when the Similar Games carousel approaches the
     * viewport (see [Modifier.onApproachingVisible]). Idempotent: only
     * the first call per [currentGameId] actually launches the fetch;
     * subsequent calls (e.g. from rapid scroll wiggles) are no-ops.
     */
    fun requestSimilarGames() {
        val gameId = currentGameId ?: return
        if (similarGamesRequestedForGameId == gameId) return
        similarGamesRequestedForGameId = gameId
        loadSimilarGames(gameId)
    }

    private fun loadBiosStatus() {
        val gameId = currentGameId ?: return
        val repo = biosRepository ?: return
        scope.launch(dispatchers.io) {
            val detail = _state.value.gameDetail ?: return@launch
            val consoleId = detail.game.consoleId
            val consoleStatus = repo.getConsoleStatus(consoleId) ?: return@launch

            if (consoleStatus.biosRequired && consoleStatus.missingFiles.isNotEmpty()) {
                _state.update { it.copy(missingBiosFiles = consoleStatus.missingFiles) }
            }
        }
    }

    private fun observeScrapeStatus(gameId: String) {
        // Track scraping state from WebSocket events
        val scrapingJob = scope.launch(dispatchers.io) {
            scrapeService.scrapingGameIds.collect { scrapingIds ->
                _state.update { it.copy(isScraping = gameId in scrapingIds) }
            }
        }
        // Track queued state — game has a pending ScrapeQueueItem the
        // worker hasn't picked up yet. Drives the "Scrape queued" hint.
        val queuedJob = scope.launch(dispatchers.io) {
            scrapeService.queuedGameIds.collect { queuedIds ->
                _state.update { it.copy(isScrapeQueued = gameId in queuedIds) }
            }
        }

        // Reload game data when scraping finishes
        val doneJob = scope.launch(dispatchers.io) {
            scrapeService.gameScrapeDone.collect { doneGameId ->
                if (doneGameId == gameId) {
                    getGameDetailUseCase(gameId).fold(
                        onSuccess = { detail ->
                            // Always write to cache; only write to
                            // screen state if the user is still on
                            // this game. Same write-through invariant
                            // as loadGame above.
                            GameDetailCache.put(gameId, detail)
                            if (currentGameId == gameId) {
                                _state.update { it.copy(gameDetail = detail) }
                            }
                        },
                        onFailure = { /* ignore reload failure */ },
                    )
                }
            }
        }

        scrapeObserveJobs = listOf(scrapingJob, queuedJob, doneJob)
    }

    private fun scrapeAndRefresh(gameId: String) {
        scope.launch(dispatchers.io) {
            try {
                apiClient.scrapeIfNeeded(gameId)
                // The WebSocket game_scrape_status events drive isScraping state.
                // The observer in observeScrapeStatus() handles reload on completion.
            } catch (e: Exception) {
                println("GameDetailViewModel: scrape request failed for game $gameId: ${e.message}")
            }
        }
    }

    /**
     * Download a game Spela can't emulate to a user-chosen folder (#1257).
     * Prompts for the destination, then streams the file there (no managed
     * cache entry). Progress flows through the same observeDownload path as
     * a normal download. On platforms without a folder picker (Android) the
     * picker returns null and this is a no-op.
     */
    private fun downloadToFolder() {
        val gameId = currentGameId ?: return
        val gameTitle = _state.value.gameDetail?.game?.title ?: ""
        scope.launch(dispatchers.io) {
            val dir = pickDirectory("Choose a folder to download to") ?: return@launch
            _state.update { it.copy(isDownloading = true) }
            downloadRepository.downloadGameToDirectory(gameId, gameTitle, dir).fold(
                onSuccess = { _state.update { it.copy(isDownloading = false) } },
                onFailure = { error -> _state.update { it.copy(error = error.message, isDownloading = false) } },
            )
        }
    }

    private fun downloadGame() {
        val gameId = currentGameId ?: return
        val gameTitle = _state.value.gameDetail?.game?.title ?: ""
        _state.update { it.copy(isDownloading = true) }
        scope.launch(dispatchers.io) {
            downloadRepository.downloadGame(gameId, gameTitle).fold(
                onSuccess = {
                    _state.update { it.copy(isGameCached = true, isDownloading = false) }
                    currentGameId?.let { loadCheats(it) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isDownloading = false) }
                },
            )
        }
    }

    /**
     * Sub-threshold download-then-launch flow (#932). Tap on the
     * Play/Resume/Continue button for a small uncached game lands
     * here. The download is suppressed-progress for the first
     * INSTANT_DOWNLOAD_FALLBACK_DELAY_MS; if it doesn't finish in
     * that window, we drop back to the regular progress UI so the
     * user isn't staring at a silent spinner. On success we raise
     * `pendingAutoLaunch` and the screen invokes its onPlay handler.
     *
     * The size guard duplicates the GameHeroContent check defensively
     * — if a caller dispatches this for an above-threshold game (or
     * one with unknown size), we just behave like a normal download.
     */
    private fun downloadGameAndPlay() {
        val gameId = currentGameId ?: return
        val gameTitle = _state.value.gameDetail?.game?.title ?: ""
        val fileSize = _state.value.gameDetail?.game?.fileSize ?: 0L
        val isInstant = fileSize in 1..<INSTANT_DOWNLOAD_THRESHOLD_BYTES

        _state.update {
            it.copy(isDownloading = true, isInstantDownload = isInstant)
        }

        if (isInstant) {
            scope.launch(dispatchers.default) {
                delay(INSTANT_DOWNLOAD_FALLBACK_DELAY_MS)
                // If the download is still in flight after the silent
                // window, surface the regular progress UI. Idempotent:
                // post-completion this branch updates a no-op.
                if (_state.value.isDownloading) {
                    _state.update { it.copy(isInstantDownload = false) }
                }
            }
        }

        scope.launch(dispatchers.io) {
            downloadRepository.downloadGame(gameId, gameTitle).fold(
                onSuccess = {
                    // Auto-launch is gated on `isInstant` so an
                    // above-threshold game never silently fires the
                    // play handler when it finally completes (e.g. a
                    // 2 GB ROM after a 30-minute download — the user
                    // has likely walked away and would be surprised
                    // to land in the emulator). Above-threshold games
                    // would normally not reach this codepath at all
                    // (the UI routes them to the regular Download
                    // button), but defending here means future
                    // routing changes can't accidentally enable the
                    // surprise-launch.
                    _state.update {
                        it.copy(
                            isGameCached = true,
                            isDownloading = false,
                            isInstantDownload = false,
                            pendingAutoLaunch = isInstant,
                        )
                    }
                    currentGameId?.let { loadCheats(it) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            error = error.message,
                            isDownloading = false,
                            isInstantDownload = false,
                        )
                    }
                },
            )
        }
    }

    /**
     * Resume a paused/resumably-failed download from its on-disk offset (#1296).
     * Clears any error toast (the resume is the recovery) and reuses the same
     * success/failure handling as a fresh download.
     */
    private fun resumeDownload() {
        val gameId = currentGameId ?: return
        _state.update { it.copy(isDownloading = true, error = null) }
        scope.launch(dispatchers.io) {
            downloadRepository.resumeDownload(gameId).fold(
                onSuccess = {
                    _state.update { it.copy(isGameCached = true, isDownloading = false) }
                    currentGameId?.let { loadCheats(it) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isDownloading = false) }
                },
            )
        }
    }

    /**
     * Discard the partial and download the game over from scratch — the
     * recovery for a terminal failure (corrupt/disk-full/changed file). (#1296)
     */
    private fun restartDownload() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            downloadRepository.deleteLocalGame(gameId)
            downloadGame()
        }
    }

    private fun deleteLocalGame() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            downloadRepository.deleteLocalGame(gameId)
            _state.update { it.copy(isGameCached = false, showDeleteDownloadDialog = false) }
        }
    }

    private fun openDownloadFolder() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            val path = downloadRepository.getLocalGamePath(gameId) ?: return@launch
            revealInFileManager(path)
        }
    }

    private fun toggleFavorite() {
        val detail = _state.value.gameDetail ?: return
        val currentlyFavorite = detail.game.isFavorite
        scope.launch(dispatchers.io) {
            toggleFavoriteUseCase(detail.game.id, currentlyFavorite).fold(
                onSuccess = {
                    mutateGameDetail { d ->
                        d.copy(game = d.game.copy(isFavorite = !currentlyFavorite))
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    /**
     * Upserts (or clears) the per-game save-state opt-out for the
     * current game. Optimistic update with rollback on API failure
     * — same pattern as togglePlayLater. The wire format sends an
     * empty string to clear the row server-side. See #804 phase 4b
     * spec point (c).
     */
    private fun setGameSaveStatePolicy(
        choice: com.spela.player.domain.model.SaveStateChoice?,
    ) {
        val gameId = currentGameId ?: return
        val previous = _state.value.gameSaveStatePolicy
        _state.update { it.copy(gameSaveStatePolicy = choice) }
        scope.launch(dispatchers.io) {
            preferencesRepository.updatePreferences(
                gameSaveStatePolicies = mapOf(gameId to (choice?.apiId ?: "")),
            ).onFailure {
                _state.update { it.copy(gameSaveStatePolicy = previous) }
            }
        }
    }

    private fun togglePlayLater() {
        val detail = _state.value.gameDetail ?: return
        val currentlyInPlayLater = detail.game.isInPlayLater
        // Optimistic update: flip immediately to prevent double-clicks
        mutateGameDetail { d ->
            d.copy(game = d.game.copy(isInPlayLater = !currentlyInPlayLater))
        }
        scope.launch(dispatchers.io) {
            togglePlayLaterUseCase(detail.game.id, currentlyInPlayLater).fold(
                onSuccess = { /* Already updated optimistically */ },
                onFailure = { error ->
                    // Revert on failure
                    mutateGameDetail { d ->
                        d.copy(game = d.game.copy(isInPlayLater = currentlyInPlayLater))
                    }
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun rateGame(rating: Int, review: String) {
        val gameId = currentGameId ?: return
        _state.update { it.copy(isRating = true, myRating = rating) }
        scope.launch(dispatchers.io) {
            ratingRepository.rateGame(gameId, rating, review).fold(
                onSuccess = {
                    val summary = ratingRepository.getRatingSummary(gameId).getOrNull()
                    _state.update {
                        it.copy(
                            myRating = rating,
                            ratingSummary = summary,
                            isRating = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isRating = false) }
                },
            )
        }
    }

    private fun deleteRating() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            ratingRepository.deleteRating(gameId).fold(
                onSuccess = {
                    val summary = ratingRepository.getRatingSummary(gameId).getOrNull()
                    _state.update {
                        it.copy(
                            myRating = null,
                            ratingSummary = summary,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun loadSharedSaves() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            sharedSaveRepository.getSharedSaves(gameId).fold(
                onSuccess = { saves ->
                    _state.update { it.copy(sharedSaves = saves) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun shareSave(sessionId: String, saveId: String, name: String, description: String) {
        val gameId = currentGameId ?: return
        _state.update { it.copy(isSharing = true) }
        scope.launch(dispatchers.io) {
            // Resolve the save's actual bytes via the session repo.
            // Pre-#979 this method passed ByteArray(0) unconditionally —
            // every "share save" upload was a zero-byte payload — and
            // the test fake masked the bug by accepting any bytes.
            val repo = sessionRepository
            if (repo == null) {
                _state.update { it.copy(error = "Sessions not available", isSharing = false) }
                return@launch
            }
            val saveBytes = repo.downloadSessionSave(sessionId, saveId).getOrElse { error ->
                _state.update { it.copy(error = "Failed to load save: ${error.message}", isSharing = false) }
                return@launch
            }
            sharedSaveRepository.shareSave(gameId, name, description, saveBytes).fold(
                onSuccess = { shared ->
                    _state.update {
                        it.copy(
                            sharedSaves = listOf(shared) + it.sharedSaves,
                            isSharing = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isSharing = false) }
                },
            )
        }
    }

    private fun downloadSharedSave(saveId: String) {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            sharedSaveRepository.downloadSharedSave(gameId, saveId).fold(
                onSuccess = { data ->
                    _state.update { it.copy(successMessage = "Shared save downloaded") }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun deleteSharedSave(saveId: String) {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            sharedSaveRepository.deleteSharedSave(gameId, saveId).fold(
                onSuccess = {
                    _state.update {
                        it.copy(sharedSaves = it.sharedSaves.filter { s -> s.id != saveId })
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun playFromSharedSave(saveId: String) {
        val gameId = currentGameId ?: return
        val repo = sessionRepository ?: return
        _state.update { it.copy(isPlayingFromSharedSave = true) }
        scope.launch(dispatchers.io) {
            repo.createSessionFromSharedSave(gameId, saveId).fold(
                onSuccess = { session ->
                    _state.update { state ->
                        state.copy(
                            sessions = state.sessions + session,
                            isPlayingFromSharedSave = false,
                            playFromSharedSaveSessionId = session.id,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            error = error.message,
                            isPlayingFromSharedSave = false,
                        )
                    }
                },
            )
        }
    }

    private fun showAddToCollectionDialog() {
        _state.update { it.copy(showAddToCollectionDialog = true, isLoadingCollections = true) }
        scope.launch(dispatchers.io) {
            getMyCollectionsUseCase().fold(
                onSuccess = { collections ->
                    _state.update {
                        it.copy(userCollections = collections, isLoadingCollections = false)
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoadingCollections = false,
                            error = error.message ?: "Failed to load collections",
                        )
                    }
                },
            )
        }
    }

    private fun addToCollection(collectionId: String) {
        val gameId = currentGameId ?: return
        val collectionName = _state.value.userCollections
            .find { it.id == collectionId }?.name ?: "collection"

        _state.update { it.copy(showAddToCollectionDialog = false) }
        scope.launch(dispatchers.io) {
            addGameToCollectionUseCase(collectionId, gameId).fold(
                onSuccess = {
                    _state.update {
                        it.copy(successMessage = "Added to $collectionName")
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Failed to add to collection")
                    }
                },
            )
        }
    }

    private fun createCollectionAndAddGame(name: String) {
        val gameId = currentGameId ?: return
        _state.update { it.copy(isCreatingCollection = true, collectionCreationError = null) }
        scope.launch(dispatchers.io) {
            createCollectionUseCase(name = name).fold(
                onSuccess = { collection ->
                    addGameToCollectionUseCase(collection.id, gameId).fold(
                        onSuccess = {
                            _state.update {
                                it.copy(
                                    showAddToCollectionDialog = false,
                                    isCreatingCollection = false,
                                    collectionCreationError = null,
                                    successMessage = "Created \"${collection.name}\" and added game",
                                )
                            }
                        },
                        onFailure = { error ->
                            _state.update {
                                it.copy(
                                    isCreatingCollection = false,
                                    collectionCreationError = error.message ?: "Failed to add game to collection",
                                )
                            }
                        },
                    )
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isCreatingCollection = false,
                            collectionCreationError = error.message ?: "Failed to create collection",
                        )
                    }
                },
            )
        }
    }

    /** Persist the per-game Wii controller scheme (#1559). */
    private fun selectWiiControlScheme(scheme: com.spela.player.domain.model.WiiControlScheme) {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            preferencesRepository.setWiiControlScheme(gameId, scheme)
            withContext(dispatchers.main) {
                _state.update { it.copy(wiiControlScheme = scheme) }
            }
        }
    }

    /** Persist the per-game Wii IR pointer source (#1560). Applies next launch. */
    private fun selectWiiIrSource(source: com.spela.player.domain.model.WiiIrSource) {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            preferencesRepository.setWiiIrSource(gameId, source)
            withContext(dispatchers.main) {
                _state.update { it.copy(wiiIrSource = source) }
            }
        }
    }

    private fun loadGameStats(gameId: String) {
        _state.update { it.copy(isLoadingStats = true) }
        scope.launch(dispatchers.io) {
            getGameStatsUseCase(gameId).fold(
                onSuccess = { stats ->
                    _state.update { it.copy(gameStats = stats, isLoadingStats = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingStats = false) }
                },
            )
        }
    }

    private fun loadReviews(gameId: String) {
        _state.update { it.copy(isLoadingReviews = true, reviewsPage = 1) }
        scope.launch(dispatchers.io) {
            ratingRepository.getGameRatings(gameId, page = 1, pageSize = 10).fold(
                onSuccess = { ratings ->
                    val total = ratingRepository.getRatingSummary(gameId).getOrNull()?.totalRatings ?: 0
                    _state.update {
                        it.copy(
                            reviews = ratings,
                            reviewsTotal = total,
                            reviewsPage = 1,
                            isLoadingReviews = false,
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingReviews = false) }
                },
            )
        }
    }

    private fun loadMoreReviews(gameId: String) {
        val nextPage = _state.value.reviewsPage + 1
        _state.update { it.copy(isLoadingReviews = true) }
        scope.launch(dispatchers.io) {
            ratingRepository.getGameRatings(gameId, page = nextPage, pageSize = 10).fold(
                onSuccess = { ratings ->
                    _state.update {
                        it.copy(
                            reviews = it.reviews + ratings,
                            reviewsPage = nextPage,
                            isLoadingReviews = false,
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingReviews = false) }
                },
            )
        }
    }

    private fun loadGameSharedSessions(gameId: String) {
        _state.update { it.copy(isLoadingSharedSessions = true) }
        scope.launch(dispatchers.io) {
            sharedSessionRepository.getGameSharedSessions(gameId).fold(
                onSuccess = { sharedSessions ->
                    _state.update { it.copy(gameSharedSessions = sharedSessions, isLoadingSharedSessions = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingSharedSessions = false) }
                },
            )
        }
    }

    // Achievement loading moved to GameDetailAchievementManager (#691).

    private fun loadCheats(gameId: String) {
        val repo = cheatRepository ?: return
        _state.update { it.copy(isLoadingCheats = true) }
        scope.launch(dispatchers.io) {
            repo.getCheatsForGame(gameId).fold(
                onSuccess = { cheats ->
                    _state.update { it.copy(cheats = cheats, isLoadingCheats = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingCheats = false) }
                },
            )
        }
    }

    private fun loadSimilarGames(gameId: String) {
        _state.update { it.copy(isLoadingSimilar = true) }
        scope.launch(dispatchers.io) {
            gameRepository.getSimilarGames(gameId).fold(
                onSuccess = { games ->
                    _state.update { it.copy(similarGames = games, isLoadingSimilar = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingSimilar = false) }
                },
            )
        }
    }

    private fun loadDeveloperGames(gameId: String) {
        _state.update { it.copy(isLoadingDeveloperGames = true) }
        scope.launch(dispatchers.io) {
            gameRepository.getDeveloperGames(gameId).fold(
                onSuccess = { games ->
                    val devName = _state.value.gameDetail?.game?.developer
                    _state.update {
                        it.copy(
                            developerGames = games,
                            developerName = devName,
                            isLoadingDeveloperGames = false,
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingDeveloperGames = false) }
                },
            )
        }
    }

    private fun loadGameSeriesLinks(gameId: String) {
        val repo = exploreRepository ?: return
        scope.launch(dispatchers.io) {
            repo.getGameSeries(gameId).fold(
                onSuccess = { series ->
                    _state.update { it.copy(gameSeries = series) }
                },
                onFailure = { /* silently ignore — series links are optional */ },
            )
        }
    }

    private fun loadGameFranchiseLinks(gameId: String) {
        val repo = exploreRepository ?: return
        scope.launch(dispatchers.io) {
            repo.getGameFranchises(gameId).fold(
                onSuccess = { franchises ->
                    _state.update { it.copy(gameFranchises = franchises) }
                },
                onFailure = { /* silently ignore — franchise links are optional */ },
            )
        }
    }

    private fun createChallenge(
        saveStateId: String,
        name: String,
        description: String,
        type: String,
        difficulty: String,
    ) {
        val gameId = currentGameId ?: return
        val consoleName = _state.value.gameDetail?.game?.consoleName ?: "unknown"
        _state.update { it.copy(isCreatingChallenge = true) }
        scope.launch(dispatchers.io) {
            challengeRepository.createChallenge(
                gameId = gameId,
                name = name,
                description = description,
                type = type,
                difficulty = difficulty,
                coreName = consoleName,
                saveData = ByteArray(0),
                screenshotData = null,
            ).fold(
                onSuccess = { challenge ->
                    _state.update {
                        it.copy(
                            showCreateChallengeDialog = false,
                            isCreatingChallenge = false,
                            successMessage = "Challenge \"${challenge.name}\" created!",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message, isCreatingChallenge = false)
                    }
                },
            )
        }
    }

    // Session CRUD moved to GameDetailSessionManager (#691).
}
