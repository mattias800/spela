package com.spela.player.domain.repository

import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.User

interface AuthRepository {
    suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens>
    suspend fun register(serverUrl: String, username: String, email: String, password: String): Result<AuthTokens>
    suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens>
    suspend fun getCurrentUser(): Result<User>
    suspend fun getStoredTokens(): AuthTokens?
    suspend fun storeTokens(tokens: AuthTokens)
    suspend fun clearTokens()
    fun isLoggedIn(): Boolean
}
