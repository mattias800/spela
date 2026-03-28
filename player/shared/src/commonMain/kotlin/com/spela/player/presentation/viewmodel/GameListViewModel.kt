package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ScrapeService
import com.spela.player.data.repository.BiosRepository
import com.spela.player.domain.model.Game
import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.usecase.*
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.state.GameListState
import com.spela.player.presentation.state.ViewMode
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameListViewModel(
    private val getConsolesUseCase: GetConsolesUseCase,
    private val getGamesForConsoleUseCase: GetGamesForConsoleUseCase,
    private val searchGamesUseCase: SearchGamesUseCase,
    private val getRecentGamesUseCase: GetRecentGamesUseCase,
    private val getFavoriteGamesUseCase: GetFavoriteGamesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val getPlayLaterGamesUseCase: GetPlayLaterGamesUseCase,
    private val togglePlayLaterUseCase: TogglePlayLaterUseCase,
    private val getUserStatsUseCase: GetUserStatsUseCase,
    private val getRecentAchievementsUseCase: GetRecentAchievementsUseCase,
    private val challengeRepository: ChallengeRepository,
    private val scrapeService: ScrapeService,
    private val gameRepository: GameRepository? = null,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val biosRepository: BiosRepository? = null,
) {
    private val _state = MutableStateFlow(GameListState())
    val state: StateFlow<GameListState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var dashboardJob: Job? = null
    private var consolesJob: Job? = null
    private var consoleGamesJob: Job? = null

    init {
        // Observe scrape completions and update games in state
        scope.launch(dispatchers.io) {
            scrapeService.scrapedGames.collect { scrapedGame ->
                _state.update { state ->
                    state.copy(
                        games = updateGameInList(state.games, scrapedGame),
                        recentGames = updateGameInList(state.recentGames, scrapedGame),
                        favoriteGames = updateGameInList(state.favoriteGames, scrapedGame),
                        playLaterGames = updateGameInList(state.playLaterGames, scrapedGame),
                    )
                }
            }
        }
    }

    /**
     * Enqueue a game for scraping if it has no cover art and hasn't been scraped yet.
     */
    fun requestScrapeIfNeeded(game: Game) {
        if (game.coverUrl == null && game.scrapeAttempts == 0) {
            scrapeService.enqueueScrape(game.id)
        }
    }

    private fun updateGameInList(games: List<Game>, scraped: Game): List<Game> {
        var changed = false
        val result = games.map { existing ->
            if (existing.id == scraped.id) {
                changed = true
                existing.copy(
                    coverUrl = scraped.coverUrl,
                    description = scraped.description,
                    developer = scraped.developer,
                    publisher = scraped.publisher,
                    releaseDate = scraped.releaseDate,
                    genre = scraped.genre,
                    players = scraped.players,
                    rating = scraped.rating,
                    scrapeAttempts = scraped.scrapeAttempts,
                )
            } else {
                existing
            }
        }
        return if (changed) result else games
    }

    fun onIntent(intent: GameListIntent) {
        when (intent) {
            GameListIntent.LoadDashboard -> loadDashboard()
            GameListIntent.LoadDashboardWidgets -> loadDashboardWidgets()
            GameListIntent.LoadConsoles -> loadConsoles()
            is GameListIntent.SelectConsole -> loadGamesForConsole(intent.consoleId)
            is GameListIntent.Search -> searchGames(intent.query)
            is GameListIntent.ToggleFavorite -> toggleFavorite(intent.gameId, intent.isFavorite)
            is GameListIntent.TogglePlayLater -> togglePlayLater(intent.gameId, intent.isInPlayLater)
            GameListIntent.DismissError -> _state.update { it.copy(error = null) }
            is GameListIntent.FilterByConsole -> filterByConsole(intent.consoleId)
            is GameListIntent.SetSortBy -> setSortBy(intent.sortBy)
            is GameListIntent.SetSortOrder -> setSortOrder(intent.order)
            GameListIntent.ToggleViewMode -> toggleViewMode()
            GameListIntent.LoadMoreGames -> loadMoreGames()
            GameListIntent.ToggleHideBetas -> toggleHideBetas()
        }
    }

    private fun loadDashboard() {
        val currentState = _state.value
        println("[GameListVM] loadDashboard() called — consoles=${currentState.consoles.size}, recent=${currentState.recentGames.size}, isLoading=${currentState.isLoading}, jobActive=${dashboardJob?.isActive}")
        // Skip if a load is already in-flight
        if (dashboardJob?.isActive == true) return
        // Only show loading spinner if we have no cached data.
        // If we already have data, refresh silently in the background.
        val showLoading = currentState.consoles.isEmpty() && currentState.recentGames.isEmpty()
        println("[GameListVM] loadDashboard() showLoading=$showLoading")
        _state.update { it.copy(isLoading = showLoading) }
        dashboardJob = scope.launch(dispatchers.io) {
            val consoles = getConsolesUseCase().getOrDefault(emptyList())
            val recent = getRecentGamesUseCase().getOrDefault(emptyList())
            val favorites = getFavoriteGamesUseCase().getOrDefault(emptyList())
            val playLater = getPlayLaterGamesUseCase().getOrDefault(emptyList())
            println("[GameListVM] loadDashboard() result: consoles=${consoles.size}, recent=${recent.size}, favorites=${favorites.size}, playLater=${playLater.size}")
            _state.update {
                it.copy(
                    consoles = consoles,
                    recentGames = recent,
                    favoriteGames = favorites,
                    playLaterGames = playLater,
                    isLoading = false,
                )
            }
        }
        loadDashboardWidgets()
        // Load BIOS status for dashboard console list (AC 4.5)
        biosRepository?.let { repo ->
            scope.launch(dispatchers.io) {
                try {
                    val missingBios = repo.getConsolesWithMissingBios()
                    _state.update { it.copy(consolesWithMissingBios = missingBios.keys) }
                } catch (_: Exception) {
                    // Best effort
                }
            }
        }
    }

    private fun loadDashboardWidgets() {
        // Personal stats
        scope.launch(dispatchers.io) {
            _state.update { it.copy(isLoadingPersonalStats = true) }
            getUserStatsUseCase().fold(
                onSuccess = { stats ->
                    _state.update { it.copy(personalStats = stats, isLoadingPersonalStats = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingPersonalStats = false) }
                },
            )
        }
        // Recent achievements
        scope.launch(dispatchers.io) {
            _state.update { it.copy(isLoadingAchievements = true) }
            getRecentAchievementsUseCase().fold(
                onSuccess = { achievements ->
                    _state.update { it.copy(recentAchievements = achievements, isLoadingAchievements = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingAchievements = false) }
                },
            )
        }
        // Trending challenges
        scope.launch(dispatchers.io) {
            _state.update { it.copy(isLoadingTrendingChallenges = true) }
            challengeRepository.getChallenges(sort = "most_attempted", page = 1).fold(
                onSuccess = { challenges ->
                    _state.update { it.copy(trendingChallenges = challenges.take(4), isLoadingTrendingChallenges = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingTrendingChallenges = false) }
                },
            )
        }
        // Global top-rated games for dashboard discovery section
        gameRepository?.let { repo ->
            scope.launch(dispatchers.io) {
                _state.update { it.copy(isLoadingTopRated = true) }
                repo.getTopRatedGamesGlobal().fold(
                    onSuccess = { games ->
                        _state.update { it.copy(topRatedGames = games, isLoadingTopRated = false) }
                    },
                    onFailure = {
                        _state.update { it.copy(isLoadingTopRated = false) }
                    },
                )
            }
        }
        // Recently added games
        gameRepository?.let { repo ->
            scope.launch(dispatchers.io) {
                repo.getRecentlyAddedGames().fold(
                    onSuccess = { games ->
                        _state.update { it.copy(recentlyAddedGames = games) }
                    },
                    onFailure = { /* best effort */ },
                )
            }
        }
    }

    private fun loadConsoles() {
        consolesJob?.cancel()
        _state.update { it.copy(isLoading = it.consoles.isEmpty()) }
        consolesJob = scope.launch(dispatchers.io) {
            getConsolesUseCase().fold(
                onSuccess = { consoles ->
                    _state.update { it.copy(consoles = consoles, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
        // Load BIOS status in parallel (AC 4.5)
        biosRepository?.let { repo ->
            scope.launch(dispatchers.io) {
                try {
                    val missingBios = repo.getConsolesWithMissingBios()
                    _state.update { it.copy(consolesWithMissingBios = missingBios.keys) }
                } catch (_: Exception) {
                    // Best effort — don't block console list on BIOS check
                }
            }
        }
    }

    private fun loadGamesForConsole(consoleId: String) {
        // Skip if already loading games for this console
        if (consoleGamesJob?.isActive == true && _state.value.selectedConsoleId == consoleId) return
        consoleGamesJob?.cancel()

        val isSameConsole = _state.value.selectedConsoleId == consoleId
        _state.update {
            it.copy(
                selectedConsoleId = consoleId,
                // Only clear data and show loading when switching to a different console.
                // When revisiting the same console, keep showing cached data.
                isLoading = if (isSameConsole) it.games.isEmpty() else true,
                games = if (isSameConsole) it.games else emptyList(),
                topRatedGames = if (isSameConsole) it.topRatedGames else emptyList(),
            )
        }
        consoleGamesJob = scope.launch(dispatchers.io) {
            getGamesForConsoleUseCase(consoleId).fold(
                onSuccess = { games ->
                    _state.update { it.copy(games = games, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
        // Load top-rated games in parallel
        gameRepository?.let { repo ->
            scope.launch(dispatchers.io) {
                _state.update { it.copy(isLoadingTopRated = true) }
                repo.getTopRatedGames(consoleId).fold(
                    onSuccess = { games ->
                        _state.update { it.copy(topRatedGames = games, isLoadingTopRated = false) }
                    },
                    onFailure = {
                        _state.update { it.copy(isLoadingTopRated = false) }
                    },
                )
            }
        }
    }

    private fun searchGames(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = scope.launch(dispatchers.io) {
            delay(300) // debounce
            _state.update { it.copy(isLoading = true) }
            val current = _state.value
            searchGamesUseCase(
                query = current.searchQuery,
                consoleId = current.selectedConsoleFilter,
                sortBy = current.sortBy,
                sortOrder = current.sortOrder,
            ).fold(
                onSuccess = { games ->
                    _state.update { it.copy(games = games, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun filterByConsole(consoleId: String?) {
        _state.update { it.copy(selectedConsoleFilter = consoleId) }
        reloadGames()
    }

    private fun setSortBy(sortBy: String) {
        _state.update { it.copy(sortBy = sortBy) }
        reloadGames()
    }

    private fun setSortOrder(order: String) {
        _state.update { it.copy(sortOrder = order) }
        reloadGames()
    }

    private fun toggleViewMode() {
        _state.update {
            it.copy(
                viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID,
            )
        }
    }

    private fun reloadGames() {
        val current = _state.value
        _state.update { it.copy(isLoading = true, currentPage = 1) }
        scope.launch(dispatchers.io) {
            val repo = gameRepository
            if (repo != null) {
                repo.searchGamesPaginated(
                    query = current.searchQuery,
                    consoleId = current.selectedConsoleFilter,
                    sortBy = current.sortBy,
                    sortOrder = current.sortOrder,
                    page = 1,
                    pageSize = current.pageSize,
                    hidePreRelease = current.hideBetas,
                ).fold(
                    onSuccess = { result ->
                        _state.update {
                            it.copy(
                                games = result.data,
                                totalGames = result.total,
                                currentPage = result.page,
                                hasMorePages = result.data.size.toLong() < result.total,
                                isLoading = false,
                            )
                        }
                    },
                    onFailure = { error ->
                        _state.update { it.copy(error = error.message, isLoading = false) }
                    },
                )
            } else {
                searchGamesUseCase(
                    query = current.searchQuery,
                    consoleId = current.selectedConsoleFilter,
                    sortBy = current.sortBy,
                    sortOrder = current.sortOrder,
                ).fold(
                    onSuccess = { games ->
                        _state.update {
                            it.copy(
                                games = games,
                                totalGames = games.size.toLong(),
                                hasMorePages = false,
                                isLoading = false,
                            )
                        }
                    },
                    onFailure = { error ->
                        _state.update { it.copy(error = error.message, isLoading = false) }
                    },
                )
            }
        }
    }

    private fun loadMoreGames() {
        val current = _state.value
        if (current.isLoadingMore || !current.hasMorePages) return
        val repo = gameRepository ?: return

        _state.update { it.copy(isLoadingMore = true) }
        scope.launch(dispatchers.io) {
            repo.searchGamesPaginated(
                query = current.searchQuery,
                consoleId = current.selectedConsoleFilter,
                sortBy = current.sortBy,
                sortOrder = current.sortOrder,
                page = current.currentPage + 1,
                pageSize = current.pageSize,
                hidePreRelease = current.hideBetas,
            ).fold(
                onSuccess = { result ->
                    _state.update {
                        val allGames = it.games + result.data
                        it.copy(
                            games = allGames,
                            totalGames = result.total,
                            currentPage = result.page,
                            hasMorePages = allGames.size.toLong() < result.total,
                            isLoadingMore = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoadingMore = false) }
                },
            )
        }
    }

    private fun toggleHideBetas() {
        _state.update { it.copy(hideBetas = !it.hideBetas) }
        reloadGames()
    }

    private fun toggleFavorite(gameId: String, isFavorite: Boolean) {
        scope.launch(dispatchers.io) {
            toggleFavoriteUseCase(gameId, isFavorite).fold(
                onSuccess = {
                    val updatedFavorites = getFavoriteGamesUseCase().getOrDefault(emptyList())
                    _state.update { it.copy(favoriteGames = updatedFavorites) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun togglePlayLater(gameId: String, isInPlayLater: Boolean) {
        scope.launch(dispatchers.io) {
            togglePlayLaterUseCase(gameId, isInPlayLater).fold(
                onSuccess = {
                    val updatedPlayLater = getPlayLaterGamesUseCase().getOrDefault(emptyList())
                    _state.update { it.copy(playLaterGames = updatedPlayLater) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }
}
