package com.spela.player.presentation.intent

sealed interface LoginIntent {
    data class SetServerUrl(val url: String) : LoginIntent
    data class SetUsername(val username: String) : LoginIntent
    data class SetEmail(val email: String) : LoginIntent
    data class SetPassword(val password: String) : LoginIntent
    data object ToggleRegisterMode : LoginIntent
    data object Submit : LoginIntent
    data object DismissError : LoginIntent
}
