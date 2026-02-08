package com.spela.player.domain.usecase

import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.User
import com.spela.player.domain.repository.AuthRepository

class LoginUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(serverUrl: String, username: String, password: String): Result<AuthTokens> {
        return authRepository.login(serverUrl, username, password).onSuccess {
            authRepository.storeTokens(it)
        }
    }
}

class RegisterUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(serverUrl: String, username: String, email: String, password: String): Result<AuthTokens> {
        return authRepository.register(serverUrl, username, email, password).onSuccess {
            authRepository.storeTokens(it)
        }
    }
}

class GetCurrentUserUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): Result<User> = authRepository.getCurrentUser()
}

class LogoutUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke() {
        authRepository.clearTokens()
    }
}

class IsLoggedInUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): Boolean = authRepository.isLoggedIn()
}
