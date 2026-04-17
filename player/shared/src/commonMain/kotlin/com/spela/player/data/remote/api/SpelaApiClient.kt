package com.spela.player.data.remote.api

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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
                        val response: AuthResponse = client.post("$baseUrl/api/auth/refresh") {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(refreshToken))
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

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
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
            client.get("$baseUrl/api/health")
            true
        } catch (_: Exception) {
            false
        }
    }

    // Auth

    suspend fun login(request: LoginRequest): AuthResponse {
        return client.post("$baseUrl/api/auth/login") {
            setBody(request)
        }.body()
    }

    suspend fun register(request: RegisterRequest): AuthResponse {
        return client.post("$baseUrl/api/auth/register") {
            setBody(request)
        }.body()
    }

    suspend fun refreshToken(request: RefreshRequest): AuthResponse {
        return client.post("$baseUrl/api/auth/refresh") {
            setBody(request)
        }.body()
    }

    suspend fun getCurrentUser(): UserDto {
        return client.get("$baseUrl/api/user/profile").body()
    }

    // Consoles & Games

    suspend fun getConsoles(): List<com.spela.client.models.ConsoleResponse> {
        return client.get("$baseUrl/api/consoles").body()
    }

    /** Returns paginated games for a console via the /api/games endpoint */
    suspend fun getGamesForConsole(
        consoleId: String,
        page: Int? = null,
        pageSize: Int? = null,
        hidePreRelease: Boolean = true,
        grouped: Boolean = true,
        region: String? = null,
    ): GameListResponse {
        return client.get("$baseUrl/api/games") {
            parameter("consoleId", consoleId)
            page?.let { parameter("page", it) }
            pageSize?.let { parameter("pageSize", it) }
            parameter("hidePreRelease", hidePreRelease)
            parameter("grouped", grouped)
            region?.let { parameter("region", it) }
        }.body()
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
    ): GameListResponse {
        return client.get("$baseUrl/api/games") {
            consoleId?.let { parameter("consoleId", it) }
            sortBy?.let { parameter("sortBy", it) }
            sortOrder?.let { parameter("sortOrder", it) }
            page?.let { parameter("page", it) }
            pageSize?.let { parameter("pageSize", it) }
            parameter("hidePreRelease", hidePreRelease)
            parameter("grouped", grouped)
            region?.let { parameter("region", it) }
        }.body()
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
    ): GameListResponse {
        return client.get("$baseUrl/api/games") {
            parameter("search", query)
            consoleId?.let { parameter("consoleId", it) }
            sortBy?.let { parameter("sortBy", it) }
            sortOrder?.let { parameter("sortOrder", it) }
            parameter("hidePreRelease", hidePreRelease)
            parameter("grouped", grouped)
            region?.let { parameter("region", it) }
            page?.let { parameter("page", it) }
            pageSize?.let { parameter("pageSize", it) }
        }.body()
    }

    /** Returns a single enriched GameResponse */
    suspend fun getGameDetail(gameId: String): GameDto {
        return client.get("$baseUrl/api/games/$gameId").body()
    }

    /** Triggers a scrape if the game has never been scraped. Returns immediately. */
    suspend fun scrapeIfNeeded(gameId: String) {
        client.post("$baseUrl/api/games/$gameId/scrape-if-needed")
    }

    /** Admin: scrape metadata for a single game. */
    suspend fun adminScrapeGame(gameId: String) {
        client.post("$baseUrl/api/admin/games/$gameId/scrape")
    }

    /** Admin: refresh achievement cache for a single game. */
    suspend fun adminRefreshAchievements(gameId: String) {
        client.post("$baseUrl/api/admin/games/$gameId/achievements/refresh")
    }

    suspend fun getRecentlyAddedGames(pageSize: Int = 12): GameListResponse {
        return client.get("$baseUrl/api/games") {
            parameter("sortBy", "created_at")
            parameter("sortOrder", "desc")
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getTopRatedGames(consoleId: String): List<TopRatedGameDto> {
        return client.get("$baseUrl/api/consoles/$consoleId/top-rated").body()
    }

    suspend fun getTopRatedGamesGlobal(): List<TopRatedGameDto> {
        return client.get("$baseUrl/api/top-rated").body()
    }

    suspend fun getTopRatedAvailable(): List<TopListGameDto> {
        return client.get("$baseUrl/api/top-lists/top-rated").body()
    }

    suspend fun getLongestGames(): List<LongestGameDto> {
        return client.get("$baseUrl/api/top-lists/longest").body()
    }

    suspend fun getSimilarGames(gameId: String): List<SimilarGameDto> {
        return client.get("$baseUrl/api/games/$gameId/similar").body()
    }

    suspend fun getDeveloperGames(gameId: String): List<DeveloperGameDto> {
        return client.get("$baseUrl/api/games/$gameId/developer-games").body()
    }

    /** Returns featured games for the Explore page hero carousel */
    suspend fun getExploreFeatured(): List<FeaturedGameDto> {
        return client.get("$baseUrl/api/explore/featured").body()
    }

    /** Returns curated rows for the Explore page (Top Rated, Recently Added, etc.) */
    suspend fun getExploreRows(): List<ExploreRowDto> {
        val response: ExploreRowsResponseDto = client.get("$baseUrl/api/explore/rows").body()
        return response.rows
    }

    /** Returns all themes with game counts, sorted by count DESC */
    suspend fun getThemes(): List<com.spela.client.models.ThemeResponse> {
        return client.get("$baseUrl/api/themes").body()
    }

    /** Returns paginated games for a theme */
    suspend fun getThemeGames(themeId: String, page: Int = 1, pageSize: Int = 20): GameListResponse {
        return client.get("$baseUrl/api/themes/$themeId/games") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    /** Returns top keywords by game count */
    suspend fun getKeywords(limit: Int = 50): List<com.spela.client.models.KeywordResponse> {
        return client.get("$baseUrl/api/keywords") {
            parameter("limit", limit)
        }.body()
    }

    /** Returns paginated games for a keyword */
    suspend fun getKeywordGames(keywordId: String, page: Int = 1, pageSize: Int = 20): GameListResponse {
        return client.get("$baseUrl/api/keywords/$keywordId/games") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    /** Returns featured series for the Explore page */
    suspend fun getFeaturedSeries(): List<FeaturedSeriesDto> {
        return client.get("$baseUrl/api/explore/series/featured").body()
    }

    /** Returns detail for a specific series */
    suspend fun getSeriesDetail(id: String): SeriesDetailDto {
        return client.get("$baseUrl/api/series/$id").body()
    }

    /** Returns detail for a specific franchise */
    suspend fun getFranchiseDetail(id: String): SeriesDetailDto {
        return client.get("$baseUrl/api/franchises/$id").body()
    }

    /** Returns series links for a game */
    suspend fun getGameSeries(gameId: String): List<GameSeriesLinkDto> {
        return client.get("$baseUrl/api/games/$gameId/series").body()
    }

    /** Returns franchise links for a game */
    suspend fun getGameFranchises(gameId: String): List<GameFranchiseLinkDto> {
        return client.get("$baseUrl/api/games/$gameId/franchises").body()
    }

    /** Returns available mood definitions for the mood picker */
    suspend fun getMoods(): List<MoodDefinitionDto> {
        return client.get("$baseUrl/api/explore/moods").body()
    }

    /** Returns games matching a mood */
    suspend fun getMoodGames(mood: String): List<GameDto> {
        return client.get("$baseUrl/api/explore/mood/$mood").body()
    }

    /** Returns a single random surprise game */
    suspend fun getSurpriseGame(): GameDto {
        return client.get("$baseUrl/api/explore/surprise").body()
    }

    /** Returns personalized "For You" recommendation rows */
    suspend fun getForYou(): ForYouResponseDto {
        return client.get("$baseUrl/api/explore/for-you").body()
    }

    /** Returns the user's taste profile (genre/theme/console breakdown) */
    suspend fun getTasteProfile(): TasteProfileDto {
        return client.get("$baseUrl/api/user/taste-profile").body()
    }

    /** Returns collaborative filtering recommendations */
    suspend fun getPlayersLikeYou(): PlayersLikeYouResponseDto {
        return client.get("$baseUrl/api/explore/players-like-you").body()
    }

    /** Returns list of developer summaries */
    suspend fun getDevelopers(): DeveloperListResponseDto {
        return client.get("$baseUrl/api/explore/developers").body()
    }

    /** Returns detail for a specific developer */
    suspend fun getDeveloperDetail(name: String): DeveloperDetailResponseDto {
        val encoded = name.encodeURLParameter()
        return client.get("$baseUrl/api/explore/developers/$encoded").body()
    }

    /** Returns detail for a specific publisher (same response type as developer) */
    suspend fun getPublisherDetail(name: String): DeveloperDetailResponseDto {
        val encoded = name.encodeURLParameter()
        return client.get("$baseUrl/api/explore/publishers/$encoded").body()
    }

    /** Returns developer spotlight for the Explore page */
    suspend fun getDeveloperSpotlight(): DeveloperSpotlightResponseDto {
        return client.get("$baseUrl/api/explore/developers/spotlight").body()
    }

    /** Returns console showcase data for a specific console */
    suspend fun getConsoleShowcase(consoleId: String): ConsoleShowcaseDto {
        val encoded = consoleId.encodeURLParameter()
        return client.get("$baseUrl/api/explore/consoles/$encoded/showcase").body()
    }

    /** Returns console highlights for the Explore page quick-jump section */
    suspend fun getConsoleHighlights(): ConsoleHighlightsResponseDto {
        return client.get("$baseUrl/api/explore/console-highlights").body()
    }

    /** Returns a paginated list of IGDB artwork for the gallery */
    suspend fun getArtworkGallery(page: Int = 1): ArtworkGalleryResponseDto {
        return client.get("$baseUrl/api/explore/artwork") {
            parameter("page", page)
        }.body()
    }

    /** Returns a paginated list of screenshots for the gallery */
    suspend fun getScreenshotGallery(page: Int = 1): ScreenshotGalleryResponseDto {
        return client.get("$baseUrl/api/explore/screenshots") {
            parameter("page", page)
        }.body()
    }

    /** Returns trending games (most played this week) */
    suspend fun getTrending(): TrendingResponseDto {
        return client.get("$baseUrl/api/explore/trending").body()
    }

    /** Returns community top-rated games */
    suspend fun getCommunityTop(): CommunityTopResponseDto {
        return client.get("$baseUrl/api/explore/community-top").body()
    }

    /** Returns cult classics (high community, low IGDB) */
    suspend fun getCultClassics(): CultClassicsResponseDto {
        return client.get("$baseUrl/api/explore/cult-classics").body()
    }

    /** Returns recently reviewed games */
    suspend fun getRecentlyReviewed(): RecentlyReviewedResponseDto {
        return client.get("$baseUrl/api/explore/recently-reviewed").body()
    }

    /** Returns games with active shared sessions or challenges */
    suspend fun getActiveNow(): ActiveNowResponseDto {
        return client.get("$baseUrl/api/explore/active-now").body()
    }

    /** Returns games released on this day in history */
    suspend fun getOnThisDay(): OnThisDayResponseDto {
        return client.get("$baseUrl/api/explore/on-this-day").body()
    }

    /** Returns best games from a given year */
    suspend fun getBestOfYear(year: Int): BestOfYearResponseDto {
        return client.get("$baseUrl/api/explore/best-of-year/$year").body()
    }

    /** Returns personal play anniversaries */
    suspend fun getYourAnniversaries(): YourAnniversariesResponseDto {
        return client.get("$baseUrl/api/explore/your-anniversaries").body()
    }

    /** Returns games from a specific decade */
    suspend fun getDecade(decade: String): DecadeResponseDto {
        return client.get("$baseUrl/api/explore/decades/$decade").body()
    }

    /** Returns games that are easy to 100% complete */
    suspend fun getEasyToComplete(): EasyToCompleteResponseDto {
        return client.get("$baseUrl/api/explore/easy-to-complete").body()
    }

    /** Returns the hardest games to complete */
    suspend fun getHardestGames(): HardestGamesResponseDto {
        return client.get("$baseUrl/api/explore/hardest-games").body()
    }

    /** Returns games the user is almost done completing */
    suspend fun getAlmostDone(): AlmostDoneResponseDto {
        return client.get("$baseUrl/api/explore/almost-done").body()
    }

    /** Returns games with fresh achievement content */
    suspend fun getFreshChallenges(): FreshChallengesResponseDto {
        return client.get("$baseUrl/api/explore/fresh-challenges").body()
    }

    /** Returns active community challenges */
    suspend fun getActiveChallenges(): ActiveChallengesResponseDto {
        return client.get("$baseUrl/api/explore/active-challenges").body()
    }

    /** Returns games filtered by multi-faceted criteria */
    // Global Search

    suspend fun globalSearch(query: String, limit: Int = 5): GlobalSearchResponseDto {
        return client.get("$baseUrl/api/search") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()
    }

    suspend fun getFilteredGames(
        filters: Map<String, String>,
        page: Int? = null,
        pageSize: Int? = null,
    ): GameListResponse {
        return client.get("$baseUrl/api/games") {
            filters.forEach { (key, value) ->
                parameter(key, value)
            }
            page?.let { parameter("page", it) }
            pageSize?.let { parameter("pageSize", it) }
        }.body()
    }

    /** Returns user's saved searches */
    suspend fun getSavedSearches(): List<SavedSearchDto> {
        return client.get("$baseUrl/api/user/saved-searches").body()
    }

    /** Creates a new saved search */
    suspend fun createSavedSearch(name: String, filters: Map<String, String>): SavedSearchDto {
        return client.post("$baseUrl/api/user/saved-searches") {
            setBody(CreateSavedSearchRequest(
                name = name,
                filters = filters.mapValues { (_, v) -> kotlinx.serialization.json.JsonPrimitive(v) },
            ))
        }.body()
    }

    /** Deletes a saved search */
    suspend fun deleteSavedSearch(id: String) {
        client.delete("$baseUrl/api/user/saved-searches/$id")
    }

    // --- Phase 14: Wild Features ---

    /** Returns the decision wizard step configuration */
    suspend fun getWizardSteps(): WizardResponseDto {
        return client.get("$baseUrl/api/explore/wizard").body()
    }

    /** Returns wizard recommendations based on mood/era/vibe choices */
    suspend fun getWizardResults(mood: String, era: String, vibe: String): WizardResultsResponseDto {
        return client.get("$baseUrl/api/explore/wizard/results") {
            parameter("mood", mood)
            parameter("era", era)
            parameter("vibe", vibe)
        }.body()
    }

    /** Returns the user's explorer badges */
    suspend fun getExplorerBadges(): com.spela.client.models.ExplorerBadgesResponse {
        return client.get("$baseUrl/api/user/explorer-badges").body()
    }

    /** Returns the user's completionist map (per-console progress) */
    suspend fun getCompletionistMap(): com.spela.client.models.CompletionistMapResponse {
        return client.get("$baseUrl/api/user/completionist-map").body()
    }

    /** Returns flat GameResponse[] with lastPlayedAt/totalPlayTime enriched */
    suspend fun getRecentGames(): List<GameDto> {
        return client.get("$baseUrl/api/user/recent").body()
    }

    /** Returns flat GameResponse[] with isFavorite=true */
    suspend fun getFavoriteGames(): List<GameDto> {
        return client.get("$baseUrl/api/user/favorites").body()
    }

    suspend fun addFavorite(gameId: String) {
        client.post("$baseUrl/api/user/favorites/$gameId")
    }

    suspend fun removeFavorite(gameId: String) {
        client.delete("$baseUrl/api/user/favorites/$gameId")
    }

    /** Returns flat GameResponse[] for user's Play Later queue */
    suspend fun getPlayLaterGames(): List<GameDto> {
        return client.get("$baseUrl/api/user/play-later").body()
    }

    suspend fun addToPlayLater(gameId: String) {
        client.post("$baseUrl/api/user/play-later/$gameId")
    }

    suspend fun removeFromPlayLater(gameId: String) {
        client.delete("$baseUrl/api/user/play-later/$gameId")
    }

    // User Preferences

    suspend fun getPreferences(): UserPreferencesDto {
        return client.get("$baseUrl/api/user/preferences").body()
    }

    suspend fun updatePreferences(request: UpdatePreferencesRequest): UserPreferencesDto {
        return client.put("$baseUrl/api/user/preferences") {
            setBody(request)
        }.body()
    }

    // Game Key Mapping

    suspend fun getGameKeyMapping(gameId: String): GameKeyMappingDto {
        return client.get("$baseUrl/api/user/games/$gameId/keymapping").body()
    }

    suspend fun updateGameKeyMapping(gameId: String, request: UpdateGameKeyMappingRequest): GameKeyMappingDto {
        return client.put("$baseUrl/api/user/games/$gameId/keymapping") {
            setBody(request)
        }.body()
    }

    suspend fun deleteGameKeyMapping(gameId: String) {
        client.delete("$baseUrl/api/user/games/$gameId/keymapping")
    }

    // Game Stats

    suspend fun getGameStats(gameId: String): GameStatsDto {
        return client.get("$baseUrl/api/games/$gameId/stats").body()
    }

    // Game Cheats

    suspend fun getGameCheats(gameId: String): List<CheatDto> {
        return client.get("$baseUrl/api/games/$gameId/cheats").body()
    }

    // Game Achievements

    suspend fun getGameAchievements(gameId: String): GameAchievementsResponse {
        return client.get("$baseUrl/api/games/$gameId/achievements").body()
    }

    suspend fun getAchievementProgress(gameId: String): AchievementProgressResponse {
        return client.get("$baseUrl/api/games/$gameId/achievements/progress").body()
    }

    suspend fun getAchievementTimeline(gameId: String): AchievementTimelineResponse {
        return client.get("$baseUrl/api/games/$gameId/achievements/timeline").body()
    }

    suspend fun getAchievementLeaderboard(gameId: String): AchievementLeaderboardResponse {
        return client.get("$baseUrl/api/games/$gameId/achievements/leaderboard").body()
    }

    // User Stats & Achievements

    suspend fun getUserStats(): UserStatsDto {
        return client.get("$baseUrl/api/user/stats").body()
    }

    suspend fun getRecentAchievements(): RecentAchievementsResponse {
        return client.get("$baseUrl/api/user/achievements/recent").body()
    }

    suspend fun getShowcase(): List<ShowcaseAchievementDto> {
        return client.get("$baseUrl/api/user/achievements/showcase").body()
    }

    suspend fun getPublicShowcase(userId: String): List<ShowcaseAchievementDto> {
        return client.get("$baseUrl/api/users/$userId/achievements/showcase").body()
    }

    suspend fun getUnlockedAchievements(): UnlockedAchievementsResponse {
        return client.get("$baseUrl/api/user/achievements/unlocked").body()
    }

    suspend fun updateShowcase(entries: List<ShowcaseUpdateEntry>): List<ShowcaseAchievementDto> {
        return client.put("$baseUrl/api/user/achievements/showcase") {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(entries)
        }.body()
    }

    // Devices

    suspend fun deleteDevice(deviceId: Long) {
        client.delete("$baseUrl/api/user/devices/$deviceId")
    }

    suspend fun registerDevice(request: RegisterDeviceRequest): DeviceDto {
        return client.post("$baseUrl/api/user/devices") {
            setBody(request)
        }.body()
    }

    suspend fun getDevices(): List<DeviceDto> {
        return client.get("$baseUrl/api/user/devices").body()
    }

    suspend fun updateDevice(deviceId: Long, name: String): DeviceDto {
        return client.put("$baseUrl/api/user/devices/$deviceId") {
            setBody(mapOf("name" to name))
        }.body()
    }

    suspend fun updateDevicePreferences(deviceId: Long, request: UpdateDevicePreferencesRequest): DeviceDto {
        return client.put("$baseUrl/api/user/devices/$deviceId/preferences") {
            setBody(request)
        }.body()
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
        val response = client.get("$baseUrl/api/games/$gameId/download")
        if (!response.status.isSuccess()) {
            throw RuntimeException("M3U download failed: HTTP ${response.status.value}")
        }
        return response.body()
    }

    // BIOS

    suspend fun getBiosStatus(): com.spela.client.models.BiosListResponse {
        return client.get("$baseUrl/api/bios").body()
    }

    suspend fun downloadBiosFile(filename: String): ByteArray {
        return client.get("$baseUrl/api/bios/${filename.encodeURLPath()}").body()
    }

    // Cores

    suspend fun getAvailableCores(): List<LibretroCoreDto> {
        return client.get("$baseUrl/api/cores").body()
    }

    /** May return either a full Core object or just {coreName: "..."} */
    suspend fun getRecommendedCore(gameId: String): LibretroCoreDto {
        val text: String = client.get("$baseUrl/api/games/$gameId/core").body()
        return try {
            json.decodeFromString<LibretroCoreDto>(text)
        } catch (_: Exception) {
            // Server returns just {coreName: "..."} when core isn't in DB
            val obj = json.parseToJsonElement(text).jsonObject
            val coreName = obj["coreName"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("No core name in response: $text")
            LibretroCoreDto(id = 0, name = coreName)
        }
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

    // Shared Saves

    suspend fun getSharedSaves(gameId: String, page: Int = 1, pageSize: Int = 20): SharedSavesResponse {
        return client.get("$baseUrl/api/games/$gameId/shared-saves") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun shareSave(gameId: String, name: String, description: String, data: ByteArray): SharedSaveStateDto {
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
        return client.get("$baseUrl/api/games/$gameId/shared-saves/$saveId/download").body()
    }

    suspend fun deleteSharedSave(gameId: String, saveId: String) {
        client.delete("$baseUrl/api/games/$gameId/shared-saves/$saveId")
    }

    // Ratings

    suspend fun rateGame(
        gameId: String,
        request: com.spela.client.models.CreateOrUpdateRatingRequest,
    ): com.spela.client.models.GameRatingResponse {
        return client.post("$baseUrl/api/games/$gameId/ratings") {
            setBody(request)
        }.body()
    }

    suspend fun getGameRatings(
        gameId: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): com.spela.client.models.PaginatedResponseGameRatingResponse {
        return client.get("$baseUrl/api/games/$gameId/ratings") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getGameRatingSummary(
        gameId: String,
    ): com.spela.client.models.RatingSummaryResponse {
        return client.get("$baseUrl/api/games/$gameId/ratings/summary").body()
    }

    suspend fun getMyRating(gameId: String): com.spela.client.models.GameRatingResponse {
        return client.get("$baseUrl/api/games/$gameId/ratings/mine").body()
    }

    suspend fun deleteRating(gameId: String) {
        client.delete("$baseUrl/api/games/$gameId/ratings")
    }

    // Social

    suspend fun getOnlineUsers(): OnlineUsersResponse {
        return client.get("$baseUrl/api/social/online").body()
    }

    suspend fun getActivityFeed(page: Int = 1, pageSize: Int = 20): ActivityFeedResponse {
        return client.get("$baseUrl/api/social/activity") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getPublicProfile(userId: String): PublicProfileDto {
        return client.get("$baseUrl/api/users/$userId/profile").body()
    }

    suspend fun getPublicPlayHeatmap(userId: String): List<HeatmapEntryDto> {
        return client.get("$baseUrl/api/users/$userId/play-heatmap").body()
    }

    suspend fun updatePlayTime(gameId: String, seconds: Long) {
        client.post("$baseUrl/api/games/$gameId/play-time") {
            setBody(mapOf("seconds" to seconds))
        }
    }

    suspend fun clearCurrentGame(gameId: String) {
        client.delete("$baseUrl/api/games/$gameId/play-time")
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

    suspend fun getRAStatus(): RAStatusDto {
        return client.get("$baseUrl/api/user/ra/status").body()
    }

    suspend fun linkRA(request: RALinkRequestDto): RAStatusDto {
        return client.post("$baseUrl/api/user/ra/link") {
            setBody(request)
        }.body()
    }

    suspend fun unlinkRA() {
        client.delete("$baseUrl/api/user/ra/link")
    }

    suspend fun getRAToken(): RATokenResponseDto {
        return client.get("$baseUrl/api/user/ra/token").body()
    }

    suspend fun updateRASettings(request: RASettingsRequestDto): RAStatusDto {
        return client.put("$baseUrl/api/user/ra/settings") {
            setBody(request)
        }.body()
    }

    // Shared Sessions

    suspend fun getMySharedSessions(page: Int = 1, pageSize: Int = 20): SharedSessionsResponse {
        return client.get("$baseUrl/api/shared-sessions") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getSharedSession(sharedSessionId: String): SharedSessionDetailDto {
        return client.get("$baseUrl/api/shared-sessions/$sharedSessionId").body()
    }

    suspend fun getSharedSessionInvitations(): SharedSessionInvitationsResponse {
        return client.get("$baseUrl/api/shared-sessions/invitations").body()
    }

    suspend fun getPendingInvitationCount(): SharedSessionInvitationCountResponse {
        return client.get("$baseUrl/api/shared-sessions/invitations/count").body()
    }

    suspend fun createSharedSession(request: CreateSharedSessionRequest): SharedSessionDetailDto {
        return client.post("$baseUrl/api/shared-sessions") {
            setBody(request)
        }.body()
    }

    suspend fun deleteSharedSession(sharedSessionId: String) {
        client.delete("$baseUrl/api/shared-sessions/$sharedSessionId")
    }

    suspend fun inviteToSharedSession(sharedSessionId: String, request: InviteToSharedSessionRequest) {
        client.post("$baseUrl/api/shared-sessions/$sharedSessionId/invitations") {
            setBody(request)
        }
    }

    suspend fun acceptSharedSessionInvitation(invitationId: String) {
        client.post("$baseUrl/api/shared-sessions/invitations/$invitationId/accept")
    }

    suspend fun rejectSharedSessionInvitation(invitationId: String) {
        client.post("$baseUrl/api/shared-sessions/invitations/$invitationId/reject")
    }

    suspend fun leaveSharedSession(sharedSessionId: String) {
        client.delete("$baseUrl/api/shared-sessions/$sharedSessionId/members/me")
    }

    suspend fun removeSharedSessionMember(sharedSessionId: String, userId: String) {
        client.delete("$baseUrl/api/shared-sessions/$sharedSessionId/members/$userId")
    }

    suspend fun getGameSharedSessions(gameId: String): List<SharedSessionDto> {
        return client.get("$baseUrl/api/games/$gameId/shared-sessions").body()
    }

    suspend fun getSharedSessionSaves(sharedSessionId: String): List<SharedSessionSaveDto> {
        return client.get("$baseUrl/api/shared-sessions/$sharedSessionId/saves").body()
    }

    suspend fun deleteSharedSessionSave(sharedSessionId: String, saveId: Long) {
        client.delete("$baseUrl/api/shared-sessions/$sharedSessionId/saves/$saveId")
    }

    suspend fun takeTurn(sharedSessionId: String): TakeTurnResponse {
        return client.post("$baseUrl/api/shared-sessions/$sharedSessionId/turn/take").body()
    }

    suspend fun releaseTurn(sharedSessionId: String) {
        client.post("$baseUrl/api/shared-sessions/$sharedSessionId/turn/release")
    }

    suspend fun sharedSessionHeartbeat(sharedSessionId: String) {
        client.post("$baseUrl/api/shared-sessions/$sharedSessionId/heartbeat")
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
        return client.get("$baseUrl/api/shared-sessions/$sharedSessionId/saves/$saveId/download").body()
    }

    suspend fun downloadSharedSessionAutoSave(sharedSessionId: String): ByteArray {
        return client.get("$baseUrl/api/shared-sessions/$sharedSessionId/saves/auto").body()
    }

    suspend fun copySharedSessionSaveToGame(sharedSessionId: String, saveId: Long) {
        client.post("$baseUrl/api/shared-sessions/$sharedSessionId/saves/$saveId/copy-to-game")
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

    suspend fun getMyCollections(page: Int = 1, pageSize: Int = 20): CollectionsResponse {
        return client.get("$baseUrl/api/collections") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getPublicCollections(page: Int = 1, pageSize: Int = 20): CollectionsResponse {
        return client.get("$baseUrl/api/collections/public") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getCollection(id: String): CollectionDetailDto {
        return client.get("$baseUrl/api/collections/$id").body()
    }

    suspend fun createCollection(request: CreateCollectionRequest): CollectionDto {
        return client.post("$baseUrl/api/collections") {
            setBody(request)
        }.body()
    }

    suspend fun updateCollection(id: String, request: UpdateCollectionRequest): CollectionDto {
        return client.put("$baseUrl/api/collections/$id") {
            setBody(request)
        }.body()
    }

    suspend fun deleteCollection(id: String) {
        client.delete("$baseUrl/api/collections/$id")
    }

    suspend fun addGameToCollection(collectionId: String, gameId: String) {
        client.post("$baseUrl/api/collections/$collectionId/games") {
            setBody(AddGameToCollectionRequest(gameId = gameId.toInt()))
        }
    }

    suspend fun removeGameFromCollection(collectionId: String, gameId: String) {
        client.delete("$baseUrl/api/collections/$collectionId/games/$gameId")
    }

    // Stats

    suspend fun getMostPlayedGames(): MostPlayedResponse {
        return client.get("$baseUrl/api/stats/most-played").body()
    }

    suspend fun getMostActivePlayers(): MostActivePlayersResponse {
        return client.get("$baseUrl/api/stats/most-active-players").body()
    }

    // Netplay

    suspend fun createNetplaySession(request: CreateNetplaySessionRequest): NetplaySessionDto {
        return client.post("$baseUrl/api/netplay/sessions") {
            setBody(request)
        }.body()
    }

    suspend fun getNetplaySessions(): NetplaySessionsResponse {
        return client.get("$baseUrl/api/netplay/sessions").body()
    }

    suspend fun getNetplaySession(sessionId: String): NetplaySessionDto {
        return client.get("$baseUrl/api/netplay/sessions/$sessionId").body()
    }

    suspend fun joinNetplayByInviteCode(request: JoinByInviteCodeRequest): NetplaySessionDto {
        return client.post("$baseUrl/api/netplay/sessions/join") {
            setBody(request)
        }.body()
    }

    suspend fun leaveNetplaySession(sessionId: String) {
        client.post("$baseUrl/api/netplay/sessions/$sessionId/leave")
    }

    suspend fun deleteNetplaySession(sessionId: String) {
        client.delete("$baseUrl/api/netplay/sessions/$sessionId")
    }

    suspend fun updateNetplaySettings(sessionId: String, request: UpdateNetplaySettingsRequest): NetplaySessionDto {
        return client.put("$baseUrl/api/netplay/sessions/$sessionId/settings") {
            setBody(request)
        }.body()
    }

    suspend fun sendNetplayInvite(sessionId: String, request: SendNetplayInviteRequest) {
        client.post("$baseUrl/api/netplay/sessions/$sessionId/invites") {
            setBody(request)
        }
    }

    // User Search

    suspend fun searchUsers(query: String = "", page: Int = 1, pageSize: Int = 20): UserSearchResponse {
        return client.get("$baseUrl/api/users/search") {
            parameter("q", query)
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getRecentPartners(): List<UserSearchResultDto> {
        return client.get("$baseUrl/api/users/recent-partners").body()
    }

    // Challenges

    suspend fun getChallenges(
        gameId: String? = null,
        consoleId: String? = null,
        difficulty: String? = null,
        sort: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
    ): ChallengesResponse {
        return client.get("$baseUrl/api/challenges") {
            gameId?.let { parameter("gameId", it) }
            consoleId?.let { parameter("consoleId", it) }
            difficulty?.let { parameter("difficulty", it) }
            sort?.let { parameter("sort", it) }
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getGameChallenges(gameId: String, page: Int = 1, pageSize: Int = 20): ChallengesResponse {
        return client.get("$baseUrl/api/games/$gameId/challenges") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getMyChallenges(page: Int = 1, pageSize: Int = 20): ChallengesResponse {
        return client.get("$baseUrl/api/user/challenges") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getChallenge(id: String): ChallengeDto {
        return client.get("$baseUrl/api/challenges/$id").body()
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
    ): ChallengeDto {
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
        client.delete("$baseUrl/api/challenges/$id")
    }

    suspend fun downloadChallengeSave(challengeId: String): ByteArray {
        return client.get("$baseUrl/api/challenges/$challengeId/save/download").body()
    }

    suspend fun startChallengeAttempt(challengeId: String): ChallengeAttemptDto {
        return client.post("$baseUrl/api/challenges/$challengeId/attempts/start").body()
    }

    suspend fun completeChallengeAttempt(challengeId: String, attemptId: String): ChallengeAttemptDto {
        return client.post("$baseUrl/api/challenges/$challengeId/attempts/$attemptId/complete").body()
    }

    suspend fun abandonChallengeAttempt(challengeId: String, attemptId: String) {
        client.post("$baseUrl/api/challenges/$challengeId/attempts/$attemptId/abandon")
    }

    suspend fun getMyChallengeAttempts(challengeId: String): List<ChallengeAttemptDto> {
        return client.get("$baseUrl/api/challenges/$challengeId/attempts/mine").body()
    }

    suspend fun getChallengeLeaderboard(
        challengeId: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): ChallengeLeaderboardResponse {
        return client.get("$baseUrl/api/challenges/$challengeId/leaderboard") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
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

    suspend fun getSessionsForGame(gameId: String): List<GameSessionDto> {
        return client.get("$baseUrl/api/games/$gameId/sessions").body()
    }

    suspend fun createSessionFromSharedSave(gameId: String, saveId: String): GameSessionDto {
        return client.post("$baseUrl/api/games/$gameId/sessions/from-shared-save/$saveId").body()
    }

    suspend fun createSession(gameId: String, name: String): GameSessionDto {
        return client.post("$baseUrl/api/games/$gameId/sessions") {
            setBody(CreateSessionRequest(name = name))
        }.body()
    }

    suspend fun getSession(sessionId: String): GameSessionDto {
        return client.get("$baseUrl/api/sessions/$sessionId").body()
    }

    suspend fun updateSession(sessionId: String, name: String? = null, coreName: String? = null): GameSessionDto {
        return client.put("$baseUrl/api/sessions/$sessionId") {
            setBody(UpdateSessionRequest(name = name, coreName = coreName))
        }.body()
    }

    suspend fun deleteSession(sessionId: String) {
        client.delete("$baseUrl/api/sessions/$sessionId")
    }

    suspend fun duplicateSession(sessionId: String, name: String? = null): GameSessionDto {
        return client.post("$baseUrl/api/sessions/$sessionId/duplicate") {
            if (name != null) {
                setBody(mapOf("name" to name))
            }
        }.body()
    }

    suspend fun getSessionSaves(sessionId: String): List<SaveStateDto> {
        return client.get("$baseUrl/api/sessions/$sessionId/saves").body()
    }

    suspend fun uploadSessionSave(sessionId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String = ""): SaveStateDto {
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

    suspend fun downloadSessionSave(sessionId: String, saveId: String): ByteArray {
        val response = client.get("$baseUrl/api/sessions/$sessionId/saves/$saveId")
        if (!response.status.isSuccess()) {
            throw RuntimeException("Session save download failed: HTTP ${response.status.value}")
        }
        return response.body()
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
        val response = client.get("$baseUrl/api/sessions/$sessionId/saves/auto")
        if (!response.status.isSuccess()) {
            throw RuntimeException("Session auto-save download failed: HTTP ${response.status.value}")
        }
        return response.body()
    }

    suspend fun uploadSlotSave(sessionId: String, slot: Int, data: ByteArray, screenshot: ByteArray?, coreName: String = ""): SaveStateDto {
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
        val response = client.get("$baseUrl/api/sessions/$sessionId/saves/slot/$slot")
        if (!response.status.isSuccess()) {
            throw RuntimeException("Slot save download failed: HTTP ${response.status.value}")
        }
        return response.body()
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
        val response = client.get("$baseUrl/api/sessions/$sessionId/sram")
        if (!response.status.isSuccess()) {
            throw RuntimeException("Session SRAM download failed: HTTP ${response.status.value}")
        }
        return response.body()
    }

    suspend fun getSessionCheats(sessionId: String): SessionCheatConfigDto {
        return client.get("$baseUrl/api/sessions/$sessionId/cheats").body()
    }

    suspend fun updateSessionCheats(sessionId: String, cheatsEnabled: Boolean, enabledIndices: List<Int>): SessionCheatConfigDto {
        return client.put("$baseUrl/api/sessions/$sessionId/cheats") {
            setBody(UpdateSessionCheatsRequest(cheatsEnabled = cheatsEnabled, enabledIndices = enabledIndices))
        }.body()
    }

    fun close() {
        client.close()
    }
}
