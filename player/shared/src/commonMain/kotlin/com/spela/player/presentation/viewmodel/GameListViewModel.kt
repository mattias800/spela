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
        }
    }

    private fun loadDashboard() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            val consoles = getConsolesUseCase().getOrDefault(emptyList())
            val recent = getRecentGamesUseCase().getOrDefault(emptyList())
            val favorites = getFavoriteGamesUseCase().getOrDefault(emptyList())
            val playLater = getPlayLaterGamesUseCase().getOrDefault(emptyList())
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
    }

    private fun loadConsoles() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
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
        _state.update { it.copy(isLoading = true, selectedConsoleId = consoleId) }
        scope.launch(dispatchers.io) {
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
        val current = _state.value
        _state.update { it.copy(searchQuery = query, isLoading = true) }
        scope.launch(dispatchers.io) {
            searchGamesUseCase(
                query = query,
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
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
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
