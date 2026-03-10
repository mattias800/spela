package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.domain.model.FeaturedSeries
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.Keyword
import com.spela.player.domain.model.SeriesDetail
import com.spela.player.domain.model.Theme
import com.spela.player.domain.repository.ExploreRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreState(
    val featuredGames: List<FeaturedGame> = emptyList(),
    val rows: List<ExploreRow> = emptyList(),
    val themes: List<Theme> = emptyList(),
    val keywords: List<Keyword> = emptyList(),
    val featuredSeries: List<FeaturedSeries> = emptyList(),
    val isLoadingFeatured: Boolean = false,
    val isLoadingRows: Boolean = false,
    val isLoadingThemes: Boolean = false,
    val isLoadingKeywords: Boolean = false,
    val isLoadingFeaturedSeries: Boolean = false,
    val error: String? = null,
) {
    val isLoading: Boolean get() = isLoadingFeatured || isLoadingRows || isLoadingThemes || isLoadingKeywords || isLoadingFeaturedSeries
    val isEmpty: Boolean get() = featuredGames.isEmpty() && rows.isEmpty() && themes.isEmpty() && keywords.isEmpty() && featuredSeries.isEmpty() && !isLoading
}

data class ThemeDetailState(
    val themeId: String = "",
    val themeName: String = "",
    val games: List<Game> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class KeywordDetailState(
    val keywordId: String = "",
    val keywordName: String = "",
    val games: List<Game> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class SeriesDetailState(
    val seriesId: String = "",
    val seriesName: String = "",
    val detail: SeriesDetail? = null,
    val consoleFilter: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val filteredGames: List<com.spela.player.domain.model.SeriesGame>
        get() {
            val games = detail?.games ?: return emptyList()
            val filtered = if (consoleFilter != null) {
                games.filter { it.consoleAbbreviation == consoleFilter }
            } else {
                games
            }
            return filtered.sortedWith(compareBy(nullsLast()) { it.releaseDate })
        }
}

class ExploreViewModel(
    private val exploreRepository: ExploreRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ExploreState())
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    private val _themeDetailState = MutableStateFlow(ThemeDetailState())
    val themeDetailState: StateFlow<ThemeDetailState> = _themeDetailState.asStateFlow()

    private val _keywordDetailState = MutableStateFlow(KeywordDetailState())
    val keywordDetailState: StateFlow<KeywordDetailState> = _keywordDetailState.asStateFlow()

    private val _seriesDetailState = MutableStateFlow(SeriesDetailState())
    val seriesDetailState: StateFlow<SeriesDetailState> = _seriesDetailState.asStateFlow()

    private var featuredJob: Job? = null
    private var rowsJob: Job? = null
    private var themesJob: Job? = null
    private var keywordsJob: Job? = null
    private var themeDetailJob: Job? = null
    private var keywordDetailJob: Job? = null
    private var featuredSeriesJob: Job? = null
    private var seriesDetailJob: Job? = null

    fun load() {
        loadFeatured()
        loadRows()
        loadThemes()
        loadKeywords()
        loadFeaturedSeries()
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun loadThemeGames(themeId: String, themeName: String) {
        themeDetailJob?.cancel()
        _themeDetailState.update {
            ThemeDetailState(themeId = themeId, themeName = themeName, isLoading = true)
        }
        themeDetailJob = scope.launch(dispatchers.io) {
            exploreRepository.getThemeGames(themeId, page = 1, pageSize = 50).fold(
                onSuccess = { games ->
                    _themeDetailState.update { it.copy(games = games, isLoading = false) }
                },
                onFailure = { error ->
                    _themeDetailState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

    fun loadKeywordGames(keywordId: String, keywordName: String) {
        keywordDetailJob?.cancel()
        _keywordDetailState.update {
            KeywordDetailState(keywordId = keywordId, keywordName = keywordName, isLoading = true)
        }
        keywordDetailJob = scope.launch(dispatchers.io) {
            exploreRepository.getKeywordGames(keywordId, page = 1, pageSize = 50).fold(
                onSuccess = { games ->
                    _keywordDetailState.update { it.copy(games = games, isLoading = false) }
                },
                onFailure = { error ->
                    _keywordDetailState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

    fun dismissThemeDetailError() {
        _themeDetailState.update { it.copy(error = null) }
    }

    fun dismissKeywordDetailError() {
        _keywordDetailState.update { it.copy(error = null) }
    }

    fun loadSeriesDetail(seriesId: String, seriesName: String) {
        seriesDetailJob?.cancel()
        _seriesDetailState.update {
            SeriesDetailState(seriesId = seriesId, seriesName = seriesName, isLoading = true)
        }
        seriesDetailJob = scope.launch(dispatchers.io) {
            exploreRepository.getSeriesDetail(seriesId).fold(
                onSuccess = { detail ->
                    _seriesDetailState.update { it.copy(detail = detail, isLoading = false) }
                },
                onFailure = { error ->
                    _seriesDetailState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

    fun setSeriesConsoleFilter(abbreviation: String?) {
        _seriesDetailState.update { it.copy(consoleFilter = abbreviation) }
    }

    fun dismissSeriesDetailError() {
        _seriesDetailState.update { it.copy(error = null) }
    }

    private fun loadFeatured() {
        if (featuredJob?.isActive == true) return
        _state.update { it.copy(isLoadingFeatured = true) }
        featuredJob = scope.launch(dispatchers.io) {
            exploreRepository.getFeaturedGames().fold(
                onSuccess = { featured ->
                    _state.update { it.copy(featuredGames = featured, isLoadingFeatured = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingFeatured = false, error = error.message) }
                },
            )
        }
    }

    private fun loadRows() {
        if (rowsJob?.isActive == true) return
        _state.update { it.copy(isLoadingRows = true) }
        rowsJob = scope.launch(dispatchers.io) {
            exploreRepository.getExploreRows().fold(
                onSuccess = { rows ->
                    _state.update { it.copy(rows = rows, isLoadingRows = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingRows = false, error = error.message) }
                },
            )
        }
    }

    private fun loadThemes() {
        if (themesJob?.isActive == true) return
        _state.update { it.copy(isLoadingThemes = true) }
        themesJob = scope.launch(dispatchers.io) {
            exploreRepository.getThemes().fold(
                onSuccess = { themes ->
                    _state.update { it.copy(themes = themes, isLoadingThemes = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingThemes = false, error = error.message) }
                },
            )
        }
    }

    private fun loadKeywords() {
        if (keywordsJob?.isActive == true) return
        _state.update { it.copy(isLoadingKeywords = true) }
        keywordsJob = scope.launch(dispatchers.io) {
            exploreRepository.getKeywords().fold(
                onSuccess = { keywords ->
                    _state.update { it.copy(keywords = keywords, isLoadingKeywords = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingKeywords = false, error = error.message) }
                },
            )
        }
    }

    private fun loadFeaturedSeries() {
        if (featuredSeriesJob?.isActive == true) return
        _state.update { it.copy(isLoadingFeaturedSeries = true) }
        featuredSeriesJob = scope.launch(dispatchers.io) {
            exploreRepository.getFeaturedSeries().fold(
                onSuccess = { series ->
                    _state.update { it.copy(featuredSeries = series, isLoadingFeaturedSeries = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingFeaturedSeries = false, error = error.message) }
                },
            )
        }
    }
}
