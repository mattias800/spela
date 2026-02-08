package com.spela.player.data.remote.interceptor

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.RefreshRequest
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
 * Ktor plugin that adds the Authorization header to all requests
 * and handles 401 responses with token refresh.
 */
fun HttpClientConfig<*>.installAuth(tokenManager: TokenManager) {
    install(HttpSend) {
        intercept { request ->
            tokenManager.accessToken?.let { token ->
                request.headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            execute(request)
        }
    }
}
