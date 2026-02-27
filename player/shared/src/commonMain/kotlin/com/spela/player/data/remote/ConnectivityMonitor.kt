package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.util.DispatcherProvider
import io.ktor.client.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.UnknownHostException

class ConnectivityMonitor(
    private val apiClient: SpelaApiClient,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Online)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Backward-compatible derived flow. Repositories using isOnline.value continue working. */
    val isOnline: StateFlow<Boolean> = object : StateFlow<Boolean> {
        override val value: Boolean get() = _connectionState.value.isConnected
        override val replayCache: List<Boolean> get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<Boolean>): Nothing {
            _connectionState.collect { collector.emit(it.isConnected) }
        }
    }

    private val _onReconnect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onReconnect: SharedFlow<Unit> = _onReconnect.asSharedFlow()

    private var consecutiveFailures = 0
    private var authFailureSuppressed = false

    private companion object {
        const val POLL_INTERVAL_ONLINE_MS = 30_000L
        const val POLL_INTERVAL_OFFLINE_MS = 10_000L
        const val DEBOUNCE_THRESHOLD = 2
    }

    fun start() {
        scope.launch(dispatchers.io) {
            while (true) {
                val wasConnected = _connectionState.value.isConnected
                try {
                    val reachable = apiClient.healthCheck()
                    if (reachable) {
                        consecutiveFailures = 0
                        authFailureSuppressed = false
                        if (!wasConnected && _connectionState.value !is ConnectionState.AuthFailed
                            && _connectionState.value !is ConnectionState.DatabaseError
                        ) {
                            _connectionState.value = ConnectionState.Online
                            _onReconnect.tryEmit(Unit)
                        } else if (_connectionState.value is ConnectionState.Offline
                            || _connectionState.value is ConnectionState.ServerUnreachable
                        ) {
                            _connectionState.value = ConnectionState.Online
                            _onReconnect.tryEmit(Unit)
                        }
                    } else {
                        handleFailure(ConnectionState.ServerUnreachable)
                    }
                } catch (e: Throwable) {
                    handleFailure(classifyException(e))
                }

                val interval = if (_connectionState.value.isConnected) POLL_INTERVAL_ONLINE_MS else POLL_INTERVAL_OFFLINE_MS
                delay(interval)
            }
        }
    }

    private fun handleFailure(newState: ConnectionState) {
        // Don't override higher-priority states
        if (_connectionState.value is ConnectionState.AuthFailed ||
            _connectionState.value is ConnectionState.DatabaseError
        ) return

        consecutiveFailures++
        if (consecutiveFailures >= DEBOUNCE_THRESHOLD) {
            _connectionState.value = newState
        }
    }

    fun classifyException(e: Throwable): ConnectionState {
        return when (e) {
            is UnknownHostException -> ConnectionState.Offline
            is ConnectException -> ConnectionState.ServerUnreachable
            is HttpRequestTimeoutException -> ConnectionState.ServerUnreachable
            else -> {
                // Check cause chain for DNS / connect errors
                val cause = e.cause
                when (cause) {
                    is UnknownHostException -> ConnectionState.Offline
                    is ConnectException -> ConnectionState.ServerUnreachable
                    else -> ConnectionState.ServerUnreachable
                }
            }
        }
    }

    fun reportAuthFailure(reason: AuthFailureReason) {
        if (authFailureSuppressed) return
        _connectionState.value = ConnectionState.AuthFailed(reason)
    }

    fun reportDatabaseError(message: String) {
        _connectionState.value = ConnectionState.DatabaseError(message)
    }

    fun clearAuthFailure() {
        if (_connectionState.value is ConnectionState.AuthFailed) {
            authFailureSuppressed = true
            _connectionState.value = ConnectionState.Online
            _onReconnect.tryEmit(Unit)
        }
    }

    fun clearDatabaseError() {
        if (_connectionState.value is ConnectionState.DatabaseError) {
            _connectionState.value = ConnectionState.Online
        }
    }

    /**
     * Test-only: directly sets the connection state, bypassing debounce.
     * Emits onReconnect when transitioning from a non-connected to a connected state.
     */
    internal fun forceConnectionState(state: ConnectionState) {
        val wasConnected = _connectionState.value.isConnected
        _connectionState.value = state
        if (state.isConnected && !wasConnected) {
            _onReconnect.tryEmit(Unit)
        }
    }
}
