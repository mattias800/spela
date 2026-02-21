package com.spela.player.data.remote.interceptor

import io.ktor.client.plugins.auth.providers.*
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

    fun toBearerTokens(): BearerTokens? {
        val access = accessToken ?: return null
        val refresh = refreshToken ?: return null
        return BearerTokens(access, refresh)
    }
}
