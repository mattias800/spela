package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ArtworkItem
import com.spela.player.domain.model.ConsoleHighlight
import com.spela.player.domain.model.ConsoleShowcase
import com.spela.player.domain.model.DeveloperDetail
import com.spela.player.domain.model.DeveloperSpotlight
import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.domain.model.FeaturedSeries
import com.spela.player.domain.model.ForYouRow
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.Keyword
import com.spela.player.domain.model.MoodDefinition
import com.spela.player.domain.model.ScreenshotItem
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
import kotlinx.coroutines.withContext

data class ExploreState(
    val featuredGames: List<FeaturedGame> = emptyList(),
    val rows: List<ExploreRow> = emptyList(),
    val themes: List<Theme> = emptyList(),
    val keywords: List<Keyword> = emptyList(),
    val featuredSeries: List<FeaturedSeries> = emptyList(),
    val moods: List<MoodDefinition> = emptyList(),
    val forYouRows: List<ForYouRow> = emptyList(),
    val developerSpotlight: DeveloperSpotlight? = null,
    val consoleHighlights: List<ConsoleHighlight> = emptyList(),
    val artworkShowcase: List<ArtworkItem> = emptyList(),
    val isLoadingFeatured: Boolean = false,
    val isLoadingRows: Boolean = false,
    val isLoadingThemes: Boolean = false,
    val isLoadingKeywords: Boolean = false,
    val isLoadingFeaturedSeries: Boolean = false,
    val isLoadingMoods: Boolean = false,
    val isLoadingForYou: Boolean = false,
    val isLoadingDeveloperSpotlight: Boolean = false,
    val isLoadingConsoleHighlights: Boolean = false,
    val isLoadingArtwork: Boolean = false,
    val error: String? = null,
) {
    val isLoading: Boolean get() = isLoadingFeatured || isLoadingRows || isLoadingThemes || isLoadingKeywords || isLoadingFeaturedSeries || isLoadingMoods || isLoadingForYou || isLoadingDeveloperSpotlight || isLoadingConsoleHighlights || isLoadingArtwork
    val isEmpty: Boolean get() = featuredGames.isEmpty() && rows.isEmpty() && themes.isEmpty() && keywords.isEmpty() && featuredSeries.isEmpty() && moods.isEmpty() && forYouRows.isEmpty() && developerSpotlight == null && consoleHighlights.isEmpty() && artworkShowcase.isEmpty() && !isLoading
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

data class MoodDetailState(
    val moodId: String = "",
    val moodName: String = "",
    val mood: MoodDefinition? = null,
    val games: List<Game> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class DeveloperDetailState(
    val name: String = "",
    val detail: DeveloperDetail? = null,
    val consoleFilter: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val filteredGames: List<Game>
        get() {
            val games = detail?.games ?: return emptyList()
            return if (consoleFilter != null) {
                games.filter { it.consoleName.equals(consoleFilter, ignoreCase = true) }
            } else {
                games
            }
        }
}

data class ConsoleShowcaseState(
    val consoleId: String = "",
    val showcase: ConsoleShowcase? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class ScreenshotGalleryState(
    val screenshots: List<ScreenshotItem> = emptyList(),
    val isLoading: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
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

private const val GALLERY_PAGE_SIZE = 40

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

    private val _moodDetailState = MutableStateFlow(MoodDetailState())
    val moodDetailState: StateFlow<MoodDetailState> = _moodDetailState.asStateFlow()

    private val _developerDetailState = MutableStateFlow(DeveloperDetailState())
    val developerDetailState: StateFlow<DeveloperDetailState> = _developerDetailState.asStateFlow()

    private val _consoleShowcaseState = MutableStateFlow(ConsoleShowcaseState())
    val consoleShowcaseState: StateFlow<ConsoleShowcaseState> = _consoleShowcaseState.asStateFlow()

    private val _screenshotGalleryState = MutableStateFlow(ScreenshotGalleryState())
    val screenshotGalleryState: StateFlow<ScreenshotGalleryState> = _screenshotGalleryState.asStateFlow()

    private var featuredJob: Job? = null
    private var rowsJob: Job? = null
    private var themesJob: Job? = null
    private var keywordsJob: Job? = null
    private var themeDetailJob: Job? = null
    private var keywordDetailJob: Job? = null
    private var featuredSeriesJob: Job? = null
    private var seriesDetailJob: Job? = null
    private var moodsJob: Job? = null
    private var moodDetailJob: Job? = null
    private var forYouJob: Job? = null
    private var developerSpotlightJob: Job? = null
    private var developerDetailJob: Job? = null
    private var consoleHighlightsJob: Job? = null
    private var consoleShowcaseJob: Job? = null
    private var artworkShowcaseJob: Job? = null
    private var screenshotGalleryJob: Job? = null

    fun load() {
        loadFeatured()
        loadRows()
        loadThemes()
        loadKeywords()
        loadFeaturedSeries()
        loadMoods()
        loadForYou()
        loadDeveloperSpotlight()
        loadConsoleHighlights()
        loadArtworkShowcase()
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

    private fun loadMoods() {
        if (moodsJob?.isActive == true) return
        _state.update { it.copy(isLoadingMoods = true) }
        moodsJob = scope.launch(dispatchers.io) {
            exploreRepository.getMoods().fold(
                onSuccess = { moods ->
                    _state.update { it.copy(moods = moods, isLoadingMoods = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingMoods = false, error = error.message) }
                },
            )
        }
    }

    private fun loadForYou() {
        if (forYouJob?.isActive == true) return
        _state.update { it.copy(isLoadingForYou = true) }
        forYouJob = scope.launch(dispatchers.io) {
            exploreRepository.getForYou().fold(
                onSuccess = { rows ->
                    _state.update { it.copy(forYouRows = rows, isLoadingForYou = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingForYou = false, error = error.message) }
                },
            )
        }
    }

    fun loadMoodGames(moodId: String, moodName: String) {
        moodDetailJob?.cancel()
        val mood = _state.value.moods.find { it.id == moodId }
        _moodDetailState.update {
            MoodDetailState(moodId = moodId, moodName = moodName, mood = mood, isLoading = true)
        }
        moodDetailJob = scope.launch(dispatchers.io) {
            exploreRepository.getMoodGames(moodId).fold(
                onSuccess = { games ->
                    _moodDetailState.update { it.copy(games = games, isLoading = false) }
                },
                onFailure = { error ->
                    _moodDetailState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

    fun loadSurpriseGame(onGameSelected: (String) -> Unit) {
        scope.launch(dispatchers.io) {
            exploreRepository.getSurpriseGame().fold(
                onSuccess = { game ->
                    withContext(dispatchers.main) {
                        onGameSelected(game.id)
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    fun dismissMoodDetailError() {
        _moodDetailState.update { it.copy(error = null) }
    }

    private fun loadDeveloperSpotlight() {
        if (developerSpotlightJob?.isActive == true) return
        _state.update { it.copy(isLoadingDeveloperSpotlight = true) }
        developerSpotlightJob = scope.launch(dispatchers.io) {
            exploreRepository.getDeveloperSpotlight().fold(
                onSuccess = { spotlight ->
                    _state.update { it.copy(developerSpotlight = spotlight, isLoadingDeveloperSpotlight = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingDeveloperSpotlight = false, error = error.message) }
                },
            )
        }
    }

    fun loadDeveloperDetail(name: String) {
        developerDetailJob?.cancel()
        _developerDetailState.update {
            DeveloperDetailState(name = name, isLoading = true)
        }
        developerDetailJob = scope.launch(dispatchers.io) {
            exploreRepository.getDeveloperDetail(name).fold(
                onSuccess = { detail ->
                    _developerDetailState.update { it.copy(detail = detail, isLoading = false) }
                },
                onFailure = { error ->
                    _developerDetailState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

    fun loadPublisherDetail(name: String) {
        developerDetailJob?.cancel()
        _developerDetailState.update {
            DeveloperDetailState(name = name, isLoading = true)
        }
        developerDetailJob = scope.launch(dispatchers.io) {
            exploreRepository.getPublisherDetail(name).fold(
                onSuccess = { detail ->
                    _developerDetailState.update { it.copy(detail = detail, isLoading = false) }
                },
                onFailure = { error ->
                    _developerDetailState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

    fun setDeveloperConsoleFilter(abbreviation: String?) {
        _developerDetailState.update { it.copy(consoleFilter = abbreviation) }
    }

    fun dismissDeveloperDetailError() {
        _developerDetailState.update { it.copy(error = null) }
    }

    private fun loadConsoleHighlights() {
        if (consoleHighlightsJob?.isActive == true) return
        _state.update { it.copy(isLoadingConsoleHighlights = true) }
        consoleHighlightsJob = scope.launch(dispatchers.io) {
            exploreRepository.getConsoleHighlights().fold(
                onSuccess = { highlights ->
                    _state.update { it.copy(consoleHighlights = highlights, isLoadingConsoleHighlights = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingConsoleHighlights = false, error = error.message) }
                },
            )
        }
    }

    fun loadConsoleShowcase(consoleId: String) {
        consoleShowcaseJob?.cancel()
        _consoleShowcaseState.update {
            ConsoleShowcaseState(consoleId = consoleId, isLoading = true)
        }
        consoleShowcaseJob = scope.launch(dispatchers.io) {
            exploreRepository.getConsoleShowcase(consoleId).fold(
                onSuccess = { showcase ->
                    _consoleShowcaseState.update { it.copy(showcase = showcase, isLoading = false) }
                },
                onFailure = { error ->
                    _consoleShowcaseState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

    fun dismissConsoleShowcaseError() {
        _consoleShowcaseState.update { it.copy(error = null) }
    }

    private fun loadArtworkShowcase() {
        if (artworkShowcaseJob?.isActive == true) return
        _state.update { it.copy(isLoadingArtwork = true) }
        artworkShowcaseJob = scope.launch(dispatchers.io) {
            exploreRepository.getArtworkGallery(page = 1).fold(
                onSuccess = { artworks ->
                    _state.update { it.copy(artworkShowcase = artworks, isLoadingArtwork = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingArtwork = false, error = error.message) }
                },
            )
        }
    }

    fun loadScreenshotGallery() {
        screenshotGalleryJob?.cancel()
        _screenshotGalleryState.update {
            ScreenshotGalleryState(isLoading = true)
        }
        screenshotGalleryJob = scope.launch(dispatchers.io) {
            exploreRepository.getScreenshotGallery(page = 1).fold(
                onSuccess = { screenshots ->
                    _screenshotGalleryState.update {
                        it.copy(
                            screenshots = screenshots,
                            isLoading = false,
                            page = 1,
                            hasMore = screenshots.size >= GALLERY_PAGE_SIZE,
                        )
                    }
                },
                onFailure = { error ->
                    _screenshotGalleryState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                },
            )
        }
    }

    fun loadMoreScreenshots() {
        val current = _screenshotGalleryState.value
        if (current.isLoading || !current.hasMore) return
        _screenshotGalleryState.update { it.copy(isLoading = true) }
        val nextPage = current.page + 1
        screenshotGalleryJob = scope.launch(dispatchers.io) {
            exploreRepository.getScreenshotGallery(page = nextPage).fold(
                onSuccess = { screenshots ->
                    _screenshotGalleryState.update {
                        it.copy(
                            screenshots = it.screenshots + screenshots,
                            isLoading = false,
                            page = nextPage,
                            hasMore = screenshots.size >= GALLERY_PAGE_SIZE,
                        )
                    }
                },
                onFailure = { error ->
                    _screenshotGalleryState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                },
            )
        }
    }

    fun dismissScreenshotGalleryError() {
        _screenshotGalleryState.update { it.copy(error = null) }
    }
}
