package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.AchievementGameItem
import com.spela.player.domain.model.ActiveNowItem
import com.spela.player.domain.model.AlmostDoneGame
import com.spela.player.domain.model.AnniversaryItem
import com.spela.player.domain.model.ArtworkItem
import com.spela.player.domain.model.CommunityTopGame
import com.spela.player.domain.model.ConsoleHighlight
import com.spela.player.domain.model.ConsoleShowcase
import com.spela.player.domain.model.CultClassicGame
import com.spela.player.domain.model.DeveloperDetail
import com.spela.player.domain.model.DeveloperSpotlight
import com.spela.player.domain.model.ExploreChallenge
import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.domain.model.FeaturedSeries
import com.spela.player.domain.model.ForYouRow
import com.spela.player.domain.model.FreshChallengeGame
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameFilters
import com.spela.player.domain.model.Keyword
import com.spela.player.domain.model.MoodDefinition
import com.spela.player.domain.model.RecentReviewItem
import com.spela.player.domain.model.CompletionistMap
import com.spela.player.domain.model.ExplorerBadge
import com.spela.player.domain.model.SavedSearch
import com.spela.player.domain.model.ScreenshotItem
import com.spela.player.domain.model.SeriesDetail
import com.spela.player.domain.model.Theme
import com.spela.player.domain.model.TrendingGame
import com.spela.player.domain.model.WizardStep
import com.spela.player.domain.repository.ExploreRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val trendingGames: List<TrendingGame> = emptyList(),
    val communityTopGames: List<CommunityTopGame> = emptyList(),
    val cultClassics: List<CultClassicGame> = emptyList(),
    val recentReviews: List<RecentReviewItem> = emptyList(),
    val activeNowGames: List<ActiveNowItem> = emptyList(),
    val onThisDayDate: String = "",
    val onThisDayGames: List<Game> = emptyList(),
    val anniversaries: List<AnniversaryItem> = emptyList(),
    val easyToCompleteGames: List<AchievementGameItem> = emptyList(),
    val hardestGames: List<AchievementGameItem> = emptyList(),
    val almostDoneGames: List<AlmostDoneGame> = emptyList(),
    val freshChallengeGames: List<FreshChallengeGame> = emptyList(),
    val activeChallenges: List<ExploreChallenge> = emptyList(),
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
    val isLoadingSocial: Boolean = false,
    val isLoadingTemporal: Boolean = false,
    val isLoadingAchievement: Boolean = false,
    val error: String? = null,
) {
    val isLoading: Boolean get() = isLoadingFeatured || isLoadingRows || isLoadingThemes || isLoadingKeywords || isLoadingFeaturedSeries || isLoadingMoods || isLoadingForYou || isLoadingDeveloperSpotlight || isLoadingConsoleHighlights || isLoadingArtwork || isLoadingSocial || isLoadingTemporal || isLoadingAchievement
    val isEmpty: Boolean get() = featuredGames.isEmpty() && rows.isEmpty() && themes.isEmpty() && keywords.isEmpty() && featuredSeries.isEmpty() && moods.isEmpty() && forYouRows.isEmpty() && developerSpotlight == null && consoleHighlights.isEmpty() && artworkShowcase.isEmpty() && trendingGames.isEmpty() && communityTopGames.isEmpty() && cultClassics.isEmpty() && recentReviews.isEmpty() && activeNowGames.isEmpty() && onThisDayGames.isEmpty() && anniversaries.isEmpty() && easyToCompleteGames.isEmpty() && hardestGames.isEmpty() && almostDoneGames.isEmpty() && freshChallengeGames.isEmpty() && activeChallenges.isEmpty() && !isLoading
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

data class GameSearchState(
    val filters: GameFilters = GameFilters(),
    val results: List<Game> = emptyList(),
    val savedSearches: List<SavedSearch> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingSavedSearches: Boolean = false,
    val isSaving: Boolean = false,
    val showFilterPanel: Boolean = false,
    val error: String? = null,
)

data class WizardState(
    val steps: List<WizardStep> = emptyList(),
    val currentStep: Int = 0,
    val selections: Map<String, String> = emptyMap(),
    val resultGames: List<Game> = emptyList(),
    val resultTitle: String = "",
    val isLoadingSteps: Boolean = false,
    val isLoadingResults: Boolean = false,
    val error: String? = null,
) {
    val isComplete: Boolean get() = currentStep >= steps.size && steps.isNotEmpty()
    val currentStepData: WizardStep? get() = steps.getOrNull(currentStep)
}

data class ExplorerBadgesState(
    val badges: List<ExplorerBadge> = emptyList(),
    val isLoading: Boolean = false,
)

data class CompletionistMapState(
    val data: CompletionistMap? = null,
    val isLoading: Boolean = false,
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

    private val _gameSearchState = MutableStateFlow(GameSearchState())
    val gameSearchState: StateFlow<GameSearchState> = _gameSearchState.asStateFlow()

    private val _wizardState = MutableStateFlow(WizardState())
    val wizardState: StateFlow<WizardState> = _wizardState.asStateFlow()

    private val _explorerBadgesState = MutableStateFlow(ExplorerBadgesState())
    val explorerBadgesState: StateFlow<ExplorerBadgesState> = _explorerBadgesState.asStateFlow()

    private val _completionistMapState = MutableStateFlow(CompletionistMapState())
    val completionistMapState: StateFlow<CompletionistMapState> = _completionistMapState.asStateFlow()

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
    private var socialJob: Job? = null
    private var temporalJob: Job? = null
    private var achievementJob: Job? = null
    private var screenshotGalleryJob: Job? = null
    private var searchJob: Job? = null
    private var savedSearchesJob: Job? = null
    private var savingSearchJob: Job? = null

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
        loadSocialData()
        loadTemporalData()
        loadAchievementData()
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

    private fun loadSocialData() {
        if (socialJob?.isActive == true) return
        _state.update { it.copy(isLoadingSocial = true) }
        socialJob = scope.launch(dispatchers.io) {
            // Load all social endpoints concurrently
            val trendingDeferred = async { exploreRepository.getTrending() }
            val communityTopDeferred = async { exploreRepository.getCommunityTop() }
            val cultClassicsDeferred = async { exploreRepository.getCultClassics() }
            val recentlyReviewedDeferred = async { exploreRepository.getRecentlyReviewed() }
            val activeNowDeferred = async { exploreRepository.getActiveNow() }

            awaitAll(trendingDeferred, communityTopDeferred, cultClassicsDeferred, recentlyReviewedDeferred, activeNowDeferred)

            _state.update { current ->
                current.copy(
                    trendingGames = trendingDeferred.await().getOrDefault(emptyList()),
                    communityTopGames = communityTopDeferred.await().getOrDefault(emptyList()),
                    cultClassics = cultClassicsDeferred.await().getOrDefault(emptyList()),
                    recentReviews = recentlyReviewedDeferred.await().getOrDefault(emptyList()),
                    activeNowGames = activeNowDeferred.await().getOrDefault(emptyList()),
                    isLoadingSocial = false,
                )
            }
        }
    }

    private fun loadTemporalData() {
        if (temporalJob?.isActive == true) return
        _state.update { it.copy(isLoadingTemporal = true) }
        temporalJob = scope.launch(dispatchers.io) {
            val onThisDayDeferred = async { exploreRepository.getOnThisDay() }
            val anniversariesDeferred = async { exploreRepository.getYourAnniversaries() }

            awaitAll(onThisDayDeferred, anniversariesDeferred)

            val onThisDayResult = onThisDayDeferred.await()
            _state.update { current ->
                current.copy(
                    onThisDayDate = onThisDayResult.getOrNull()?.first ?: "",
                    onThisDayGames = onThisDayResult.getOrNull()?.second ?: emptyList(),
                    anniversaries = anniversariesDeferred.await().getOrDefault(emptyList()),
                    isLoadingTemporal = false,
                )
            }
        }
    }

    private fun loadAchievementData() {
        if (achievementJob?.isActive == true) return
        _state.update { it.copy(isLoadingAchievement = true) }
        achievementJob = scope.launch(dispatchers.io) {
            val easyDeferred = async { exploreRepository.getEasyToComplete() }
            val hardestDeferred = async { exploreRepository.getHardestGames() }
            val almostDoneDeferred = async { exploreRepository.getAlmostDone() }
            val freshDeferred = async { exploreRepository.getFreshChallenges() }
            val activeDeferred = async { exploreRepository.getActiveChallenges() }

            awaitAll(easyDeferred, hardestDeferred, almostDoneDeferred, freshDeferred, activeDeferred)

            _state.update { current ->
                current.copy(
                    easyToCompleteGames = easyDeferred.await().getOrDefault(emptyList()),
                    hardestGames = hardestDeferred.await().getOrDefault(emptyList()),
                    almostDoneGames = almostDoneDeferred.await().getOrDefault(emptyList()),
                    freshChallengeGames = freshDeferred.await().getOrDefault(emptyList()),
                    activeChallenges = activeDeferred.await().getOrDefault(emptyList()),
                    isLoadingAchievement = false,
                )
            }
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

    // --- Phase 13: Advanced Search & Multi-Faceted Filtering ---

    fun updateFilters(filters: GameFilters) {
        _gameSearchState.update { it.copy(filters = filters) }
    }

    fun toggleFilterPanel() {
        _gameSearchState.update { it.copy(showFilterPanel = !it.showFilterPanel) }
    }

    fun applyFilters() {
        val filters = _gameSearchState.value.filters
        searchJob?.cancel()
        _gameSearchState.update { it.copy(isLoading = true, error = null, showFilterPanel = false) }
        searchJob = scope.launch(dispatchers.io) {
            exploreRepository.getFilteredGames(filters.toQueryMap(), page = 1, pageSize = 50).fold(
                onSuccess = { games ->
                    _gameSearchState.update { it.copy(results = games, isLoading = false) }
                },
                onFailure = { error ->
                    _gameSearchState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

    fun clearFilters() {
        _gameSearchState.update { it.copy(filters = GameFilters(), results = emptyList()) }
    }

    fun loadSavedSearches() {
        savedSearchesJob?.cancel()
        _gameSearchState.update { it.copy(isLoadingSavedSearches = true) }
        savedSearchesJob = scope.launch(dispatchers.io) {
            exploreRepository.getSavedSearches().fold(
                onSuccess = { searches ->
                    _gameSearchState.update { it.copy(savedSearches = searches, isLoadingSavedSearches = false) }
                },
                onFailure = { error ->
                    _gameSearchState.update { it.copy(isLoadingSavedSearches = false, error = error.message) }
                },
            )
        }
    }

    fun saveCurrentSearch(name: String) {
        val filters = _gameSearchState.value.filters
        if (filters.isEmpty) return
        savingSearchJob?.cancel()
        _gameSearchState.update { it.copy(isSaving = true) }
        savingSearchJob = scope.launch(dispatchers.io) {
            exploreRepository.createSavedSearch(name, filters.toQueryMap()).fold(
                onSuccess = { savedSearch ->
                    _gameSearchState.update {
                        it.copy(
                            savedSearches = it.savedSearches + savedSearch,
                            isSaving = false,
                        )
                    }
                },
                onFailure = { error ->
                    _gameSearchState.update { it.copy(isSaving = false, error = error.message) }
                },
            )
        }
    }

    fun deleteSavedSearch(id: String) {
        scope.launch(dispatchers.io) {
            exploreRepository.deleteSavedSearch(id).fold(
                onSuccess = {
                    _gameSearchState.update {
                        it.copy(savedSearches = it.savedSearches.filter { s -> s.id != id })
                    }
                },
                onFailure = { error ->
                    _gameSearchState.update { it.copy(error = error.message) }
                },
            )
        }
    }

    fun applySavedSearch(savedSearch: SavedSearch) {
        val filters = GameFilters.fromQueryMap(savedSearch.filters)
        _gameSearchState.update { it.copy(filters = filters) }
        applyFilters()
    }

    fun dismissSearchError() {
        _gameSearchState.update { it.copy(error = null) }
    }

    // --- Phase 14: Wild Features ---

    fun loadWizardSteps() {
        scope.launch(dispatchers.io) {
            _wizardState.update { it.copy(isLoadingSteps = true) }
            exploreRepository.getWizardSteps().fold(
                onSuccess = { steps ->
                    _wizardState.update {
                        it.copy(steps = steps, isLoadingSteps = false, currentStep = 0, selections = emptyMap(), resultGames = emptyList())
                    }
                },
                onFailure = { error ->
                    _wizardState.update { it.copy(isLoadingSteps = false, error = error.message) }
                },
            )
        }
    }

    fun selectWizardOption(type: String, optionId: String) {
        _wizardState.update {
            val newSelections = it.selections + (type to optionId)
            val newStep = it.currentStep + 1
            it.copy(selections = newSelections, currentStep = newStep)
        }
        // If we just completed the wizard, load results
        if (_wizardState.value.isComplete) {
            loadWizardResults()
        }
    }

    fun wizardGoBack() {
        _wizardState.update {
            if (it.currentStep > 0) it.copy(currentStep = it.currentStep - 1)
            else it
        }
    }

    fun restartWizard() {
        _wizardState.update {
            it.copy(currentStep = 0, selections = emptyMap(), resultGames = emptyList(), resultTitle = "")
        }
    }

    private fun loadWizardResults() {
        val state = _wizardState.value
        val mood = state.selections["mood"] ?: ""
        val era = state.selections["era"] ?: ""
        val vibe = state.selections["vibe"] ?: ""
        scope.launch(dispatchers.io) {
            _wizardState.update { it.copy(isLoadingResults = true) }
            exploreRepository.getWizardResults(mood, era, vibe).fold(
                onSuccess = { results ->
                    _wizardState.update {
                        it.copy(resultGames = results.games, resultTitle = results.title, isLoadingResults = false)
                    }
                },
                onFailure = { error ->
                    _wizardState.update { it.copy(isLoadingResults = false, error = error.message) }
                },
            )
        }
    }

    fun shuffleWizardResults() {
        loadWizardResults()
    }

    fun loadExplorerBadges() {
        scope.launch(dispatchers.io) {
            _explorerBadgesState.update { it.copy(isLoading = true) }
            exploreRepository.getExplorerBadges().fold(
                onSuccess = { badges ->
                    _explorerBadgesState.update { it.copy(badges = badges, isLoading = false) }
                },
                onFailure = {
                    _explorerBadgesState.update { it.copy(isLoading = false) }
                },
            )
        }
    }

    fun loadCompletionistMap() {
        scope.launch(dispatchers.io) {
            _completionistMapState.update { it.copy(isLoading = true) }
            exploreRepository.getCompletionistMap().fold(
                onSuccess = { data ->
                    _completionistMapState.update { it.copy(data = data, isLoading = false) }
                },
                onFailure = {
                    _completionistMapState.update { it.copy(isLoading = false) }
                },
            )
        }
    }
}
