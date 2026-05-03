package com.spela.player.domain.usecase

import com.spela.player.data.device.DeviceManager
import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.User
import com.spela.player.domain.repository.AuthRepository
import kotlinx.coroutines.withTimeoutOrNull

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val deviceManager: DeviceManager,
) {
    suspend operator fun invoke(serverUrl: String, username: String, password: String): Result<AuthTokens> {
        return authRepository.login(serverUrl, username, password).onSuccess {
            deviceManager.registerWithServer()
        }
    }
}

class RegisterUseCase(
    private val authRepository: AuthRepository,
    private val deviceManager: DeviceManager,
) {
    suspend operator fun invoke(serverUrl: String, username: String, email: String, password: String): Result<AuthTokens> {
        return authRepository.register(serverUrl, username, email, password).onSuccess {
            deviceManager.registerWithServer()
        }
    }
}

class GetCurrentUserUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): Result<User> = authRepository.getCurrentUser()
}

class LogoutUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke() {
        // Best-effort server-side revocation: blacklist access token and
        // delete refresh tokens. We swallow the network error and always
        // clear local state — a sign-out must never get stuck. The 5s
        // ceiling keeps the UX snappy even when the network drops or the
        // server is unreachable; a longer outage falls through to the
        // local clear so the user appears signed out immediately.
        withTimeoutOrNull(5_000) { authRepository.logout() }
        authRepository.clearTokens()
    }
}

class IsLoggedInUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): Boolean = authRepository.isLoggedIn()
}
