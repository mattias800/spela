package com.spela.player.presentation.state

data class LoginState(
    val serverUrl: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val isRegisterMode: Boolean = false,
)
