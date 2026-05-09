package com.spela.player.data.repository

import com.spela.client.models.AuthLoginRequest
import com.spela.client.models.AuthRefreshRequest
import com.spela.client.models.AuthRegisterRequest
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.User
import com.spela.player.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val tokenManager: TokenManager,
    private val database: SpelaDatabase,
) : AuthRepository {

    private val queries = database.spelaDatabaseQueries
    private var cachedTokens: AuthTokens? = null

    override suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens> {
        return runCatching {
            apiClient.setBaseUrl(serverUrl)
            val response = apiClient.login(AuthLoginRequest(username = username, password = password))
            val tokens = response.toDomain()
            persistTokens(tokens)
            tokens
        }
    }

    override suspend fun register(serverUrl: String, username: String, email: String, password: String): Result<AuthTokens> {
        return runCatching {
            apiClient.setBaseUrl(serverUrl)
            val response = apiClient.register(
                AuthRegisterRequest(username = username, email = email, password = password),
            )
            val tokens = response.toDomain()
            persistTokens(tokens)
            tokens
        }
    }

    override suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens> {
        return runCatching {
            apiClient.setBaseUrl(serverUrl)
            val response = apiClient.refreshToken(AuthRefreshRequest(refreshToken = refreshToken))
            val tokens = response.toDomain()
            persistTokens(tokens)
            tokens
        }
    }

    override suspend fun getCurrentUser(): Result<User> {
        return runCatching {
            apiClient.getCurrentUser().toDomain()
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
        return tokens
    }

    override suspend fun storeTokens(tokens: AuthTokens) {
        persistTokens(tokens)
    }

    override suspend fun clearTokens() {
        cachedTokens = null
        tokenManager.clearTokens()
        queries.deleteTokens()
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
        println("[AuthDiag] persistTokens: head=${tokens.accessToken.take(12)} hasTokens=${tokenManager.hasTokens()}")
    }
}
