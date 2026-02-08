package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.LoginRequest
import com.spela.player.data.remote.dto.RefreshRequest
import com.spela.player.data.remote.dto.RegisterRequest
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.User
import com.spela.player.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val tokenManager: TokenManager,
) : AuthRepository {

    private var cachedTokens: AuthTokens? = null

    override suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens> {
        return runCatching {
            apiClient.setBaseUrl(serverUrl)
            val response = apiClient.login(LoginRequest(username, password))
            val tokens = response.toDomain()
            tokenManager.setTokens(tokens.accessToken, tokens.refreshToken)
            cachedTokens = tokens
            tokens
        }
    }

    override suspend fun register(serverUrl: String, username: String, email: String, password: String): Result<AuthTokens> {
        return runCatching {
            apiClient.setBaseUrl(serverUrl)
            val response = apiClient.register(RegisterRequest(username, email, password))
            val tokens = response.toDomain()
            tokenManager.setTokens(tokens.accessToken, tokens.refreshToken)
            cachedTokens = tokens
            tokens
        }
    }

    override suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens> {
        return runCatching {
            apiClient.setBaseUrl(serverUrl)
            val response = apiClient.refreshToken(RefreshRequest(refreshToken))
            val tokens = response.toDomain()
            tokenManager.setTokens(tokens.accessToken, tokens.refreshToken)
            cachedTokens = tokens
            tokens
        }
    }

    override suspend fun getCurrentUser(): Result<User> {
        return runCatching {
            apiClient.getCurrentUser().toDomain()
        }
    }

    override suspend fun getStoredTokens(): AuthTokens? = cachedTokens

    override suspend fun storeTokens(tokens: AuthTokens) {
        cachedTokens = tokens
        tokenManager.setTokens(tokens.accessToken, tokens.refreshToken)
    }

    override suspend fun clearTokens() {
        cachedTokens = null
        tokenManager.clearTokens()
    }

    override fun isLoggedIn(): Boolean = tokenManager.hasTokens()
}
