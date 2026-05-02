package com.spela.player.data.remote.api

import com.spela.client.apis.AdminApi
import com.spela.client.apis.AuthApi
import com.spela.client.apis.BiosApi
import com.spela.client.apis.ChallengesApi
import com.spela.client.apis.CollectionsApi
import com.spela.client.apis.ConsolesApi
import com.spela.client.apis.CoresApi
import com.spela.client.apis.DevicesApi
import com.spela.client.apis.EnrichmentApi
import com.spela.client.apis.ExploreApi
import com.spela.client.apis.FavoritesApi
import com.spela.client.apis.GamesApi
import com.spela.client.apis.NetplayApi
import com.spela.client.apis.PlayLaterApi
import com.spela.client.apis.ProfilesApi
import com.spela.client.apis.RatingsApi
import com.spela.client.apis.RetroachievementsApi
import com.spela.client.apis.SearchApi
import com.spela.client.apis.SessionsApi
import com.spela.client.apis.SharedSavesApi
import com.spela.client.apis.SharedSessionsApi
import com.spela.client.apis.SocialApi
import com.spela.client.apis.StatsApi
import com.spela.client.apis.SystemApi
import com.spela.client.apis.TopListsApi
import com.spela.client.apis.UserApi
import com.spela.player.data.remote.AuthFailureReason
import com.spela.player.data.remote.dto.*
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.util.FileStorage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json

class SpelaApiClient(
    private val engineFactory: io.ktor.client.engine.HttpClientEngineFactory<*>,
    private val tokenManager: TokenManager,
    val onAuthFailure: ((AuthFailureReason) -> Unit)? = null,
    private val onTokenRefreshed: (suspend (String, String) -> Unit)? = null,
) {
    private var baseUrl: String = ""

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient(engineFactory) {
        // Throw on non-2xx responses so HTTP errors are never silently swallowed.
        expectSuccess = true

        install(ContentNegotiation) {
            json(this@SpelaApiClient.json)
        }

        install(Logging) {
            level = LogLevel.HEADERS
        }

        install(Auth) {
            bearer {
                loadTokens {
                    tokenManager.toBearerTokens()
                }

                refreshTokens {
                    val refreshToken = tokenManager.refreshToken ?: run {
                        tokenManager.clearTokens()
                        onAuthFailure?.invoke(AuthFailureReason.SESSION_EXPIRED)
                        return@refreshTokens null
                    }

                    try {
                        val response: com.spela.client.models.AuthLoginResponse =
                            client.post("$baseUrl/api/auth/refresh") {
                                markAsRefreshTokenRequest()
                                contentType(ContentType.Application.Json)
                                setBody(com.spela.client.models.AuthRefreshRequest(refreshToken = refreshToken))
                            }.body()

                        tokenManager.setTokens(response.accessToken, response.refreshToken)
                        onTokenRefreshed?.invoke(response.accessToken, response.refreshToken)
                        BearerTokens(response.accessToken, response.refreshToken)
                    } catch (_: Exception) {
                        tokenManager.clearTokens()
                        onAuthFailure?.invoke(AuthFailureReason.REFRESH_FAILED)
                        null
                    }
                }

                sendWithoutRequest { true }
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }

        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    // Generated API delegates. Recreated whenever the base URL changes so that
    // every business method routes through the same shared HttpClient (and
    // therefore the same auth / refresh / logging plugins) while letting the
    // generated *Api classes own the URL paths.
    private lateinit var adminApi: AdminApi
    private lateinit var authApi: AuthApi
    private lateinit var biosApi: BiosApi
    private lateinit var challengesApi: ChallengesApi
    private lateinit var collectionsApi: CollectionsApi
    private lateinit var consolesApi: ConsolesApi
    private lateinit var coresApi: CoresApi
    private lateinit var devicesApi: DevicesApi
    private lateinit var enrichmentApi: EnrichmentApi
    private lateinit var exploreApi: ExploreApi
    private lateinit var favoritesApi: FavoritesApi
    private lateinit var gamesApi: GamesApi
    private lateinit var netplayApi: NetplayApi
    private lateinit var playLaterApi: PlayLaterApi
    private lateinit var profilesApi: ProfilesApi
    private lateinit var ratingsApi: RatingsApi
    private lateinit var retroachievementsApi: RetroachievementsApi
    private lateinit var searchApi: SearchApi
    private lateinit var sessionsApi: SessionsApi
    private lateinit var sharedSavesApi: SharedSavesApi
    private lateinit var sharedSessionsApi: SharedSessionsApi
    private lateinit var socialApi: SocialApi
    private lateinit var statsApi: StatsApi
    private lateinit var systemApi: SystemApi
    private lateinit var topListsApi: TopListsApi
    private lateinit var userApi: UserApi

    init {
        rebuildApis()
    }

    private fun rebuildApis() {
        adminApi = AdminApi(baseUrl, client)
        authApi = AuthApi(baseUrl, client)
        biosApi = BiosApi(baseUrl, client)
        challengesApi = ChallengesApi(baseUrl, client)
        collectionsApi = CollectionsApi(baseUrl, client)
        consolesApi = ConsolesApi(baseUrl, client)
        coresApi = CoresApi(baseUrl, client)
        devicesApi = DevicesApi(baseUrl, client)
        enrichmentApi = EnrichmentApi(baseUrl, client)
        exploreApi = ExploreApi(baseUrl, client)
        favoritesApi = FavoritesApi(baseUrl, client)
        gamesApi = GamesApi(baseUrl, client)
        netplayApi = NetplayApi(baseUrl, client)
        playLaterApi = PlayLaterApi(baseUrl, client)
        profilesApi = ProfilesApi(baseUrl, client)
        ratingsApi = RatingsApi(baseUrl, client)
        retroachievementsApi = RetroachievementsApi(baseUrl, client)
        searchApi = SearchApi(baseUrl, client)
        sessionsApi = SessionsApi(baseUrl, client)
        sharedSavesApi = SharedSavesApi(baseUrl, client)
        sharedSessionsApi = SharedSessionsApi(baseUrl, client)
        socialApi = SocialApi(baseUrl, client)
        statsApi = StatsApi(baseUrl, client)
        systemApi = SystemApi(baseUrl, client)
        topListsApi = TopListsApi(baseUrl, client)
        userApi = UserApi(baseUrl, client)
    }

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
        rebuildApis()
    }

    /**
     * Resolves a potentially relative URL against the server base URL.
     * Absolute URLs (http/https) are returned as-is.
     * Relative URLs (e.g. /api/images/...) are prepended with the base URL.
     */
    fun resolveUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return "$baseUrl$path"
    }

    /**
     * Resolves a URL and appends the auth token as a query parameter.
     * Save screenshots are served via the auth-protected image handler,
     * which accepts a `?token=` query param for image requests.
     */
    fun resolveAuthenticatedUrl(path: String?): String? {
        val resolved = resolveUrl(path) ?: return null
        val token = tokenManager.accessToken ?: return resolved
        val separator = if ('?' in resolved) '&' else '?'
        return "${resolved}${separator}token=$token"
    }

    // Health

    suspend fun healthCheck(): Boolean {
        return try {
            systemApi.getHealth()
            true
        } catch (_: Exception) {
            false
        }
    }

    // Auth

    suspend fun login(request: com.spela.client.models.AuthLoginRequest): com.spela.client.models.AuthLoginResponse {
        return authApi.authLogin(request).body()
    }

    suspend fun register(request: com.spela.client.models.AuthRegisterRequest): com.spela.client.models.AuthRegisterResponse {
        return authApi.authRegister(request).body()
    }

    suspend fun refreshToken(request: com.spela.client.models.AuthRefreshRequest): com.spela.client.models.AuthLoginResponse {
        return authApi.authRefresh(request).body()
    }

    suspend fun getCurrentUser(): com.spela.client.models.UserResponse {
        return userApi.getUserProfile().body()
    }

    // Consoles & Games

    suspend fun getConsoles(): List<com.spela.client.models.ConsoleResponse> {
        return consolesApi.listConsoles().body()
    }

    /** Returns paginated games for a console via the /api/games endpoint */
    suspend fun getGamesForConsole(
        consoleId: String,
        page: Int? = null,
        pageSize: Int? = null,
        hidePreRelease: Boolean = true,
        grouped: Boolean = true,
        region: String? = null,
    ): com.spela.client.models.PaginatedResponseGameResponse {
        return gamesApi.listGames(
            consoleId = consoleId,
            page = page?.toLong(),
            pageSize = pageSize?.toLong(),
            grouped = grouped.toGroupedListGames(),
            hidePreRelease = hidePreRelease.toHidePreReleaseListGames(),
            region = region,
        ).body()
    }

    /** Returns {data, total, page, pageSize} paginated wrapper */
    suspend fun getAllGames(
        consoleId: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
        hidePreRelease: Boolean = true,
        grouped: Boolean = true,
        region: String? = null,
    ): com.spela.client.models.PaginatedResponseGameResponse {
        return gamesApi.listGames(
            consoleId = consoleId,
            sortBy = sortBy,
            sortOrder = sortOrder,
            page = page?.toLong(),
            pageSize = pageSize?.toLong(),
            grouped = grouped.toGroupedListGames(),
            hidePreRelease = hidePreRelease.toHidePreReleaseListGames(),
            region = region,
        ).body()
    }

    /** Returns {data, total, page, pageSize} paginated wrapper with search filter */
    suspend fun searchGames(
        query: String,
        consoleId: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        hidePreRelease: Boolean = true,
        grouped: Boolean = true,
        region: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): com.spela.client.models.PaginatedResponseGameResponse {
        return gamesApi.listGames(
            search = query,
            consoleId = consoleId,
            sortBy = sortBy,
            sortOrder = sortOrder,
            grouped = grouped.toGroupedListGames(),
            hidePreRelease = hidePreRelease.toHidePreReleaseListGames(),
            region = region,
            page = page?.toLong(),
            pageSize = pageSize?.toLong(),
        ).body()
    }

    /** Returns a single enriched GameResponse */
    suspend fun getGameDetail(gameId: String): com.spela.client.models.GameResponse {
        return gamesApi.getGame(gameId).body()
    }

    /** Triggers a scrape if the game has never been scraped. Returns immediately. */
    suspend fun scrapeIfNeeded(gameId: String) {
        gamesApi.scrapeGameIfNeeded(gameId)
    }

    /** Admin: scrape metadata for a single game. */
    suspend fun adminScrapeGame(gameId: String) {
        adminApi.scrapeGame(gameId)
    }

    /** Admin: refresh achievement cache for a single game. */
    suspend fun adminRefreshAchievements(gameId: String) {
        adminApi.refreshAchievements(gameId)
    }

    suspend fun getRecentlyAddedGames(pageSize: Int = 12): com.spela.client.models.PaginatedResponseGameResponse {
        return gamesApi.listGames(
            sortBy = "created_at",
            sortOrder = "desc",
            pageSize = pageSize.toLong(),
        ).body()
    }

    suspend fun getTopRatedGames(consoleId: String): List<com.spela.client.models.TopRatedGameResponse> {
        return consolesApi.getConsoleTopRated(consoleId).body()
    }

    suspend fun getTopRatedGamesGlobal(): List<com.spela.client.models.TopRatedGameResponse> {
        return consolesApi.getTopRatedGlobal().body()
    }

    suspend fun getTopRatedAvailable(): List<com.spela.client.models.TopListGameResponse> {
        return topListsApi.getTopListAvailable().body()
    }

    suspend fun getLongestGames(): List<com.spela.client.models.LongestGameResponse> {
        return topListsApi.getTopListLongest().body()
    }

    suspend fun getSimilarGames(gameId: String): List<com.spela.client.models.SimilarGameResponse> {
        return gamesApi.getSimilarGames(gameId).body()
    }

    suspend fun getDeveloperGames(gameId: String): List<com.spela.client.models.DeveloperGameResponse> {
        return gamesApi.getDeveloperGames(gameId).body()
    }

    /** Returns featured games for the Explore page hero carousel */
    suspend fun getExploreFeatured(): List<com.spela.client.models.FeaturedGameResponse> {
        return exploreApi.getExploreFeatured().body()
    }

    /** Returns curated rows for the Explore page (Top Rated, Recently Added, etc.) */
    suspend fun getExploreRows(): List<com.spela.client.models.ExploreRowResponse> {
        return exploreApi.getExploreRows().body().rows.orEmpty()
    }

    /** Returns all themes with game counts, sorted by count DESC */
    suspend fun getThemes(): List<com.spela.client.models.ThemeResponse> {
        return enrichmentApi.listThemes().body()
    }

    /** Returns paginated games for a theme */
    suspend fun getThemeGames(themeId: String, page: Int = 1, pageSize: Int = 20): com.spela.client.models.PaginatedResponseGameResponse {
        return enrichmentApi.listThemeGames(themeId, page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    /** Returns top keywords by game count */
    suspend fun getKeywords(limit: Int = 50): List<com.spela.client.models.KeywordResponse> {
        return enrichmentApi.listKeywords(limit = limit.toLong()).body()
    }

    /** Returns paginated games for a keyword */
    suspend fun getKeywordGames(keywordId: String, page: Int = 1, pageSize: Int = 20): com.spela.client.models.PaginatedResponseGameResponse {
        return enrichmentApi.listKeywordGames(keywordId, page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    /** Returns featured series for the Explore page */
    suspend fun getFeaturedSeries(): List<com.spela.client.models.FeaturedSeriesResponse> {
        return exploreApi.getExploreFeaturedSeries().body()
    }

    /** Returns detail for a specific series */
    suspend fun getSeriesDetail(id: String): com.spela.client.models.SeriesDetailResponse {
        return enrichmentApi.getSeriesDetail(id).body()
    }

    /** Returns detail for a specific franchise */
    suspend fun getFranchiseDetail(id: String): com.spela.client.models.FranchiseDetailResponse {
        return enrichmentApi.getFranchiseDetail(id).body()
    }

    /** Returns series links for a game */
    suspend fun getGameSeries(gameId: String): List<com.spela.client.models.GameSeriesResponse> {
        return enrichmentApi.getGameSeries(gameId).body()
    }

    /** Returns franchise links for a game */
    suspend fun getGameFranchises(gameId: String): List<com.spela.client.models.GameFranchiseResponse> {
        return enrichmentApi.getGameFranchises(gameId).body()
    }

    /** Returns available mood definitions for the mood picker */
    suspend fun getMoods(): List<com.spela.client.models.MoodResponse> {
        return exploreApi.getExploreMoods().body()
    }

    /** Returns games matching a mood */
    suspend fun getMoodGames(mood: String): List<com.spela.client.models.GameResponse> {
        return exploreApi.getMoodGames(mood).body()
    }

    /** Returns a single random surprise game */
    suspend fun getSurpriseGame(): com.spela.client.models.GameResponse {
        return exploreApi.getSurpriseGame().body()
    }

    /** Returns personalized "For You" recommendation rows */
    suspend fun getForYou(): com.spela.client.models.ForYouResponse {
        return exploreApi.getForYou().body()
    }

    /** Returns the user's taste profile (genre/theme/console breakdown) */
    suspend fun getTasteProfile(): com.spela.client.models.TasteProfileResponse {
        return profilesApi.getTasteProfile().body()
    }

    /** Returns collaborative filtering recommendations */
    suspend fun getPlayersLikeYou(): com.spela.client.models.PlayersLikeYouResponse {
        return exploreApi.getPlayersLikeYou().body()
    }

    /** Returns list of developer summaries */
    suspend fun getDevelopers(): com.spela.client.models.DeveloperListResponse {
        return exploreApi.getDevelopers().body()
    }

    /** Returns detail for a specific developer */
    suspend fun getDeveloperDetail(name: String): com.spela.client.models.DeveloperDetailResponse {
        return exploreApi.getDeveloperDetail(name).body()
    }

    /** Returns detail for a specific publisher. */
    suspend fun getPublisherDetail(name: String): com.spela.client.models.PublisherDetailResponse {
        return exploreApi.getPublisherDetail(name).body()
    }

    /** Returns developer spotlight for the Explore page */
    suspend fun getDeveloperSpotlight(): com.spela.client.models.DeveloperSpotlightResponse {
        return exploreApi.getDeveloperSpotlight().body()
    }

    /** Returns console showcase data for a specific console */
    suspend fun getConsoleShowcase(consoleId: String): com.spela.client.models.ConsoleShowcaseResponse {
        return exploreApi.getConsoleShowcase(consoleId).body()
    }

    /** Returns console highlights for the Explore page quick-jump section */
    suspend fun getConsoleHighlights(): com.spela.client.models.ConsoleHighlightsResponse {
        return exploreApi.getConsoleHighlights().body()
    }

    /** Returns a paginated list of IGDB artwork for the gallery */
    suspend fun getArtworkGallery(page: Int = 1): com.spela.client.models.ArtworkGalleryResponse {
        return exploreApi.getArtworkGallery(page = page.toLong()).body()
    }

    /** Returns a paginated list of screenshots for the gallery */
    suspend fun getScreenshotGallery(page: Int = 1): com.spela.client.models.ScreenshotGalleryResponse {
        return exploreApi.getScreenshotGallery(page = page.toLong()).body()
    }

    /** Returns trending games (most played this week) */
    suspend fun getTrending(): com.spela.client.models.TrendingResponse {
        return exploreApi.getTrending().body()
    }

    /** Returns community top-rated games */
    suspend fun getCommunityTop(): com.spela.client.models.CommunityTopResponse {
        return exploreApi.getCommunityTop().body()
    }

    /** Returns cult classics (high community, low IGDB) */
    suspend fun getCultClassics(): com.spela.client.models.CultClassicsResponse {
        return exploreApi.getCultClassics().body()
    }

    /** Returns recently reviewed games */
    suspend fun getRecentlyReviewed(): com.spela.client.models.RecentlyReviewedResponse {
        return exploreApi.getRecentlyReviewed().body()
    }

    /** Returns games with active shared sessions or challenges */
    suspend fun getActiveNow(): com.spela.client.models.ActiveNowResponse {
        return exploreApi.getActiveNow().body()
    }

    /** Returns games released on this day in history */
    suspend fun getOnThisDay(): com.spela.client.models.OnThisDayResponse {
        return exploreApi.getOnThisDay().body()
    }

    /** Returns best games from a given year */
    suspend fun getBestOfYear(year: Int): com.spela.client.models.BestOfYearResponse {
        return exploreApi.getBestOfYear(year.toString()).body()
    }

    /** Returns personal play anniversaries */
    suspend fun getYourAnniversaries(): com.spela.client.models.AnniversariesResponse {
        return exploreApi.getYourAnniversaries().body()
    }

    /** Returns games from a specific decade */
    suspend fun getDecade(decade: String): com.spela.client.models.DecadesResponse {
        return exploreApi.getDecades(decade).body()
    }

    /** Returns games that are easy to 100% complete */
    suspend fun getEasyToComplete(): com.spela.client.models.EasyToCompleteResponse {
        return exploreApi.getEasyToComplete().body()
    }

    /** Returns the hardest games to complete */
    suspend fun getHardestGames(): com.spela.client.models.HardestGamesResponse {
        return exploreApi.getHardestGames().body()
    }

    /** Returns games the user is almost done completing */
    suspend fun getAlmostDone(): com.spela.client.models.AlmostDoneResponse {
        return exploreApi.getAlmostDone().body()
    }

    /** Returns games with fresh achievement content */
    suspend fun getFreshChallenges(): com.spela.client.models.FreshChallengesResponse {
        return exploreApi.getFreshChallenges().body()
    }

    /** Returns active community challenges */
    suspend fun getActiveChallenges(): com.spela.client.models.ActiveChallengesResponse {
        return exploreApi.getActiveChallenges().body()
    }

    /** Returns games filtered by multi-faceted criteria */
    // Global Search

    suspend fun globalSearch(query: String, limit: Int = 5): com.spela.client.models.SearchResponse {
        return searchApi.globalSearch(q = query, limit = limit.toLong()).body()
    }

    suspend fun getFilteredGames(
        filters: Map<String, String>,
        page: Int? = null,
        pageSize: Int? = null,
    ): com.spela.client.models.PaginatedResponseGameResponse {
        return gamesApi.listGames(
            page = page?.toLong(),
            pageSize = pageSize?.toLong(),
            consoles = filters["consoles"],
            search = filters["search"],
            letter = filters["letter"],
            region = filters["region"],
            genres = filters["genres"],
            themes = filters["themes"],
            keywords = filters["keywords"],
            perspectives = filters["perspectives"],
            developer = filters["developer"],
            publisher = filters["publisher"],
            yearMin = filters["yearMin"],
            yearMax = filters["yearMax"],
            ratingMin = filters["ratingMin"],
            ratingMax = filters["ratingMax"],
            playStatus = filters["playStatus"],
            sortBy = filters["sortBy"],
            sortOrder = filters["sortOrder"],
        ).body()
    }

    /** Returns user's saved searches */
    suspend fun getSavedSearches(): List<com.spela.client.models.SavedSearchResponse> {
        return userApi.listSavedSearches().body()
    }

    /** Creates a new saved search */
    suspend fun createSavedSearch(
        name: String,
        filters: Map<String, String>,
    ): com.spela.client.models.SavedSearchResponse {
        return userApi.createSavedSearch(
            com.spela.client.models.SavedSearchRequest(
                name = name,
                filters = kotlinx.serialization.json.JsonObject(
                    filters.mapValues { (_, v) -> kotlinx.serialization.json.JsonPrimitive(v) },
                ),
            ),
        ).body()
    }

    /** Deletes a saved search */
    suspend fun deleteSavedSearch(id: String) {
        userApi.deleteSavedSearch(id)
    }

    // --- Phase 14: Wild Features ---

    /** Returns the decision wizard step configuration */
    suspend fun getWizardSteps(): com.spela.client.models.WizardResponse {
        return exploreApi.getWizardSteps().body()
    }

    /** Returns wizard recommendations based on mood/era/vibe choices */
    suspend fun getWizardResults(mood: String, era: String, vibe: String): com.spela.client.models.WizardResultsResponse {
        return exploreApi.getWizardResults(mood = mood, era = era, vibe = vibe).body()
    }

    /** Returns the user's explorer badges */
    suspend fun getExplorerBadges(): com.spela.client.models.ExplorerBadgesResponse {
        return profilesApi.getExplorerBadges().body()
    }

    /** Returns the user's completionist map (per-console progress) */
    suspend fun getCompletionistMap(): com.spela.client.models.CompletionistMapResponse {
        return profilesApi.getCompletionistMap().body()
    }

    /** Returns flat GameResponse[] with lastPlayedAt/totalPlayTime enriched */
    suspend fun getRecentGames(): List<com.spela.client.models.GameResponse> {
        return userApi.getRecentGames().body()
    }

    /** Returns flat GameResponse[] with isFavorite=true */
    suspend fun getFavoriteGames(): List<com.spela.client.models.GameResponse> {
        return favoritesApi.listFavorites().body()
    }

    suspend fun addFavorite(gameId: String) {
        favoritesApi.addFavorite(gameId)
    }

    suspend fun removeFavorite(gameId: String) {
        favoritesApi.removeFavorite(gameId)
    }

    /** Returns flat GameResponse[] for user's Play Later queue */
    suspend fun getPlayLaterGames(): List<com.spela.client.models.GameResponse> {
        return playLaterApi.listPlayLater().body()
    }

    suspend fun addToPlayLater(gameId: String) {
        playLaterApi.addToPlayLater(gameId)
    }

    suspend fun removeFromPlayLater(gameId: String) {
        playLaterApi.removeFromPlayLater(gameId)
    }

    // User Preferences

    suspend fun getPreferences(): com.spela.client.models.UserPreferencesResponse {
        return userApi.getUserPreferences().body()
    }

    suspend fun updatePreferences(
        request: com.spela.client.models.UpdatePreferencesRequest,
    ): com.spela.client.models.UserPreferencesResponse {
        return userApi.updateUserPreferences(request).body()
    }

    // Game Key Mapping

    suspend fun getGameKeyMapping(gameId: String): com.spela.client.models.GameKeyMappingResponse {
        return userApi.getGameKeyMapping(gameId).body()
    }

    suspend fun updateGameKeyMapping(
        gameId: String,
        request: com.spela.client.models.UpdateGameKeyMappingRequest,
    ): com.spela.client.models.GameKeyMappingResponse {
        return userApi.updateGameKeyMapping(gameId, request).body()
    }

    suspend fun deleteGameKeyMapping(gameId: String) {
        userApi.deleteGameKeyMapping(gameId)
    }

    // Game Stats

    suspend fun getGameStats(gameId: String): com.spela.client.models.GameStatsResponse {
        return gamesApi.getGameStats(gameId).body()
    }

    // Game Cheats

    suspend fun getGameCheats(gameId: String): List<com.spela.client.models.GameCheatResponse> {
        return gamesApi.getGameCheats(gameId).body()
    }

    // Game Achievements

    suspend fun getGameAchievements(gameId: String): com.spela.client.models.GameAchievementsResponse {
        return retroachievementsApi.getGameAchievements(gameId).body()
    }

    suspend fun getAchievementProgress(gameId: String): com.spela.client.models.GameAchievementProgressResponse {
        return retroachievementsApi.getAchievementProgress(gameId).body()
    }

    suspend fun getAchievementTimeline(
        gameId: String,
    ): com.spela.client.models.AchievementTimelineResponse {
        return retroachievementsApi.getAchievementTimeline(gameId).body()
    }

    suspend fun getAchievementLeaderboard(
        gameId: String,
    ): com.spela.client.models.AchievementLeaderboardResponse {
        return retroachievementsApi.getAchievementLeaderboard(gameId).body()
    }

    // User Stats & Achievements

    suspend fun getUserStats(): com.spela.client.models.UserStatsResponse {
        return userApi.getUserStats().body()
    }

    suspend fun getRecentAchievements(): com.spela.client.models.RecentAchievementsResponse {
        return retroachievementsApi.getRecentAchievements().body()
    }

    suspend fun getShowcase(): List<com.spela.client.models.ShowcaseEntryResponse> {
        return retroachievementsApi.getAchievementShowcase().body()
    }

    suspend fun getPublicShowcase(
        userId: String,
    ): List<com.spela.client.models.ShowcaseEntryResponse> {
        return retroachievementsApi.getPublicAchievementShowcase(userId).body()
    }

    suspend fun getUnlockedAchievements(): com.spela.client.models.UnlockedAchievementsResponse {
        return retroachievementsApi.getUnlockedAchievements().body()
    }

    suspend fun updateShowcase(
        entries: List<com.spela.client.models.ShowcaseEntryInput>,
    ): List<com.spela.client.models.ShowcaseEntryResponse> {
        return retroachievementsApi.updateAchievementShowcase(entries).body()
    }

    // Devices

    suspend fun deleteDevice(deviceId: Long) {
        devicesApi.deleteDevice(deviceId.toString())
    }

    suspend fun registerDevice(
        request: com.spela.client.models.RegisterDeviceRequest,
    ): com.spela.client.models.DeviceResponse {
        return devicesApi.registerDevice(request).body()
    }

    suspend fun getDevices(): List<com.spela.client.models.DeviceResponse> {
        return devicesApi.listDevices().body()
    }

    suspend fun updateDevice(deviceId: Long, name: String): com.spela.client.models.Device {
        return devicesApi.updateDevice(
            deviceId.toString(),
            com.spela.client.models.UpdateDeviceRequest(name = name),
        ).body()
    }

    suspend fun updateDevicePreferences(
        deviceId: Long,
        request: com.spela.client.models.UpdateDevicePreferencesRequest,
    ): com.spela.client.models.DevicePreferencesResponse {
        return devicesApi.updateDevicePreferences(deviceId.toString(), request).body()
    }

    // Game Download

    suspend fun downloadGame(gameId: String, onProgress: (Long, Long?) -> Unit = { _, _ -> }): ByteArray {
        val response = client.get("$baseUrl/api/games/$gameId/download") {
            onDownload { bytesSentTotal, contentLength ->
                onProgress(bytesSentTotal, contentLength)
            }
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Game download failed: HTTP ${response.status.value}")
        }
        return response.body()
    }

    suspend fun downloadM3U(gameId: String): ByteArray {
        // Same /api/games/{id}/download endpoint as downloadGame(); server
        // returns the M3U playlist for multi-disc titles when no disc is
        // specified. No progress callback needed here.
        return gamesApi.downloadGame(gameId).response.body<ByteArray>()
    }

    // BIOS

    suspend fun getBiosStatus(): com.spela.client.models.BiosListResponse {
        return biosApi.listBiosFiles().body()
    }

    suspend fun downloadBiosFile(filename: String): ByteArray {
        return biosApi.downloadBios(filename).response.body<ByteArray>()
    }

    /**
     * Fetches a Bundle BIOS entry's archive — a zip of the
     * `<biosDir>/<SubDir>/` tree on the server. Used by
     * BiosRepository when a registry entry is flagged
     * `bundle = true` (e.g. PPSSPP system assets — flash0 + lang +
     * fonts + atlases). Caller unzips the bytes into the local
     * `<biosDir>/<SubDir>/` to mirror the server's layout. See #911.
     */
    suspend fun downloadBiosArchive(filename: String): ByteArray {
        return biosApi.downloadBiosArchive(filename).response.body<ByteArray>()
    }

    // Cores

    suspend fun getAvailableCores(): List<com.spela.client.models.Core> {
        return coresApi.listCores().body()
    }

    suspend fun getCoreManifest(coreId: Long): com.spela.client.models.CoreManifestResponse {
        return coresApi.getCoreManifest(coreId).body()
    }

    suspend fun getRecommendedCore(gameId: String): com.spela.player.domain.model.LibretroCore {
        println("[ApiClient] getRecommendedCore HTTP call start (gameId=$gameId, baseUrl=$baseUrl)")
        val resp = try {
            gamesApi.getRecommendedCore(gameId)
        } catch (t: Throwable) {
            println("[ApiClient] getRecommendedCore HTTP call THREW: ${t::class.simpleName}: ${t.message}")
            throw t
        }
        println("[ApiClient] getRecommendedCore HTTP responded; decoding body")
        val dto = try {
            resp.body()
        } catch (t: Throwable) {
            println("[ApiClient] getRecommendedCore body() THREW: ${t::class.simpleName}: ${t.message}")
            throw t
        }
        println("[ApiClient] getRecommendedCore decoded: name=${dto.name}")
        return dto.toDomain()
    }

    suspend fun downloadCore(coreId: String, platform: String = "android", onProgress: (Long, Long?) -> Unit = { _, _ -> }): ByteArray {
        val response = client.get("$baseUrl/api/cores/$coreId/download") {
            parameter("platform", platform)
            onDownload { bytesSentTotal, contentLength ->
                onProgress(bytesSentTotal, contentLength)
            }
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Core download failed: HTTP ${response.status.value}")
        }
        return response.body()
    }

    /**
     * Fetches a specific historical build of the core identified by [coreId]
     * matching [sha256]. The endpoint returns the binary directly (zipped,
     * same as the unversioned endpoint) or 404 when the hash has been
     * pruned from server-side retention.
     *
     * Returns the raw HTTP status alongside the body so callers can
     * distinguish 404 (pruned) from real errors.
     */
    suspend fun downloadCoreByHash(
        coreId: String,
        sha256: String,
        platform: String = "android",
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): Pair<Int, ByteArray?> {
        val response = client.get("$baseUrl/api/cores/$coreId/download") {
            parameter("platform", platform)
            parameter("sha256", sha256)
            onDownload { bytesSentTotal, contentLength ->
                onProgress(bytesSentTotal, contentLength)
            }
        }
        val status = response.status.value
        return if (response.status.isSuccess()) {
            status to response.body<ByteArray>()
        } else {
            status to null
        }
    }

    // Shared Saves

    suspend fun getSharedSaves(
        gameId: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): com.spela.client.models.PaginatedResponseSharedSaveResponse {
        return sharedSavesApi.listSharedSaves(gameId, page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    suspend fun shareSave(
        gameId: String,
        name: String,
        description: String,
        data: ByteArray,
    ): com.spela.client.models.SharedSaveResponse {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/shared-saves",
            formData = formData {
                append("name", name)
                append("description", description)
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"shared-save.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    suspend fun downloadSharedSave(gameId: String, saveId: String): ByteArray {
        return sharedSavesApi.downloadSharedSave(gameId, saveId).response.body<ByteArray>()
    }

    suspend fun deleteSharedSave(gameId: String, saveId: String) {
        sharedSavesApi.deleteSharedSave(gameId, saveId)
    }

    // Ratings

    suspend fun rateGame(
        gameId: String,
        request: com.spela.client.models.CreateOrUpdateRatingRequest,
    ): com.spela.client.models.GameRatingResponse {
        return ratingsApi.createOrUpdateGameRating(gameId, request).body()
    }

    suspend fun getGameRatings(
        gameId: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): com.spela.client.models.PaginatedResponseGameRatingResponse {
        return ratingsApi.listGameRatings(gameId, page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    suspend fun getGameRatingSummary(
        gameId: String,
    ): com.spela.client.models.RatingSummaryResponse {
        return ratingsApi.getGameRatingSummary(gameId).body()
    }

    suspend fun getMyRating(gameId: String): com.spela.client.models.GameRatingResponse {
        return ratingsApi.getMyGameRating(gameId).body()
    }

    suspend fun deleteRating(gameId: String) {
        ratingsApi.deleteMyGameRating(gameId)
    }

    // Social

    suspend fun getOnlineUsers(): com.spela.client.models.OnlineUsersResponse {
        return socialApi.getOnlineUsers().body()
    }

    suspend fun getActivityFeed(
        page: Int = 1,
        pageSize: Int = 20,
    ): com.spela.client.models.PaginatedResponseActivityEventResponse {
        return socialApi.getActivityFeed(page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    suspend fun getPublicProfile(userId: String): com.spela.client.models.PublicProfileResponse {
        return socialApi.getPublicProfile(userId).body()
    }

    suspend fun getPublicPlayHeatmap(userId: String): List<com.spela.client.models.HeatmapEntry> {
        return statsApi.getPublicPlayHeatmap(userId).body()
    }

    suspend fun updatePlayTime(gameId: String, seconds: Long) {
        gamesApi.updateGamePlayTime(
            gameId,
            com.spela.client.models.UpdateGamePlayTimeRequest(seconds = seconds),
        )
    }

    suspend fun clearCurrentGame(gameId: String) {
        gamesApi.stopPlayingGame(gameId)
    }

    /**
     * Returns the WebSocket URL for real-time events, with the auth token as a query param.
     * Converts http(s):// to ws(s)://.
     */
    fun getWebSocketUrl(): String? {
        if (baseUrl.isBlank()) return null
        val token = tokenManager.accessToken ?: return null
        val wsBase = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        return "$wsBase/api/ws?token=$token"
    }

    // RetroAchievements

    suspend fun getRAStatus(): com.spela.client.models.RAStatusResponse {
        return retroachievementsApi.getRAAccountStatus().body()
    }

    suspend fun linkRA(
        request: com.spela.client.models.LinkRAAccountRequest,
    ): com.spela.client.models.RAStatusResponse {
        return retroachievementsApi.linkRAAccount(request).body()
    }

    suspend fun unlinkRA() {
        retroachievementsApi.unlinkRAAccount()
    }

    suspend fun getRAToken(): com.spela.client.models.RATokenResponse {
        return retroachievementsApi.getRAToken().body()
    }

    suspend fun updateRASettings(
        request: com.spela.client.models.UpdateRASettingsRequest,
    ): com.spela.client.models.RAStatusResponse {
        return retroachievementsApi.updateRASettings(request).body()
    }

    // Shared Sessions

    suspend fun getMySharedSessions(): List<SharedSessionDto> {
        // Server returns a bare array — not paginated.
        return sharedSessionsApi.listMySharedSessions().body()
    }

    suspend fun getSharedSession(sharedSessionId: String): SharedSessionDetailDto {
        return sharedSessionsApi.getSharedSession(sharedSessionId).body()
    }

    suspend fun getSharedSessionInvitations(): List<SharedSessionInvitationDto> {
        return sharedSessionsApi.listSharedSessionInvites().body()
    }

    suspend fun getPendingInvitationCount(): SharedSessionInvitationCountResponse {
        return sharedSessionsApi.getPendingSharedSessionInviteCount().body()
    }

    suspend fun createSharedSession(request: CreateSharedSessionRequest): SharedSessionDetailDto {
        return sharedSessionsApi.createSharedSession(request).body()
    }

    suspend fun deleteSharedSession(sharedSessionId: String) {
        sharedSessionsApi.deleteSharedSession(sharedSessionId)
    }

    suspend fun inviteToSharedSession(sharedSessionId: String, request: InviteToSharedSessionRequest) {
        sharedSessionsApi.inviteToSharedSession(sharedSessionId, request)
    }

    suspend fun acceptSharedSessionInvitation(invitationId: String) {
        sharedSessionsApi.acceptSharedSessionInvite(invitationId)
    }

    suspend fun rejectSharedSessionInvitation(invitationId: String) {
        // Server calls this "decline" — kept as rejectSharedSessionInvitation here
        // so the repository-level public name is stable.
        sharedSessionsApi.declineSharedSessionInvite(invitationId)
    }

    suspend fun leaveSharedSession(sharedSessionId: String) {
        sharedSessionsApi.leaveSharedSession(sharedSessionId)
    }

    suspend fun removeSharedSessionMember(sharedSessionId: String, userId: String) {
        sharedSessionsApi.removeSharedSessionMember(sharedSessionId, userId)
    }

    suspend fun getGameSharedSessions(gameId: String): List<SharedSessionDto> {
        return sharedSessionsApi.listGameSharedSessions(gameId).body()
    }

    suspend fun getSharedSessionSaves(sharedSessionId: String): List<SharedSessionSaveDto> {
        return sharedSessionsApi.listSharedSessionSaves(sharedSessionId).body()
    }

    suspend fun deleteSharedSessionSave(sharedSessionId: String, saveId: Long) {
        sharedSessionsApi.deleteSharedSessionSave(sharedSessionId, saveId.toString())
    }

    suspend fun takeTurn(sharedSessionId: String): TakeTurnResponse {
        return sharedSessionsApi.sharedSessionTakeTurn(sharedSessionId).body()
    }

    suspend fun releaseTurn(sharedSessionId: String) {
        sharedSessionsApi.sharedSessionReleaseTurn(sharedSessionId)
    }

    suspend fun sharedSessionHeartbeat(sharedSessionId: String) {
        sharedSessionsApi.sharedSessionHeartbeat(sharedSessionId)
    }

    suspend fun uploadSharedSessionSave(
        sharedSessionId: String,
        name: String,
        turnToken: String,
        data: ByteArray,
    ): SharedSessionSaveDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/shared-sessions/$sharedSessionId/saves",
            formData = formData {
                append("name", name)
                append("turnToken", turnToken)
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"shared-session-save.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    suspend fun downloadSharedSessionSave(sharedSessionId: String, saveId: Long): ByteArray {
        return sharedSessionsApi.downloadSharedSessionSave(sharedSessionId, saveId.toString()).response.body<ByteArray>()
    }

    suspend fun downloadSharedSessionAutoSave(sharedSessionId: String): ByteArray {
        return sharedSessionsApi.downloadSharedSessionAutoSave(sharedSessionId).response.body<ByteArray>()
    }

    suspend fun uploadSharedSessionAutoSave(
        sharedSessionId: String,
        turnToken: String,
        data: ByteArray,
    ): SharedSessionSaveDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/shared-sessions/$sharedSessionId/saves/auto",
            formData = formData {
                append("turnToken", turnToken)
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"shared-session-autosave.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    // Collections

    suspend fun getMyCollections(
        page: Int = 1,
        pageSize: Int = 20,
    ): com.spela.client.models.PaginatedResponseCollectionResponse {
        return collectionsApi.listMyCollections(page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    suspend fun getPublicCollections(
        page: Int = 1,
        pageSize: Int = 20,
    ): com.spela.client.models.PaginatedResponseCollectionResponse {
        return collectionsApi.listPublicCollections(page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    suspend fun getCollection(id: String): com.spela.client.models.CollectionDetailResponse {
        return collectionsApi.getCollection(id).body()
    }

    suspend fun createCollection(
        request: com.spela.client.models.CreateCollectionRequest,
    ): com.spela.client.models.CollectionResponse {
        return collectionsApi.createCollection(request).body()
    }

    suspend fun updateCollection(
        id: String,
        request: com.spela.client.models.UpdateCollectionRequest,
    ): com.spela.client.models.CollectionResponse {
        return collectionsApi.updateCollection(id, request).body()
    }

    suspend fun deleteCollection(id: String) {
        collectionsApi.deleteCollection(id)
    }

    suspend fun addGameToCollection(collectionId: String, gameId: String) {
        collectionsApi.addGameToCollection(
            collectionId,
            com.spela.client.models.AddGameToCollectionRequest(gameId = gameId.toLong()),
        )
    }

    suspend fun removeGameFromCollection(collectionId: String, gameId: String) {
        collectionsApi.removeGameFromCollection(collectionId, gameId)
    }

    // Stats

    suspend fun getMostPlayedGames(): com.spela.client.models.MostPlayedResponse {
        return statsApi.getMostPlayed().body()
    }

    suspend fun getMostActivePlayers(): com.spela.client.models.MostActivePlayersResponse {
        return statsApi.getMostActivePlayers().body()
    }

    // Netplay

    suspend fun createNetplaySession(
        request: com.spela.client.models.CreateNetplaySessionRequest,
    ): com.spela.client.models.NetplaySessionResponse {
        return netplayApi.createNetplaySession(request).body()
    }

    suspend fun getNetplaySessions(): com.spela.client.models.PaginatedResponseNetplaySessionResponse {
        return netplayApi.listNetplaySessions().body()
    }

    suspend fun getNetplaySession(sessionId: String): com.spela.client.models.NetplaySessionResponse {
        return netplayApi.getNetplaySession(sessionId).body()
    }

    suspend fun joinNetplayByInviteCode(
        request: com.spela.client.models.JoinByInviteCodeRequest,
    ): com.spela.client.models.NetplaySessionResponse {
        return netplayApi.joinNetplayByInviteCode(request).body()
    }

    suspend fun leaveNetplaySession(sessionId: String) {
        netplayApi.leaveNetplaySession(sessionId)
    }

    suspend fun deleteNetplaySession(sessionId: String) {
        netplayApi.deleteNetplaySession(sessionId)
    }

    suspend fun updateNetplaySettings(
        sessionId: String,
        request: com.spela.client.models.UpdateNetplaySettingsRequest,
    ): com.spela.client.models.NetplaySessionResponse {
        return netplayApi.updateNetplaySettings(sessionId, request).body()
    }

    suspend fun sendNetplayInvite(
        sessionId: String,
        request: com.spela.client.models.NetplayInviteUserRequest,
    ) {
        netplayApi.netplayInviteUser(sessionId, request)
    }

    // User Search

    suspend fun searchUsers(
        query: String = "",
        page: Int = 1,
        pageSize: Int = 20,
    ): com.spela.client.models.PaginatedResponseUserSearchResult {
        return socialApi.searchUsers(q = query, page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    suspend fun getRecentPartners(): List<com.spela.client.models.UserSearchResult> {
        return socialApi.getRecentPartners().body()
    }

    // Challenges

    suspend fun getChallenges(
        gameId: String? = null,
        consoleId: String? = null,
        difficulty: String? = null,
        sort: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
    ): com.spela.client.models.PaginatedResponseChallengeResponse {
        return challengesApi.listChallenges(
            gameId = gameId,
            consoleId = consoleId,
            difficulty = difficulty,
            sort = sort,
            page = page.toLong(),
            pageSize = pageSize.toLong(),
        ).body()
    }

    suspend fun getGameChallenges(
        gameId: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): com.spela.client.models.PaginatedResponseChallengeResponse {
        return challengesApi.listGameChallenges(gameId, page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    suspend fun getMyChallenges(
        page: Int = 1,
        pageSize: Int = 20,
    ): com.spela.client.models.PaginatedResponseChallengeResponse {
        return challengesApi.listMyChallenges(page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    suspend fun getChallenge(id: String): com.spela.client.models.ChallengeResponse {
        return challengesApi.getChallenge(id).body()
    }

    suspend fun createChallenge(
        gameId: String,
        name: String,
        description: String,
        type: String,
        difficulty: String,
        coreName: String,
        saveData: ByteArray,
        screenshotData: ByteArray?,
    ): com.spela.client.models.ChallengeResponse {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/challenges",
            formData = formData {
                append("gameId", gameId)
                append("name", name)
                append("description", description)
                append("type", type)
                append("difficulty", difficulty)
                append("coreName", coreName)
                append("save", saveData, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"challenge-save.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
                if (screenshotData != null) {
                    append("screenshot", screenshotData, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"screenshot.png\"")
                        append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    })
                }
            }
        ).body()
    }

    suspend fun deleteChallenge(id: String) {
        challengesApi.deleteChallenge(id)
    }

    suspend fun downloadChallengeSave(challengeId: String): ByteArray {
        return challengesApi.downloadChallengeSave(challengeId).response.body<ByteArray>()
    }

    suspend fun startChallengeAttempt(
        challengeId: String,
    ): com.spela.client.models.ChallengeAttemptResponse {
        return challengesApi.startChallengeAttempt(challengeId).body()
    }

    suspend fun completeChallengeAttempt(
        challengeId: String,
        attemptId: String,
    ): com.spela.client.models.ChallengeAttemptResponse {
        return challengesApi.completeChallengeAttempt(challengeId, attemptId).body()
    }

    suspend fun abandonChallengeAttempt(challengeId: String, attemptId: String) {
        challengesApi.abandonChallengeAttempt(challengeId, attemptId)
    }

    suspend fun getMyChallengeAttempts(
        challengeId: String,
    ): List<com.spela.client.models.ChallengeAttemptResponse> {
        return challengesApi.listMyChallengeAttempts(challengeId).body()
    }

    suspend fun getChallengeLeaderboard(
        challengeId: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): com.spela.client.models.PaginatedResponseChallengeLeaderboardEntry {
        return challengesApi.getChallengeLeaderboard(challengeId, page = page.toLong(), pageSize = pageSize.toLong()).body()
    }

    // Streaming Game Download

    suspend fun downloadGameToFile(
        gameId: String,
        fileStorage: FileStorage,
        destPath: String,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ) {
        client.prepareGet("$baseUrl/api/games/$gameId/download") {
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw RuntimeException("Game download failed: HTTP ${response.status.value}")
            }
            streamResponseToFile(response, fileStorage, destPath, onProgress)
        }
    }

    /**
     * Downloads a disc tar archive and extracts files directly to outputDir,
     * streaming the response to avoid buffering the entire archive in RAM.
     */
    suspend fun downloadDiscAndExtract(
        gameId: String,
        discNumber: Int,
        fileStorage: FileStorage,
        outputDir: String,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ) {
        client.prepareGet("$baseUrl/api/games/$gameId/discs/$discNumber/download") {
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw RuntimeException("Disc download failed: HTTP ${response.status.value}")
            }
            extractTarFromResponse(response, fileStorage, outputDir, onProgress)
        }
    }

    /**
     * Downloads a game tar archive (for .cue+.bin games without disc records)
     * and extracts files directly to outputDir.
     */
    suspend fun downloadGameAndExtract(
        gameId: String,
        fileStorage: FileStorage,
        outputDir: String,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ) {
        client.prepareGet("$baseUrl/api/games/$gameId/download") {
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw RuntimeException("Game download failed: HTTP ${response.status.value}")
            }
            extractTarFromResponse(response, fileStorage, outputDir, onProgress)
        }
    }

    /**
     * Extracts files from a tar archive response, streaming directly to disk.
     */
    private suspend fun extractTarFromResponse(
        response: HttpResponse,
        fileStorage: FileStorage,
        outputDir: String,
        onProgress: (Long, Long?) -> Unit,
    ) {
        val totalBytes = response.contentLength()
        val channel = response.bodyAsChannel()
        var downloaded = 0L

        // Tar format: 512-byte header, file data (padded to 512), repeat
        while (true) {
            // Read 512-byte tar header
            val header = ByteArray(512)
            channel.readFully(header, 0, 512)
            downloaded += 512
            onProgress(downloaded, totalBytes)

            // End-of-archive: all-zero block
            if (header.all { it == 0.toByte() }) break

            // Extract filename (bytes 0-99, null-terminated)
            val nameEnd = header.indexOf(0.toByte()).let { if (it < 0 || it > 100) 100 else it }
            val name = header.copyOfRange(0, nameEnd).decodeToString().trim()
            if (name.isEmpty()) break

            // Extract file size (bytes 124-135, octal ASCII)
            val sizeStr = header.copyOfRange(124, 136).decodeToString().trim().trimEnd(0.toChar())
            val fileSize = sizeStr.toLongOrNull(8) ?: 0L

            if (fileSize > 0) {
                // Stream file content directly to disk
                val filePath = "$outputDir/$name"
                fileStorage.writeFileStreaming(filePath) { append ->
                    var remaining = fileSize
                    val buffer = ByteArray(65536)
                    while (remaining > 0) {
                        val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                        val bytesRead = channel.readAvailable(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        append(buffer, 0, bytesRead)
                        remaining -= bytesRead
                        downloaded += bytesRead
                        onProgress(downloaded, totalBytes)
                    }
                }
                // Skip padding to next 512-byte boundary
                val padding = ((512 - (fileSize % 512)) % 512).toInt()
                if (padding > 0) {
                    val skip = ByteArray(padding)
                    channel.readFully(skip, 0, padding)
                    downloaded += padding
                }
            }
        }
    }

    suspend fun downloadDiscToFile(
        gameId: String,
        discNumber: Int,
        fileStorage: FileStorage,
        destPath: String,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ) {
        client.prepareGet("$baseUrl/api/games/$gameId/discs/$discNumber/download") {
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw RuntimeException("Disc download failed: HTTP ${response.status.value}")
            }
            streamResponseToFile(response, fileStorage, destPath, onProgress)
        }
    }

    private suspend fun streamResponseToFile(
        response: HttpResponse,
        fileStorage: FileStorage,
        destPath: String,
        onProgress: (Long, Long?) -> Unit,
    ) {
        val totalBytes = response.contentLength()
        val channel = response.bodyAsChannel()
        var downloaded = 0L
        println("[Download] Starting stream to $destPath, Content-Length=$totalBytes")

        fileStorage.writeFileStreaming(destPath) { append ->
            val buffer = ByteArray(65536)
            var chunkCount = 0
            while (true) {
                val bytesRead = channel.readAvailable(buffer)
                if (bytesRead == -1) break
                append(buffer, 0, bytesRead)
                downloaded += bytesRead
                chunkCount++
                if (chunkCount % 100 == 0) {
                    println("[Download] chunk=$chunkCount downloaded=$downloaded total=$totalBytes")
                }
                onProgress(downloaded, totalBytes)
            }
            println("[Download] Finished: $chunkCount chunks, $downloaded bytes written")
        }
    }

    // ── Game Sessions ──

    suspend fun getSessionsForGame(gameId: String): List<com.spela.client.models.GameSessionResponse> {
        return sessionsApi.listGameSessions(gameId).body()
    }

    suspend fun createSessionFromSharedSave(
        gameId: String,
        saveId: String,
    ): com.spela.client.models.GameSessionResponse {
        return sessionsApi.createSessionFromSharedSave(gameId, saveId).body()
    }

    suspend fun createSession(gameId: String, name: String): com.spela.client.models.GameSessionResponse {
        return sessionsApi.createSession(
            gameId,
            com.spela.client.models.CreateSessionRequest(name = name),
        ).body()
    }

    suspend fun getSession(sessionId: String): com.spela.client.models.GameSessionResponse {
        return sessionsApi.getSession(sessionId).body()
    }

    suspend fun updateSession(
        sessionId: String,
        name: String? = null,
        coreName: String? = null,
    ): com.spela.client.models.GameSessionResponse {
        return sessionsApi.updateSession(
            sessionId,
            com.spela.client.models.UpdateSessionRequest(name = name, coreName = coreName),
        ).body()
    }

    /**
     * Toggles the core-upgrade decision flags on a session. Each flag is
     * optional — null fields are left untouched server-side, matching the
     * partial-update semantics of PUT /api/sessions/{id}. See #672.
     */
    suspend fun updateSessionCoreFlags(
        sessionId: String,
        userLockedCoreVersion: Boolean? = null,
        autoLoadSuppressed: Boolean? = null,
        rehearsalCrashPending: Boolean? = null,
    ): com.spela.client.models.GameSessionResponse {
        return sessionsApi.updateSession(
            sessionId,
            com.spela.client.models.UpdateSessionRequest(
                userLockedCoreVersion = userLockedCoreVersion,
                autoLoadSuppressed = autoLoadSuppressed,
                rehearsalCrashPending = rehearsalCrashPending,
            ),
        ).body()
    }

    suspend fun deleteSession(sessionId: String) {
        sessionsApi.deleteSession(sessionId)
    }

    suspend fun cloneSession(
        sessionId: String,
        name: String? = null,
        saveId: Long? = null,
    ): com.spela.client.models.GameSessionResponse {
        // Always send a DuplicateSessionRequest — the model's `name` field
        // is nullable server-side, so passing name=null through the DTO
        // sends `{"name": null}` which huma accepts. Sending the Kotlin
        // value `null` itself would cause Ktor to serialize the body as
        // the JSON literal `null`, which huma rejects with 422. See #663.
        val request = com.spela.client.models.DuplicateSessionRequest(name = name)
        return sessionsApi.cloneSession(sessionId, saveId, request).body()
    }

    suspend fun getSessionSaves(sessionId: String): List<com.spela.client.models.SessionSaveResponse> {
        return sessionsApi.listSessionSaves(sessionId).body()
    }

    suspend fun updateSessionSave(
        sessionId: String,
        saveId: String,
        name: String,
    ): com.spela.client.models.SessionSaveResponse {
        return sessionsApi.updateSessionSave(
            sessionId,
            saveId,
            com.spela.client.models.UpdateSessionSaveRequest(name = name),
        ).body()
    }

    suspend fun deleteSessionSave(sessionId: String, saveId: String) {
        sessionsApi.deleteSessionSave(sessionId, saveId)
    }

    suspend fun uploadSessionSave(sessionId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String = ""): com.spela.client.models.SessionSaveResponse {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/sessions/$sessionId/saves",
            formData = formData {
                append("name", name)
                if (coreName.isNotEmpty()) {
                    append("coreName", coreName)
                }
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"save.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
                if (screenshot != null) {
                    append("screenshot", screenshot, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"screenshot.png\"")
                        append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    })
                }
            }
        ).body()
    }

    /**
     * Streaming variant of [uploadSessionSave] that reads the save bytes
     * from a file path on disk instead of holding them in a JVM ByteArray.
     * Required for Dolphin / GameCube (#798) where save states are
     * ~90 MB and a single ByteArray of that size doesn't fit alongside
     * the rest of the heap on Android (256 MB ceiling).
     */
    suspend fun uploadSessionSaveFromFile(
        sessionId: String,
        name: String,
        savePath: String,
        saveSize: Long,
        screenshot: ByteArray?,
        coreName: String = "",
        compression: String = "",
    ): com.spela.client.models.SessionSaveResponse {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/sessions/$sessionId/saves",
            formData = formData {
                append("name", name)
                if (coreName.isNotEmpty()) {
                    append("coreName", coreName)
                }
                if (compression.isNotEmpty()) {
                    append("compression", compression)
                }
                append("save", io.ktor.client.request.forms.ChannelProvider(saveSize) {
                    com.spela.player.util.openFileReadChannel(savePath)
                }, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"save.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
                if (screenshot != null) {
                    append("screenshot", screenshot, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"screenshot.png\"")
                        append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    })
                }
            }
        ).body()
    }

    suspend fun uploadSessionAutoSaveFromFile(
        sessionId: String,
        savePath: String,
        saveSize: Long,
        screenshot: ByteArray?,
        coreName: String = "",
        compression: String = "",
    ) {
        client.submitFormWithBinaryData(
            url = "$baseUrl/api/sessions/$sessionId/saves/auto",
            formData = formData {
                if (coreName.isNotEmpty()) {
                    append("coreName", coreName)
                }
                if (compression.isNotEmpty()) {
                    append("compression", compression)
                }
                append("save", io.ktor.client.request.forms.ChannelProvider(saveSize) {
                    com.spela.player.util.openFileReadChannel(savePath)
                }, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"autosave.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
                if (screenshot != null) {
                    append("screenshot", screenshot, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"screenshot.png\"")
                        append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    })
                }
            }
        )
    }

    suspend fun uploadSlotSaveFromFile(
        sessionId: String,
        slot: Int,
        savePath: String,
        saveSize: Long,
        screenshot: ByteArray?,
        coreName: String = "",
        compression: String = "",
    ): com.spela.client.models.SessionSaveResponse {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/sessions/$sessionId/saves/slot/$slot",
            formData = formData {
                if (coreName.isNotEmpty()) {
                    append("coreName", coreName)
                }
                if (compression.isNotEmpty()) {
                    append("compression", compression)
                }
                append("save", io.ktor.client.request.forms.ChannelProvider(saveSize) {
                    com.spela.player.util.openFileReadChannel(savePath)
                }, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"slot_${slot}.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
                if (screenshot != null) {
                    append("screenshot", screenshot, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"screenshot.png\"")
                        append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    })
                }
            }
        ) {
            method = HttpMethod.Put
        }.body()
    }

    suspend fun downloadSessionSave(sessionId: String, saveId: String): ByteArray {
        return sessionsApi.downloadSessionSave(sessionId, saveId).response.body<ByteArray>()
    }

    suspend fun uploadSessionAutoSave(sessionId: String, data: ByteArray, screenshot: ByteArray?, coreName: String = "") {
        client.submitFormWithBinaryData(
            url = "$baseUrl/api/sessions/$sessionId/saves/auto",
            formData = formData {
                if (coreName.isNotEmpty()) {
                    append("coreName", coreName)
                }
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"autosave.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
                if (screenshot != null) {
                    append("screenshot", screenshot, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"screenshot.png\"")
                        append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    })
                }
            }
        )
    }

    suspend fun downloadSessionAutoSave(sessionId: String): ByteArray {
        return sessionsApi.downloadSessionAutoSave(sessionId).response.body<ByteArray>()
    }

    /**
     * Streaming variant of [downloadSessionAutoSave] that writes the
     * response body directly to [outputPath] without materialising the
     * full payload as a JVM ByteArray. Required for cores like Dolphin
     * (#798) where save states are ~90 MB and don't fit alongside the
     * rest of the heap on Android.
     *
     * Uses `prepareGet().execute { }` to keep the connection open while
     * streaming — `client.get(url)` would eagerly buffer the body.
     */
    suspend fun downloadSessionAutoSaveToFile(sessionId: String, fileStorage: FileStorage, outputPath: String) {
        client.prepareGet("$baseUrl/api/sessions/$sessionId/saves/auto") {
            timeout { requestTimeoutMillis = Long.MAX_VALUE }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw RuntimeException("auto-save download failed: HTTP ${response.status.value}")
            }
            streamSaveResponseToFile(response, fileStorage, outputPath)
        }
    }

    /**
     * Uploads the per-session save_dir tarball, atomically replacing any
     * prior bundle. Streams the file from disk via ChannelProvider so the
     * tar is never fully materialised in memory. See #864.
     */
    suspend fun uploadSessionSaveDirBundleFromFile(sessionId: String, tarPath: String, tarSize: Long) {
        client.submitFormWithBinaryData(
            url = "$baseUrl/api/sessions/$sessionId/save-dir",
            formData = formData {
                append("file", io.ktor.client.request.forms.ChannelProvider(tarSize) {
                    com.spela.player.util.openFileReadChannel(tarPath)
                }, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"save-dir.tar\"")
                    append(HttpHeaders.ContentType, "application/x-tar")
                })
            }
        )
    }

    /**
     * Streams the session's save_dir tarball to [outputPath]. Throws on
     * non-2xx responses; in particular a 404 means the session has never
     * had a save_dir bundle and the caller should treat the local dir as
     * fresh-empty rather than failing the launch. See #864.
     */
    suspend fun downloadSessionSaveDirBundleToFile(sessionId: String, fileStorage: FileStorage, outputPath: String) {
        client.prepareGet("$baseUrl/api/sessions/$sessionId/save-dir") {
            timeout { requestTimeoutMillis = Long.MAX_VALUE }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw RuntimeException("save_dir bundle download failed: HTTP ${response.status.value}")
            }
            streamSaveResponseToFile(response, fileStorage, outputPath)
        }
    }

    suspend fun downloadSessionSaveToFile(sessionId: String, saveId: String, fileStorage: FileStorage, outputPath: String) {
        client.prepareGet("$baseUrl/api/sessions/$sessionId/saves/$saveId") {
            timeout { requestTimeoutMillis = Long.MAX_VALUE }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw RuntimeException("session save download failed: HTTP ${response.status.value}")
            }
            streamSaveResponseToFile(response, fileStorage, outputPath)
        }
    }

    suspend fun downloadSlotSaveToFile(sessionId: String, slot: Int, fileStorage: FileStorage, outputPath: String) {
        client.prepareGet("$baseUrl/api/sessions/$sessionId/saves/slot/$slot") {
            timeout { requestTimeoutMillis = Long.MAX_VALUE }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw RuntimeException("slot save download failed: HTTP ${response.status.value}")
            }
            streamSaveResponseToFile(response, fileStorage, outputPath)
        }
    }

    /**
     * Stream a save-state response to [outputPath] and decompress it
     * inline when the server tags the body with `X-Compression: gzip`.
     * Callers receive a temp file containing raw save bytes regardless
     * of how the bytes were stored on the server, so the libretro
     * `unserializeFromFile` step never has to know about compression.
     * See #804 phase 2.
     */
    private suspend fun streamSaveResponseToFile(
        response: HttpResponse,
        fileStorage: FileStorage,
        outputPath: String,
    ) {
        val compression = response.headers["X-Compression"]?.lowercase()?.trim().orEmpty()
        if (compression == "gzip") {
            // Stream the gzip-encoded body to a sibling and inflate
            // into outputPath, so the file at outputPath is always
            // raw save bytes the core can consume directly. If gunzip
            // throws mid-stream (truncated body, corrupted bytes) we
            // delete the partial outputPath as well so callers find
            // a clean "file missing" rather than a silently-bogus one.
            val gzPath = "$outputPath.gz.tmp"
            try {
                streamResponseToFile(response, fileStorage, gzPath) { _, _ -> }
                com.spela.player.util.gunzipFile(gzPath, outputPath)
            } catch (e: Throwable) {
                runCatching { fileStorage.deleteFile(outputPath) }
                throw e
            } finally {
                runCatching { fileStorage.deleteFile(gzPath) }
            }
            return
        }
        streamResponseToFile(response, fileStorage, outputPath) { _, _ -> }
    }

    suspend fun uploadSlotSave(sessionId: String, slot: Int, data: ByteArray, screenshot: ByteArray?, coreName: String = ""): com.spela.client.models.SessionSaveResponse {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/sessions/$sessionId/saves/slot/$slot",
            formData = formData {
                if (coreName.isNotEmpty()) {
                    append("coreName", coreName)
                }
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"slot_${slot}.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
                if (screenshot != null) {
                    append("screenshot", screenshot, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"screenshot.png\"")
                        append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    })
                }
            }
        ) {
            method = HttpMethod.Put
        }.body()
    }

    suspend fun downloadSlotSave(sessionId: String, slot: Int): ByteArray {
        return sessionsApi.downloadSessionSlotSave(sessionId, slot.toString()).response.body<ByteArray>()
    }

    suspend fun uploadSessionSram(sessionId: String, data: ByteArray, coreName: String = "") {
        client.submitFormWithBinaryData(
            url = "$baseUrl/api/sessions/$sessionId/sram",
            formData = formData {
                if (coreName.isNotEmpty()) {
                    append("coreName", coreName)
                }
                append("file", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"sram.srm\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        )
    }

    suspend fun downloadSessionSram(sessionId: String): ByteArray {
        return sessionsApi.downloadSessionSRAM(sessionId).response.body<ByteArray>()
    }

    suspend fun getSessionCheats(sessionId: String): com.spela.client.models.SessionCheatsResponse {
        return sessionsApi.getSessionCheats(sessionId).body()
    }

    suspend fun updateSessionCheats(
        sessionId: String,
        cheatsEnabled: Boolean,
        enabledIndices: List<Int>,
    ): com.spela.client.models.SessionCheatsResponse {
        return sessionsApi.updateSessionCheats(
            sessionId,
            com.spela.client.models.UpdateSessionCheatsRequest(
                cheatsEnabled = cheatsEnabled,
                enabledIndices = enabledIndices.map { it.toLong() },
            ),
        ).body()
    }

    fun close() {
        client.close()
    }
}

private fun Boolean.toGroupedListGames(): com.spela.client.apis.GamesApi.GroupedListGames =
    if (this) com.spela.client.apis.GamesApi.GroupedListGames.`true`
    else com.spela.client.apis.GamesApi.GroupedListGames.`false`

private fun Boolean.toHidePreReleaseListGames(): com.spela.client.apis.GamesApi.HidePreReleaseListGames =
    if (this) com.spela.client.apis.GamesApi.HidePreReleaseListGames.`true`
    else com.spela.client.apis.GamesApi.HidePreReleaseListGames.`false`
