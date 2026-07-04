package com.spela.player.domain.repository

import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.User

interface AuthRepository {
    suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens>
    suspend fun register(serverUrl: String, username: String, password: String): Result<AuthTokens>
    suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens>
    suspend fun getCurrentUser(): Result<User>
    suspend fun getStoredTokens(): AuthTokens?
    suspend fun storeTokens(tokens: AuthTokens)
    suspend fun clearTokens()
    /**
     * Calls POST /api/auth/logout on the server, blacklisting the access token
     * and revoking refresh tokens. Failures are surfaced as Result.failure but
     * callers should still proceed to clearTokens locally — a sign-out must
     * never get stuck on a network error.
     */
    suspend fun logout(): Result<Unit>
    fun isLoggedIn(): Boolean
}
