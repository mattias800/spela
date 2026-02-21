package com.spela.player.domain.usecase

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.ServerRepository
import io.ktor.client.plugins.*

/**
 * Result of attempting to restore a persisted session on app startup.
 */
sealed class RestoreSessionResult {
    /** Tokens and server restored, user validated. Navigate to Home. */
    data object Success : RestoreSessionResult()

    /** Stored server exists but server unreachable. Proceed offline. */
    data object OfflineSuccess : RestoreSessionResult()

    /** Stored server exists but tokens are invalid/expired. Navigate to Login. */
    data class NeedsLogin(val serverUrl: String) : RestoreSessionResult()

    /** No stored session at all. Navigate to ServerConnection. */
    data object NoSession : RestoreSessionResult()
}

/**
 * Checks for a persisted session (tokens + active server) and validates it.
 *
 * Flow:
 * 1. Read active server from DB → if none, return NoSession
 * 2. Read tokens from DB → if none, return NeedsLogin
 * 3. Set base URL on API client from stored server
 * 4. Try getCurrentUser() to validate token
 * 5. On success → return Success
 * 6. On auth error (401/403) → clear tokens, return NeedsLogin
 * 7. On any other error (network) → return OfflineSuccess
 */
class RestoreSessionUseCase(
    private val authRepository: AuthRepository,
    private val serverRepository: ServerRepository,
    private val apiClient: SpelaApiClient,
) {
    suspend operator fun invoke(): RestoreSessionResult {
        val activeServer = serverRepository.getActiveServer()
            ?: return RestoreSessionResult.NoSession

        val tokens = authRepository.getStoredTokens()
            ?: return RestoreSessionResult.NeedsLogin(activeServer.url)

        apiClient.setBaseUrl(activeServer.url)

        return authRepository.getCurrentUser().fold(
            onSuccess = { RestoreSessionResult.Success },
            onFailure = { error ->
                if (isAuthError(error)) {
                    authRepository.clearTokens()
                    RestoreSessionResult.NeedsLogin(activeServer.url)
                } else {
                    // Network error — proceed offline with cached data
                    RestoreSessionResult.OfflineSuccess
                }
            },
        )
    }

    private fun isAuthError(error: Throwable): Boolean {
        if (error is ResponseException) {
            val status = error.response.status.value
            return status == 401 || status == 403
        }
        return false
    }
}
