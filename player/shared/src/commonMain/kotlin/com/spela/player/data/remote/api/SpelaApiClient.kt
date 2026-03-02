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
        val response = client.get("$baseUrl/api/health")
        return response.status.isSuccess()
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

    suspend fun getConsoles(): List<ConsoleDto> {
        return client.get("$baseUrl/api/consoles").body()
    }

    /** Returns paginated games for a console via the /api/games endpoint */
    suspend fun getGamesForConsole(
        consoleId: String,
        page: Int? = null,
        pageSize: Int? = null,
    ): GameListResponse {
        return client.get("$baseUrl/api/games") {
            parameter("consoleId", consoleId)
            page?.let { parameter("page", it) }
            pageSize?.let { parameter("pageSize", it) }
        }.body()
    }

    /** Returns {data, total, page, pageSize} paginated wrapper */
    suspend fun getAllGames(
        consoleId: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): GameListResponse {
        return client.get("$baseUrl/api/games") {
            consoleId?.let { parameter("consoleId", it) }
            sortBy?.let { parameter("sortBy", it) }
            sortOrder?.let { parameter("sortOrder", it) }
            page?.let { parameter("page", it) }
            pageSize?.let { parameter("pageSize", it) }
        }.body()
    }

    /** Returns {data, total, page, pageSize} paginated wrapper with search filter */
    suspend fun searchGames(
        query: String,
        consoleId: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
    ): GameListResponse {
        return client.get("$baseUrl/api/games") {
            parameter("search", query)
            consoleId?.let { parameter("consoleId", it) }
            sortBy?.let { parameter("sortBy", it) }
            sortOrder?.let { parameter("sortOrder", it) }
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

    suspend fun getTopRatedGames(consoleId: String): List<TopRatedGameDto> {
        return client.get("$baseUrl/api/consoles/$consoleId/top-rated").body()
    }

    suspend fun getTopRatedGamesGlobal(): List<TopRatedGameDto> {
        return client.get("$baseUrl/api/top-rated").body()
    }

    suspend fun getSimilarGames(gameId: String): List<SimilarGameDto> {
        return client.get("$baseUrl/api/games/$gameId/similar").body()
    }

    suspend fun getDeveloperGames(gameId: String): List<DeveloperGameDto> {
        return client.get("$baseUrl/api/games/$gameId/developer-games").body()
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

    // Saves

    suspend fun getSaveStates(gameId: String): List<SaveStateDto> {
        return client.get("$baseUrl/api/games/$gameId/saves").body()
    }

    /** Backend expects multipart form upload with "save" file and "name" field */
    suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray, coreName: String? = null): SaveStateDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/saves",
            formData = formData {
                append("name", name)
                if (!coreName.isNullOrEmpty()) append("coreName", coreName)
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"save.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    suspend fun downloadSaveState(gameId: String, saveId: String): ByteArray {
        val response = client.get("$baseUrl/api/games/$gameId/saves/$saveId")
        if (!response.status.isSuccess()) {
            throw RuntimeException("Save state download failed: HTTP ${response.status.value}")
        }
        return response.body()
    }

    suspend fun deleteSaveState(gameId: String, saveId: String) {
        client.delete("$baseUrl/api/games/$gameId/saves/$saveId")
    }

    /** Backend expects multipart form upload with "save" file */
    suspend fun uploadAutoSave(gameId: String, data: ByteArray, coreName: String? = null): SaveStateDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/saves/auto",
            formData = formData {
                if (!coreName.isNullOrEmpty()) append("coreName", coreName)
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"autosave.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    /** Backend expects multipart form upload with "save" file and optional "screenshot" file */
    suspend fun uploadSaveStateWithScreenshot(gameId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String? = null): SaveStateDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/saves",
            formData = formData {
                append("name", name)
                if (!coreName.isNullOrEmpty()) append("coreName", coreName)
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

    /** Backend expects multipart form upload with "save" file and optional "screenshot" file */
    suspend fun uploadAutoSaveWithScreenshot(gameId: String, data: ByteArray, screenshot: ByteArray?, coreName: String? = null): SaveStateDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/saves/auto",
            formData = formData {
                if (!coreName.isNullOrEmpty()) append("coreName", coreName)
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
        ).body()
    }

    /** Returns the auto-save file as raw bytes */
    suspend fun downloadAutoSave(gameId: String): ByteArray {
        val response = client.get("$baseUrl/api/games/$gameId/saves/auto")
        if (!response.status.isSuccess()) {
            throw RuntimeException("Auto-save download failed: HTTP ${response.status.value}")
        }
        return response.body()
    }

    /** Rename a save state */
    suspend fun renameSaveState(gameId: String, saveId: String, name: String) {
        client.put("$baseUrl/api/games/$gameId/saves/$saveId") {
            setBody(mapOf("name" to name))
        }
    }

    /** Update notes on a save state */
    suspend fun updateSaveNotes(gameId: String, saveId: String, notes: String) {
        client.put("$baseUrl/api/games/$gameId/saves/$saveId/notes") {
            setBody(mapOf("notes" to notes))
        }
    }

    /** Quick-save to a numbered slot */
    suspend fun saveToSlot(gameId: String, slot: Int, data: ByteArray, screenshot: ByteArray? = null, coreName: String? = null): SaveStateDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/saves/slot/$slot",
            formData = formData {
                if (!coreName.isNullOrEmpty()) append("coreName", coreName)
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"slot-$slot.sav\"")
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

    /** Load from a numbered slot */
    suspend fun loadFromSlot(gameId: String, slot: Int): ByteArray {
        return client.get("$baseUrl/api/games/$gameId/saves/slot/$slot").body()
    }

    /** Get all quick-save slots for a game */
    suspend fun getSlots(gameId: String): List<SaveStateDto> {
        return client.get("$baseUrl/api/games/$gameId/saves/slots").body()
    }

    /** Get auto-save history for a game */
    suspend fun getAutoSaveHistory(gameId: String): List<SaveStateDto> {
        return client.get("$baseUrl/api/games/$gameId/saves/auto/history").body()
    }

    /** Bulk delete saves */
    suspend fun bulkDeleteSaves(gameId: String, saveIds: List<Long>): Int {
        val response: Map<String, Int> = client.delete("$baseUrl/api/games/$gameId/saves/bulk") {
            setBody(mapOf("ids" to saveIds))
        }.body()
        return response["deleted"] ?: 0
    }

    /** Get storage usage */
    suspend fun getStorageUsage(): StorageUsageDto {
        return client.get("$baseUrl/api/user/storage-usage").body()
    }

    /** Import a save state */
    suspend fun importSaveState(gameId: String, name: String, fileData: ByteArray): SaveStateDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/saves/import",
            formData = formData {
                append("name", name)
                append("save", fileData, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"import.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    // BIOS

    suspend fun getBiosStatus(): BiosStatusResponse {
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

    suspend fun rateGame(gameId: String, request: RateGameRequest): GameRatingDto {
        return client.post("$baseUrl/api/games/$gameId/ratings") {
            setBody(request)
        }.body()
    }

    suspend fun getGameRatings(gameId: String, page: Int = 1, pageSize: Int = 20): GameRatingsResponse {
        return client.get("$baseUrl/api/games/$gameId/ratings") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getGameRatingSummary(gameId: String): RatingSummaryDto {
        return client.get("$baseUrl/api/games/$gameId/ratings/summary").body()
    }

    suspend fun getMyRating(gameId: String): GameRatingDto {
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

    suspend fun updatePlayTime(gameId: String, seconds: Long) {
        client.post("$baseUrl/api/games/$gameId/play-time") {
            setBody(mapOf("seconds" to seconds))
        }
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

    // Relays

    suspend fun getMyRelays(page: Int = 1, pageSize: Int = 20): RelaysResponse {
        return client.get("$baseUrl/api/relays") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }.body()
    }

    suspend fun getRelay(relayId: String): RelayDetailDto {
        return client.get("$baseUrl/api/relays/$relayId").body()
    }

    suspend fun getRelayInvitations(): RelayInvitationsResponse {
        return client.get("$baseUrl/api/relays/invitations").body()
    }

    suspend fun getPendingInvitationCount(): RelayInvitationCountResponse {
        return client.get("$baseUrl/api/relays/invitations/count").body()
    }

    suspend fun createRelay(request: CreateRelayRequest): RelayDetailDto {
        return client.post("$baseUrl/api/relays") {
            setBody(request)
        }.body()
    }

    suspend fun deleteRelay(relayId: String) {
        client.delete("$baseUrl/api/relays/$relayId")
    }

    suspend fun inviteToRelay(relayId: String, request: InviteToRelayRequest) {
        client.post("$baseUrl/api/relays/$relayId/invitations") {
            setBody(request)
        }
    }

    suspend fun acceptRelayInvitation(invitationId: String) {
        client.post("$baseUrl/api/relays/invitations/$invitationId/accept")
    }

    suspend fun rejectRelayInvitation(invitationId: String) {
        client.post("$baseUrl/api/relays/invitations/$invitationId/reject")
    }

    suspend fun leaveRelay(relayId: String) {
        client.delete("$baseUrl/api/relays/$relayId/members/me")
    }

    suspend fun removeRelayMember(relayId: String, userId: String) {
        client.delete("$baseUrl/api/relays/$relayId/members/$userId")
    }

    suspend fun getGameRelays(gameId: String): List<RelayDto> {
        return client.get("$baseUrl/api/games/$gameId/relays").body()
    }

    suspend fun getRelaySaves(relayId: String): List<RelaySaveDto> {
        return client.get("$baseUrl/api/relays/$relayId/saves").body()
    }

    suspend fun deleteRelaySave(relayId: String, saveId: Long) {
        client.delete("$baseUrl/api/relays/$relayId/saves/$saveId")
    }

    suspend fun takeTurn(relayId: String): TakeTurnResponse {
        return client.post("$baseUrl/api/relays/$relayId/turn/take").body()
    }

    suspend fun releaseTurn(relayId: String) {
        client.post("$baseUrl/api/relays/$relayId/turn/release")
    }

    suspend fun relayHeartbeat(relayId: String) {
        client.post("$baseUrl/api/relays/$relayId/heartbeat")
    }

    suspend fun uploadRelaySave(
        relayId: String,
        name: String,
        turnToken: String,
        data: ByteArray,
    ): RelaySaveDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/relays/$relayId/saves",
            formData = formData {
                append("name", name)
                append("turnToken", turnToken)
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"relay-save.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    suspend fun downloadRelaySave(relayId: String, saveId: Long): ByteArray {
        return client.get("$baseUrl/api/relays/$relayId/saves/$saveId/download").body()
    }

    suspend fun downloadRelayAutoSave(relayId: String): ByteArray {
        return client.get("$baseUrl/api/relays/$relayId/saves/auto").body()
    }

    suspend fun copyRelaySaveToGame(relayId: String, saveId: Long) {
        client.post("$baseUrl/api/relays/$relayId/saves/$saveId/copy-to-game")
    }

    suspend fun uploadRelayAutoSave(
        relayId: String,
        turnToken: String,
        data: ByteArray,
    ): RelaySaveDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/relays/$relayId/saves/auto",
            formData = formData {
                append("turnToken", turnToken)
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"relay-autosave.sav\"")
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

    // Save Data (SRAM)

    suspend fun getSaveDataList(gameId: String): List<SaveDataDto> {
        return client.get("$baseUrl/api/games/$gameId/save-data").body()
    }

    suspend fun uploadActiveSaveData(gameId: String, data: ByteArray): SaveDataDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/save-data/active",
            formData = formData {
                append("file", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"active.srm\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    suspend fun downloadActiveSaveData(gameId: String): ByteArray {
        val response = client.get("$baseUrl/api/games/$gameId/save-data/active")
        if (!response.status.isSuccess()) {
            throw RuntimeException("Active save data download failed: HTTP ${response.status.value}")
        }
        return response.body()
    }

    suspend fun uploadSaveData(gameId: String, name: String, data: ByteArray): SaveDataDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/save-data",
            formData = formData {
                append("name", name)
                append("file", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"save.srm\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    suspend fun downloadSaveData(gameId: String, saveDataId: String): ByteArray {
        val response = client.get("$baseUrl/api/games/$gameId/save-data/$saveDataId/download")
        if (!response.status.isSuccess()) {
            throw RuntimeException("Save data download failed: HTTP ${response.status.value}")
        }
        return response.body()
    }

    suspend fun activateSaveData(gameId: String, saveDataId: String) {
        client.put("$baseUrl/api/games/$gameId/save-data/$saveDataId/activate")
    }

    suspend fun renameSaveData(gameId: String, saveDataId: String, name: String) {
        client.put("$baseUrl/api/games/$gameId/save-data/$saveDataId") {
            setBody(mapOf("name" to name))
        }
    }

    suspend fun deleteSaveData(gameId: String, saveDataId: String) {
        client.delete("$baseUrl/api/games/$gameId/save-data/$saveDataId")
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

    fun close() {
        client.close()
    }
}
