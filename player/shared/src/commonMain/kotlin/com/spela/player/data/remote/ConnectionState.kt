package com.spela.player.data.remote

sealed class ConnectionState {
    data object Online : ConnectionState()
    data object Offline : ConnectionState()
    data object ServerUnreachable : ConnectionState()
    data class AuthFailed(val reason: AuthFailureReason) : ConnectionState()
    data class DatabaseError(val message: String) : ConnectionState()

    val isConnected: Boolean
        get() = this is Online

    val hasNetwork: Boolean
        get() = this !is Offline

    val canReachServer: Boolean
        get() = this is Online
}

enum class AuthFailureReason {
    SESSION_EXPIRED,
    REFRESH_FAILED,
}
