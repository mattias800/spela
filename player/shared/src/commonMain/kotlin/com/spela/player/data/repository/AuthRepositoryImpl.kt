package com.spela.player.data.repository

import com.spela.client.models.AuthLoginRequest
import com.spela.client.models.AuthRefreshRequest
import com.spela.client.models.AuthRegisterRequest
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.extractUser
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.CurrentUserContext
import com.spela.player.domain.model.User
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.CurrentUserContextRepository
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val tokenManager: TokenManager,
    private val database: SpelaDatabase,
    private val currentUserContextRepository: CurrentUserContextRepository,
) : AuthRepository {

    private val queries = database.spelaDatabaseQueries
    private var cachedTokens: AuthTokens? = null
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens> {
        return runCatching {
            apiClient.setBaseUrl(serverUrl)
            val response = apiClient.login(AuthLoginRequest(username = username, password = password))
            val tokens = response.toDomain()
            persistTokens(tokens)
            currentUserContextRepository.cache(response.extractUser())
            tokens
        }
    }

    override suspend fun register(serverUrl: String, username: String, password: String): Result<AuthTokens> {
        return runCatching {
            apiClient.setBaseUrl(serverUrl)
            val response = apiClient.register(
                AuthRegisterRequest(username = username, password = password),
            )
            val tokens = response.toDomain()
            persistTokens(tokens)
            response.user?.toDomain()?.let { currentUserContextRepository.cache(it) }
            tokens
        }
    }

    override suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens> {
        return runCatching {
            apiClient.setBaseUrl(serverUrl)
            val response = apiClient.refreshToken(AuthRefreshRequest(refreshToken = refreshToken))
            val tokens = response.toDomain()
            persistTokens(tokens)
            currentUserContextRepository.cache(response.extractUser())
            tokens
        }
    }

    override suspend fun getCurrentUser(): Result<User> {
        return runCatching {
            apiClient.getCurrentUser().toDomain().also {
                currentUserContextRepository.cache(it)
            }
        }
    }

    override suspend fun getStoredTokens(): AuthTokens? {
        cachedTokens?.let { return it }
        val entity = queries.getTokens().executeAsOneOrNull() ?: return null
        val tokens = AuthTokens(
            accessToken = entity.access_token,
            refreshToken = entity.refresh_token,
        )
        cachedTokens = tokens
        tokenManager.setTokens(tokens.accessToken, tokens.refreshToken)
        seedCurrentUserContextFromToken(tokens.accessToken)
        return tokens
    }

    override suspend fun storeTokens(tokens: AuthTokens) {
        persistTokens(tokens)
    }

    override suspend fun clearTokens() {
        cachedTokens = null
        tokenManager.clearTokens()
        queries.deleteTokens()
        currentUserContextRepository.clear()
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        apiClient.logout()
    }

    override fun isLoggedIn(): Boolean = tokenManager.hasTokens()

    private suspend fun persistTokens(tokens: AuthTokens) {
        cachedTokens = tokens
        tokenManager.setTokens(tokens.accessToken, tokens.refreshToken)
        queries.insertTokens(
            access_token = tokens.accessToken,
            refresh_token = tokens.refreshToken,
            expires_at = "",
        )
    }

    private suspend fun seedCurrentUserContextFromToken(accessToken: String) {
        if (currentUserContextRepository.getCached() != null) return
        contextFromAccessToken(accessToken)?.let { currentUserContextRepository.cache(it) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun contextFromAccessToken(accessToken: String): CurrentUserContext? = runCatching {
        val payload = accessToken.split('.').getOrNull(1) ?: return@runCatching null
        val padded = payload.padEnd(payload.length + ((4 - payload.length % 4) % 4), '=')
        val decoded = Base64.UrlSafe.decode(padded).decodeToString()
        val claims = json.parseToJsonElement(decoded).jsonObject
        val userId = claims["userId"]?.jsonPrimitive?.content ?: return@runCatching null
        val username = claims["username"]?.jsonPrimitive?.content ?: ""
        CurrentUserContext(userId = userId, username = username)
    }.getOrNull()
}
