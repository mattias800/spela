package com.spela.player.presentation.intent

sealed interface LoginIntent {
    data class SetServerUrl(val url: String) : LoginIntent
    data class SetUsername(val username: String) : LoginIntent
    data class SetPassword(val password: String) : LoginIntent
    data object ToggleRegisterMode : LoginIntent
    data object Submit : LoginIntent
    data object DismissError : LoginIntent
    /**
     * Wipe transient state (credentials, isLoggedIn) so the screen
     * starts from a clean slate. Called when LoginScreen mounts so a
     * stale `isLoggedIn = true` from a prior session can't auto-fire
     * onLoginSuccess before the user has had a chance to log in.
     */
    data object Reset : LoginIntent
}
