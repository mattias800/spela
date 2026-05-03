package com.spela.player.data.remote.interceptor

import io.ktor.client.plugins.auth.providers.*
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages token storage and refresh logic for API requests.
 *
 * The two token fields are `@Volatile` so non-suspend readers
 * (`hasTokens`, `toBearerTokens`) see the latest value written by the
 * suspend setters even though they don't acquire the mutex. The mutex
 * still serialises writes so concurrent setTokens / clearTokens calls
 * can't tear an intermediate state into the volatile slot. See #978.
 */
class TokenManager {
    private val mutex = Mutex()

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
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
