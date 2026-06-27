package com.spela.player.presentation.viewmodel

import com.spela.player.domain.usecase.LoginUseCase
import com.spela.player.domain.usecase.RegisterUseCase
import com.spela.player.presentation.intent.LoginIntent
import com.spela.player.presentation.state.LoginState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    /**
     * One-shot navigation event emitted when the user has just
     * successfully signed in. Distinct from `state.isLoggedIn` —
     * which is a sticky flag that survives navigation away from the
     * Login screen — so a stale `true` from a prior session can no
     * longer auto-navigate when the user revisits Login after signing
     * out.
     */
    private val _loginSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginSucceeded: SharedFlow<Unit> = _loginSucceeded.asSharedFlow()

    fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.SetServerUrl -> _state.update { it.copy(serverUrl = intent.url) }
            is LoginIntent.SetUsername -> _state.update { it.copy(username = intent.username) }
            is LoginIntent.SetEmail -> _state.update { it.copy(email = intent.email) }
            is LoginIntent.SetPassword -> _state.update { it.copy(password = intent.password) }
            LoginIntent.ToggleRegisterMode -> _state.update { it.copy(isRegisterMode = !it.isRegisterMode) }
            LoginIntent.DismissError -> _state.update { it.copy(error = null) }
            LoginIntent.Reset -> _state.update {
                // Wipe credentials and the success flag so revisiting
                // LoginScreen after a previous successful sign-in
                // doesn't immediately auto-fire onLoginSuccess.
                LoginState(serverUrl = it.serverUrl)
            }
            LoginIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.serverUrl.isBlank() || current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "All fields are required") }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }

        scope.launch(dispatchers.io) {
            val result = if (current.isRegisterMode) {
                registerUseCase(current.serverUrl, current.username, current.email, current.password)
            } else {
                loginUseCase(current.serverUrl, current.username, current.password)
            }

            result.fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                    _loginSucceeded.tryEmit(Unit)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Authentication failed",
                        )
                    }
                },
            )
        }
    }
}
