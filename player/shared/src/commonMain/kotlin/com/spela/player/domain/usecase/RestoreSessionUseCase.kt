package com.spela.player.domain.usecase

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.ServerRepository

/**
 * Result of attempting to restore a persisted session on app startup.
 */
sealed class RestoreSessionResult {
    /** Tokens and server restored, user validated. Navigate to Home. */
    data object Success : RestoreSessionResult()

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
 * 6. On failure → clear invalid tokens, return NeedsLogin
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
            onFailure = {
                authRepository.clearTokens()
                RestoreSessionResult.NeedsLogin(activeServer.url)
            },
        )
    }
}
