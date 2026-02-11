package com.spela.player.data.remote.interceptor

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages token storage and refresh logic for API requests.
 */
class TokenManager {
    private val mutex = Mutex()

    var accessToken: String? = null
        private set
    var refreshToken: String? = null
        private set

    suspend fun setTokens(access: String, refresh: String) {
        mutex.withLock {
            accessToken = access
            refreshToken = refresh
        }
    }

    suspend fun clearTokens() {
        mutex.withLock {
            accessToken = null
            refreshToken = null
        }
    }

    fun hasTokens(): Boolean = accessToken != null
}

/**
 * Ktor plugin that adds the Authorization header to all requests.
 */
fun HttpClientConfig<*>.installAuth(tokenManager: TokenManager) {
    defaultRequest {
        tokenManager.accessToken?.let { token ->
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
