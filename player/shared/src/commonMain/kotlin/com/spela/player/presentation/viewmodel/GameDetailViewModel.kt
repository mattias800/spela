package com.spela.player.presentation.viewmodel

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
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.RatingRepository
import com.spela.player.domain.repository.RelayRepository
import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.domain.repository.SaveRepository
import com.spela.player.domain.repository.SharedSaveRepository
import com.spela.player.domain.repository.GameStatsRepository
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.state.AchievementsViewMode
import com.spela.player.presentation.state.GameDetailState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameDetailViewModel(
    private val getGameDetailUseCase: GetGameDetailUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val togglePlayLaterUseCase: TogglePlayLaterUseCase,
    private val downloadRepository: DownloadRepository,
    private val saveRepository: SaveRepository,
    private val saveDataRepository: SaveDataRepository,
    private val ratingRepository: RatingRepository,
    private val sharedSaveRepository: SharedSaveRepository,
    private val getMyCollectionsUseCase: GetMyCollectionsUseCase,
    private val addGameToCollectionUseCase: AddGameToCollectionUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val getGameStatsUseCase: GetGameStatsUseCase,
    private val gameStatsRepository: GameStatsRepository,
    private val challengeRepository: ChallengeRepository,
    private val relayRepository: RelayRepository,
    private val gameRepository: GameRepository,
    private val apiClient: SpelaApiClient,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val biosRepository: BiosRepository? = null,
) {
    private val _state = MutableStateFlow(GameDetailState())
    val state: StateFlow<GameDetailState> = _state.asStateFlow()

    private var currentGameId: String? = null

    fun onIntent(intent: GameDetailIntent) {
        when (intent) {
            is GameDetailIntent.LoadGame -> loadGame(intent.gameId)
            GameDetailIntent.DownloadGame -> downloadGame()
            GameDetailIntent.PlayGame -> { /* Handled by UI navigation to emulation screen */ }
            GameDetailIntent.DeleteLocalGame -> deleteLocalGame()
            GameDetailIntent.ShowDeleteDownloadDialog -> _state.update {
                it.copy(showDeleteDownloadDialog = true)
            }
            GameDetailIntent.DismissDeleteDownloadDialog -> _state.update {
                it.copy(showDeleteDownloadDialog = false)
            }
            GameDetailIntent.ToggleFavorite -> toggleFavorite()
            GameDetailIntent.TogglePlayLater -> togglePlayLater()
            is GameDetailIntent.RateGame -> rateGame(intent.rating, intent.review)
            GameDetailIntent.DeleteRating -> deleteRating()
            GameDetailIntent.LoadSharedSaves -> loadSharedSaves()
            is GameDetailIntent.ShareSave -> shareSave(intent.saveId, intent.name, intent.description)
            is GameDetailIntent.DownloadSharedSave -> downloadSharedSave(intent.saveId)
            is GameDetailIntent.DeleteSharedSave -> deleteSharedSave(intent.saveId)
            is GameDetailIntent.DeleteSave -> deleteSave(intent.saveId)
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
            is GameDetailIntent.LoadGameRelays -> loadGameRelays(intent.gameId)
            is GameDetailIntent.LoadAchievements -> loadAchievements(intent.gameId)
            is GameDetailIntent.LoadAchievementTimeline -> loadAchievementTimeline(intent.gameId)
            is GameDetailIntent.LoadAchievementLeaderboard -> loadAchievementLeaderboard(intent.gameId)
            is GameDetailIntent.ToggleAchievementsView -> toggleAchievementsView(intent.mode)
            GameDetailIntent.RefreshSaves -> refreshSaves()
            GameDetailIntent.LoadSaveDataCount -> loadSaveDataCount()
            GameDetailIntent.DismissError -> _state.update { it.copy(error = null) }
            GameDetailIntent.DismissSuccess -> _state.update { it.copy(successMessage = null) }

            // Feature 2: Rename
            is GameDetailIntent.RenameSave -> renameSave(intent.saveId, intent.name)
            // Feature 4: Notes
            is GameDetailIntent.UpdateSaveNotes -> updateSaveNotes(intent.saveId, intent.notes)
            // Feature 7: Bulk delete
            GameDetailIntent.ToggleSelectionMode -> toggleSelectionMode()
            is GameDetailIntent.ToggleSaveSelection -> toggleSaveSelection(intent.saveId)
            GameDetailIntent.SelectAllSaves -> selectAllSaves()
            GameDetailIntent.DeleteSelectedSaves -> deleteSelectedSaves()
            // Feature 9: Export/Import
            is GameDetailIntent.ExportSave -> { /* Export handled by UI layer with file dialog */ }
            is GameDetailIntent.ImportSave -> importSave(intent.name, intent.fileData)
        }
    }

    private fun loadGame(gameId: String) {
        currentGameId = gameId
        _state.update { GameDetailState(isLoading = true) }

        scope.launch(dispatchers.io) {
            getGameDetailUseCase(gameId).fold(
                onSuccess = { detail ->
                    val isCached = downloadRepository.isGameCached(gameId)
                    val isPlayable = detail.game.playable
                    val saves = if (isPlayable) {
                        saveRepository.getSaveStates(gameId).getOrDefault(emptyList())
                    } else emptyList()
                    val myRating = detail.game.userRating
                    val summary = ratingRepository.getRatingSummary(gameId).getOrNull()
                    val sharedSaves = if (isPlayable) {
                        sharedSaveRepository.getSharedSaves(gameId).getOrDefault(emptyList())
                    } else emptyList()
                    val unsyncedCount = saves.count { !it.isSynced }
                    _state.update {
                        it.copy(
                            gameDetail = detail,
                            saveStates = saves,
                            sharedSaves = sharedSaves,
                            isGameCached = isCached,
                            myRating = myRating,
                            ratingSummary = summary,
                            isLoading = false,
                            unsyncedSaveCount = unsyncedCount,
                        )
                    }
                    if (detail.game.scrapeAttempts == 0) {
                        scrapeAndRefresh(gameId)
                    }

                    // Load playable-only community data after we know the game is playable
                    if (isPlayable) {
                        loadGameRelays(gameId)
                        loadAchievements(gameId)
                        loadSaveDataCount()
                        loadBiosStatus()
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }

        scope.launch(dispatchers.io) {
            downloadRepository.observeDownload(gameId).collect { progress ->
                _state.update { it.copy(downloadProgress = progress) }
            }
        }

        // Load community data that applies to all games (playable or not)
        loadGameStats(gameId)
        loadReviews(gameId)
        loadSimilarGames(gameId)
        loadDeveloperGames(gameId)
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

    private fun refreshSaves() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            val saves = saveRepository.getSaveStates(gameId).getOrDefault(emptyList())
            val isCached = downloadRepository.isGameCached(gameId)
            val unsyncedCount = saves.count { !it.isSynced }
            _state.update { it.copy(saveStates = saves, isGameCached = isCached, unsyncedSaveCount = unsyncedCount) }
        }
    }

    private fun scrapeAndRefresh(gameId: String) {
        scope.launch(dispatchers.io) {
            try {
                _state.update { it.copy(isScraping = true) }
                apiClient.scrapeIfNeeded(gameId)

                var attempts = 0
                val maxAttempts = 30
                while (attempts < maxAttempts) {
                    delay(1000)
                    getGameDetailUseCase(gameId).fold(
                        onSuccess = { refreshed ->
                            if (refreshed.game.scrapeAttempts > 0) {
                                _state.update { it.copy(gameDetail = refreshed, isScraping = false) }
                                return@launch
                            }
                        },
                        onFailure = { /* continue polling */ },
                    )
                    attempts++
                }

                _state.update { it.copy(isScraping = false) }
            } catch (e: Exception) {
                println("GameDetailViewModel: scrape failed for game $gameId: ${e.message}")
                _state.update { it.copy(isScraping = false) }
            }
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
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isDownloading = false) }
                },
            )
        }
    }

    private fun deleteLocalGame() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            downloadRepository.deleteLocalGame(gameId)
            _state.update { it.copy(isGameCached = false, showDeleteDownloadDialog = false) }
        }
    }

    private fun toggleFavorite() {
        val detail = _state.value.gameDetail ?: return
        val currentlyFavorite = detail.game.isFavorite
        scope.launch(dispatchers.io) {
            toggleFavoriteUseCase(detail.game.id, currentlyFavorite).fold(
                onSuccess = {
                    _state.update {
                        val updatedGame = it.gameDetail?.game?.copy(isFavorite = !currentlyFavorite)
                        it.copy(
                            gameDetail = updatedGame?.let { g -> it.gameDetail?.copy(game = g) }
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun togglePlayLater() {
        val detail = _state.value.gameDetail ?: return
        val currentlyInPlayLater = detail.game.isInPlayLater
        // Optimistic update: flip immediately to prevent double-clicks
        _state.update {
            val updatedGame = it.gameDetail?.game?.copy(isInPlayLater = !currentlyInPlayLater)
            it.copy(gameDetail = updatedGame?.let { g -> it.gameDetail?.copy(game = g) })
        }
        scope.launch(dispatchers.io) {
            togglePlayLaterUseCase(detail.game.id, currentlyInPlayLater).fold(
                onSuccess = { /* Already updated optimistically */ },
                onFailure = { error ->
                    // Revert on failure
                    _state.update {
                        val revertedGame = it.gameDetail?.game?.copy(isInPlayLater = currentlyInPlayLater)
                        it.copy(
                            gameDetail = revertedGame?.let { g -> it.gameDetail?.copy(game = g) },
                            error = error.message,
                        )
                    }
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

    private fun shareSave(saveId: String, name: String, description: String) {
        val gameId = currentGameId ?: return
        _state.update { it.copy(isSharing = true) }
        scope.launch(dispatchers.io) {
            val saveData = saveRepository.downloadSaveState(gameId, saveId).getOrElse {
                _state.update { it.copy(error = "Failed to read save data", isSharing = false) }
                return@launch
            }
            sharedSaveRepository.shareSave(gameId, name, description, saveData).fold(
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
                    saveRepository.uploadSaveState(gameId, "Shared Save", data).fold(
                        onSuccess = { newSave ->
                            _state.update { it.copy(saveStates = it.saveStates + newSave) }
                        },
                        onFailure = { error ->
                            _state.update { it.copy(error = error.message) }
                        },
                    )
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

    private fun deleteSave(saveId: Long) {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            saveRepository.deleteSaveState(gameId, saveId.toString()).fold(
                onSuccess = {
                    _state.update {
                        val updatedSaves = it.saveStates.filter { s -> s.id != saveId }
                        it.copy(
                            saveStates = updatedSaves,
                            unsyncedSaveCount = updatedSaves.count { s -> !s.isSynced },
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
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

    private fun loadGameRelays(gameId: String) {
        _state.update { it.copy(isLoadingRelays = true) }
        scope.launch(dispatchers.io) {
            relayRepository.getGameRelays(gameId).fold(
                onSuccess = { relays ->
                    _state.update { it.copy(gameRelays = relays, isLoadingRelays = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingRelays = false) }
                },
            )
        }
    }

    private fun loadAchievements(gameId: String) {
        _state.update { it.copy(isLoadingAchievements = true) }
        scope.launch(dispatchers.io) {
            val achievements = gameStatsRepository.getGameAchievements(gameId).getOrDefault(emptyList())
            val progress = gameStatsRepository.getAchievementProgress(gameId).getOrDefault(emptyList())
            _state.update {
                it.copy(
                    achievements = achievements,
                    achievementProgress = progress,
                    isLoadingAchievements = false,
                )
            }
        }
    }

    private fun loadAchievementTimeline(gameId: String) {
        if (_state.value.achievementTimeline != null) return
        _state.update { it.copy(isLoadingAchievements = true) }
        scope.launch(dispatchers.io) {
            gameStatsRepository.getAchievementTimeline(gameId).fold(
                onSuccess = { timeline ->
                    _state.update { it.copy(achievementTimeline = timeline, isLoadingAchievements = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingAchievements = false) }
                },
            )
        }
    }

    private fun loadAchievementLeaderboard(gameId: String) {
        if (_state.value.achievementLeaderboard.isNotEmpty()) return
        _state.update { it.copy(isLoadingAchievements = true) }
        scope.launch(dispatchers.io) {
            gameStatsRepository.getAchievementLeaderboard(gameId).fold(
                onSuccess = { leaderboard ->
                    _state.update { it.copy(achievementLeaderboard = leaderboard, isLoadingAchievements = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingAchievements = false) }
                },
            )
        }
    }

    private fun toggleAchievementsView(mode: AchievementsViewMode) {
        _state.update { it.copy(achievementsView = mode) }
        val gameId = currentGameId ?: return
        when (mode) {
            AchievementsViewMode.TIMELINE -> loadAchievementTimeline(gameId)
            AchievementsViewMode.LEADERBOARD -> loadAchievementLeaderboard(gameId)
            AchievementsViewMode.GRID -> { /* Already loaded */ }
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

    private fun loadSaveDataCount() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            saveDataRepository.getSaveDataList(gameId).onSuccess { list ->
                _state.update { it.copy(saveDataCount = list.size) }
            }
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
            val saveData = saveRepository.downloadSaveState(gameId, saveStateId).getOrElse {
                _state.update {
                    it.copy(error = "Failed to download save state", isCreatingChallenge = false)
                }
                return@launch
            }
            challengeRepository.createChallenge(
                gameId = gameId,
                name = name,
                description = description,
                type = type,
                difficulty = difficulty,
                coreName = consoleName,
                saveData = saveData,
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

    // Feature 2: Rename save states
    private fun renameSave(saveId: Long, name: String) {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            saveRepository.renameSaveState(gameId, saveId.toString(), name).fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            saveStates = state.saveStates.map {
                                if (it.id == saveId) it.copy(name = name) else it
                            },
                            successMessage = "Save renamed",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    // Feature 4: Save state notes
    private fun updateSaveNotes(saveId: Long, notes: String) {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            saveRepository.updateSaveNotes(gameId, saveId.toString(), notes).fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            saveStates = state.saveStates.map {
                                if (it.id == saveId) it.copy(notes = notes.ifBlank { null }) else it
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    // Feature 7: Bulk delete
    private fun toggleSelectionMode() {
        _state.update {
            it.copy(
                isSelectionMode = !it.isSelectionMode,
                selectedSaveIds = emptySet(),
            )
        }
    }

    private fun toggleSaveSelection(saveId: Long) {
        _state.update { state ->
            val updated = if (saveId in state.selectedSaveIds) {
                state.selectedSaveIds - saveId
            } else {
                state.selectedSaveIds + saveId
            }
            state.copy(selectedSaveIds = updated)
        }
    }

    private fun selectAllSaves() {
        _state.update { state ->
            state.copy(
                selectedSaveIds = state.saveStates
                    .filter { !it.isAuto }
                    .map { it.id }
                    .toSet()
            )
        }
    }

    private fun deleteSelectedSaves() {
        val gameId = currentGameId ?: return
        val selectedIds = _state.value.selectedSaveIds.toList()
        if (selectedIds.isEmpty()) return

        scope.launch(dispatchers.io) {
            saveRepository.bulkDeleteSaves(gameId, selectedIds).fold(
                onSuccess = { count ->
                    _state.update { state ->
                        val updatedSaves = state.saveStates.filter { it.id !in selectedIds.toSet() }
                        state.copy(
                            saveStates = updatedSaves,
                            unsyncedSaveCount = updatedSaves.count { !it.isSynced },
                            isSelectionMode = false,
                            selectedSaveIds = emptySet(),
                            successMessage = "$count saves deleted",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    // Feature 9: Import save
    private fun importSave(name: String, fileData: ByteArray) {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            saveRepository.importSaveState(gameId, name, fileData).fold(
                onSuccess = { save ->
                    _state.update { state ->
                        state.copy(
                            saveStates = state.saveStates + save,
                            successMessage = "Save imported",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }
}
