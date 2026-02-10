package com.spela.player.data.remote.api

import com.spela.player.data.remote.dto.*
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.data.remote.interceptor.installAuth
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class SpelaApiClient(
    private val engineFactory: io.ktor.client.engine.HttpClientEngineFactory<*>,
    private val tokenManager: TokenManager,
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

        installAuth(tokenManager)

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

    /** Returns flat GameResponse[] for a console */
    suspend fun getGamesForConsole(consoleId: String): List<GameDto> {
        return client.get("$baseUrl/api/consoles/$consoleId/games").body()
    }

    /** Returns {data, total, page, pageSize} paginated wrapper */
    suspend fun getAllGames(): GameListResponse {
        return client.get("$baseUrl/api/games").body()
    }

    /** Returns {data, total, page, pageSize} paginated wrapper with search filter */
    suspend fun searchGames(query: String): GameListResponse {
        return client.get("$baseUrl/api/games") {
            parameter("search", query)
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

    // User Preferences

    suspend fun getPreferences(): UserPreferencesDto {
        return client.get("$baseUrl/api/user/preferences").body()
    }

    suspend fun updatePreferences(request: UpdatePreferencesRequest): UserPreferencesDto {
        return client.put("$baseUrl/api/user/preferences") {
            setBody(request)
        }.body()
    }

    // Devices

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

    suspend fun downloadGame(gameId: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): ByteArray {
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

    // Saves

    suspend fun getSaveStates(gameId: String): List<SaveStateDto> {
        return client.get("$baseUrl/api/games/$gameId/saves").body()
    }

    /** Backend expects multipart form upload with "save" file and "name" field */
    suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray): SaveStateDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/saves",
            formData = formData {
                append("name", name)
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"save.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    suspend fun downloadSaveState(gameId: String, saveId: String): ByteArray {
        return client.get("$baseUrl/api/games/$gameId/saves/$saveId").body()
    }

    suspend fun deleteSaveState(gameId: String, saveId: String) {
        client.delete("$baseUrl/api/games/$gameId/saves/$saveId")
    }

    /** Backend expects multipart form upload with "save" file */
    suspend fun uploadAutoSave(gameId: String, data: ByteArray): SaveStateDto {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/api/games/$gameId/saves/auto",
            formData = formData {
                append("save", data, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"autosave.sav\"")
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                })
            }
        ).body()
    }

    /** Returns the auto-save file as raw bytes */
    suspend fun downloadAutoSave(gameId: String): ByteArray {
        return client.get("$baseUrl/api/games/$gameId/saves/auto").body()
    }

    // Cores

    suspend fun getAvailableCores(): List<LibretroCoreDto> {
        return client.get("$baseUrl/api/cores").body()
    }

    /** May return either a full Core object or just {coreName: "..."} */
    suspend fun getRecommendedCore(gameId: String): LibretroCoreDto {
        return client.get("$baseUrl/api/games/$gameId/core").body()
    }

    suspend fun downloadCore(coreId: String, platform: String = "android", onProgress: (Long, Long) -> Unit = { _, _ -> }): ByteArray {
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

    fun close() {
        client.close()
    }
}
